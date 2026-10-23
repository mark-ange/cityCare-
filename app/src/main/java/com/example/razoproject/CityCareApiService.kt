package com.example.razoproject

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

data class CityCareCollectionSchedule(
    val barangay: String,
    val biodegradableDays: String,
    val nonBiodegradableDays: String,
    val timeSlot: String,
    val assignedTruck: String,
    val areaSchedules: List<CityCareAreaCollectionSchedule>
)

data class CityCareAreaCollectionSchedule(
    val areaName: String,
    val appliesToAllAreas: Boolean,
    val isSample: Boolean,
    val biodegradableDays: String,
    val nonBiodegradableDays: String,
    val timeSlot: String,
    val assignedTruck: String
)

data class CityCareContentImage(
    val id: String,
    val url: String,
    val name: String,
    val mimeType: String
)

data class CityCareCommunityContent(
    val id: String,
    val type: String,
    val title: String,
    val summary: String,
    val body: String,
    val imageUrl: String,
    val sourceUrl: String,
    val images: List<CityCareContentImage>,
    val eventStartAt: String,
    val eventEndAt: String,
    val location: String
)

data class CityCareNotification(
    val id: String,
    val title: String,
    val body: String,
    val type: String,
    val isRead: Boolean,
    val time: String
)

/**
 * REST API Client for CityCare CDO Backend Service (Node.js / Express & MongoDB).
 * Exclusively handles resident registration, strict citizen authentication, and report submissions.
 */
object CityCareApiService {

    @Volatile
    var BASE_URL = BuildConfig.CITYCARE_API_URL

    private const val API_SETTINGS_PREFERENCES = "citycare_api_settings"
    private const val API_BASE_URL_KEY = "api_base_url"
    private const val API_CONFIG_VERSION_KEY = "api_config_version"
    private const val API_CONFIG_VERSION = 5

    fun loadApiBaseUrl(context: Context) {
        val preferences = context.getSharedPreferences(API_SETTINGS_PREFERENCES, Context.MODE_PRIVATE)
        val savedUrl = preferences.getString(API_BASE_URL_KEY, null)
        val previousConfigVersion = preferences.getInt(API_CONFIG_VERSION_KEY, 1)
        val savedHost = savedUrl?.let { runCatching { URI(it).host?.lowercase() }.getOrNull() }
        val defaultHost = runCatching { URI(BuildConfig.CITYCARE_API_URL).host?.lowercase() }.getOrNull()
        val migrateExpiredTunnelUrl = previousConfigVersion < API_CONFIG_VERSION &&
            savedHost == "breeding-mayflower-radiator.ngrok-free.dev"
        val migrateEmulatorUrl = previousConfigVersion < API_CONFIG_VERSION &&
            savedHost == "10.0.2.2" &&
            defaultHost != "10.0.2.2"
        val migrateLocalUrl = previousConfigVersion < API_CONFIG_VERSION &&
            savedUrl != null &&
            isLocalApiUrl(savedUrl) &&
            BuildConfig.CITYCARE_API_URL.startsWith("https://", ignoreCase = true)
        val selectedUrl = if (migrateExpiredTunnelUrl || migrateEmulatorUrl || migrateLocalUrl) {
            BuildConfig.CITYCARE_API_URL
        }
        else savedUrl ?: BuildConfig.CITYCARE_API_URL
        BASE_URL = normalizeApiBaseUrl(selectedUrl)
            ?: BuildConfig.CITYCARE_API_URL
        preferences.edit().apply {
            putInt(API_CONFIG_VERSION_KEY, API_CONFIG_VERSION)
            if (migrateExpiredTunnelUrl || migrateEmulatorUrl || migrateLocalUrl) {
                remove(API_BASE_URL_KEY)
            }
        }.apply()
    }

    fun saveApiBaseUrl(context: Context, value: String): Pair<Boolean, String> {
        val normalizedUrl = normalizeApiBaseUrl(value)
            ?: return false to "Enter a valid http:// or https:// server address."

        BASE_URL = normalizedUrl
        context.getSharedPreferences(API_SETTINGS_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(API_BASE_URL_KEY, normalizedUrl)
            .putInt(API_CONFIG_VERSION_KEY, API_CONFIG_VERSION)
            .apply()
        return true to "Server address saved."
    }

    private fun isLocalApiUrl(value: String): Boolean {
        val host = runCatching { URI(value).host?.lowercase() }.getOrNull() ?: return false
        if (host == "localhost" || host.endsWith(".local")) return true
        val octets = host.split(".").mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        return octets[0] == 10 ||
            octets[0] == 127 ||
            (octets[0] == 169 && octets[1] == 254) ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)
    }

    suspend fun checkApiConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val connection = try {
            (URL("$BASE_URL/health").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }
        } catch (e: Exception) {
            return@withContext false to "Could not open the server address: ${e.message ?: "invalid URL"}"
        }

        try {
            val responseCode = connection.responseCode
            val responseStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = responseStream?.use { stream ->
                BufferedReader(InputStreamReader(stream)).use { it.readText() }
            }.orEmpty()
            val health = runCatching { JSONObject(responseText) }.getOrNull()
            val databaseStatus = health?.optString("database").orEmpty()
            val msg = health?.optString("message").orEmpty().ifBlank { "CityCare API returned HTTP $responseCode." }
            if (responseCode in 200..299 &&
                health?.optString("status") == "ONLINE" &&
                databaseStatus == "CONNECTED"
            ) {
                true to "Connected to CityCare API and MongoDB."
            } else if (databaseStatus.isNotBlank()) {
                false to "CityCare API responded, but database status is $databaseStatus."
            } else {
                false to msg
            }
        } catch (e: Exception) {
            false to "Could not reach $BASE_URL: ${e.message ?: "network unavailable"}"
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeApiBaseUrl(value: String): String? {
        val trimmed = value.trim().trimEnd('/')
        val parsed = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (parsed.scheme !in setOf("http", "https") ||
            parsed.host.isNullOrBlank() ||
            parsed.userInfo != null ||
            parsed.query != null ||
            parsed.fragment != null
        ) {
            return null
        }

        val path = parsed.path.orEmpty().trimEnd('/')
        val apiPath = if (path.endsWith("/api")) path else "$path/api"
        return runCatching {
            URI(parsed.scheme, null, parsed.host, parsed.port, apiPath, null, null)
                .toASCIIString()
                .trimEnd('/')
        }.getOrNull()
    }

    fun hasValidatedInternetConnection(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    var authToken: String? = null
    var isOnline: Boolean = true
    var pendingReportBitmap: android.graphics.Bitmap? = null

    // Current Authenticated User Session Profile Data
    var currentUserName: String = "Resident User"
    var currentUserEmail: String = "resident@citycare.com"
    var currentUserPhone: String = "0917 123 4567"
    var currentUserLocation: String = "Barangay Carmen, Zone 3"
    var currentUserBarangay: String = "Carmen"
    var currentUserPurok: String = "Zone 3"
    var currentUserPhotoUrl: String? = null

    fun setCurrentUser(
        name: String,
        email: String,
        phone: String = "",
        location: String = "Barangay Carmen, Zone 3",
        barangay: String = "",
        purok: String = "",
        photoUrl: String? = currentUserPhotoUrl
    ) {
        currentUserName = name.ifEmpty { email.substringBefore("@") }.trim()
        currentUserEmail = email.ifEmpty { "resident@citycare.com" }.trim()
        currentUserPhone = phone.ifEmpty { "0917 123 4567" }.trim()
        currentUserBarangay = barangay.ifEmpty {
            location.substringAfter("Barangay ", "").substringBefore(",").ifEmpty { "Carmen" }
        }.trim()
        currentUserPurok = purok.ifEmpty {
            location.substringAfter(",", "").trim().ifEmpty { "Zone 3" }
        }.trim()
        currentUserLocation = "Barangay $currentUserBarangay, $currentUserPurok"
        currentUserPhotoUrl = photoUrl
    }

    fun saveUserSession(context: Context) {
        try {
            val prefs = context.getSharedPreferences("citycare_user_session", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("is_logged_in", true)
                .putString("auth_token", authToken)
                .putString("user_name", currentUserName)
                .putString("user_email", currentUserEmail)
                .putString("user_phone", currentUserPhone)
                .putString("user_location", currentUserLocation)
                .putString("user_barangay", currentUserBarangay)
                .putString("user_purok", currentUserPurok)
                .putString("user_photo_url", currentUserPhotoUrl)
                .apply()
            if (currentUserPhotoUrl != null) {
                saveAccountProfilePhoto(context, currentUserEmail, currentUserPhotoUrl)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadUserSession(context: Context): Boolean {
        try {
            val prefs = context.getSharedPreferences("citycare_user_session", Context.MODE_PRIVATE)
            val isLoggedIn = prefs.getBoolean("is_logged_in", true)
            if (isLoggedIn) {
                authToken = prefs.getString("auth_token", null)
                currentUserName = prefs.getString("user_name", "Mark Angelou Abellano") ?: "Mark Angelou Abellano"
                currentUserEmail = prefs.getString("user_email", "maabellano96428@liceo.edu.ph") ?: "maabellano96428@liceo.edu.ph"
                currentUserPhone = prefs.getString("user_phone", "09700395251") ?: "09700395251"
                currentUserLocation = prefs.getString("user_location", "Barangay Carmen, Zone 3") ?: "Barangay Carmen, Zone 3"
                currentUserBarangay = prefs.getString("user_barangay", "Carmen") ?: "Carmen"
                currentUserPurok = prefs.getString("user_purok", "Zone 3") ?: "Zone 3"
                currentUserPhotoUrl = prefs.getString("user_photo_url", null)
                    ?: loadAccountProfilePhoto(context, currentUserEmail)
                saveUserSession(context)
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        currentUserName = "Mark Angelou Abellano"
        currentUserEmail = "maabellano96428@liceo.edu.ph"
        currentUserPhone = "09700395251"
        currentUserLocation = "Barangay Carmen, Zone 3"
        currentUserBarangay = "Carmen"
        currentUserPurok = "Zone 3"
        saveUserSession(context)
        return true
    }

    private fun profilePhotoPreferenceKey(email: String): String =
        "profile_photo_${email.trim().lowercase()}"

    private fun saveAccountProfilePhoto(context: Context, email: String, photoData: String?) {
        val prefs = context.getSharedPreferences("citycare_profile_media", Context.MODE_PRIVATE)
        prefs.edit().putString(profilePhotoPreferenceKey(email), photoData).apply()
    }

    private fun loadAccountProfilePhoto(context: Context, email: String): String? =
        context.getSharedPreferences("citycare_profile_media", Context.MODE_PRIVATE)
            .getString(profilePhotoPreferenceKey(email), null)

    fun loadCurrentProfilePhoto(context: Context): android.graphics.Bitmap? {
        val photoData = currentUserPhotoUrl ?: loadAccountProfilePhoto(context, currentUserEmail)
        if (photoData.isNullOrBlank()) return null
        currentUserPhotoUrl = photoData
        return base64ToBitmap(photoData)
    }

    suspend fun updateProfilePhoto(context: Context, bitmap: android.graphics.Bitmap?): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val photoData = bitmap?.let { source ->
                val maxDimension = 512
                val scale = minOf(1f, maxDimension.toFloat() / maxOf(source.width, source.height))
                val resized = if (scale < 1f) {
                    android.graphics.Bitmap.createScaledBitmap(
                        source,
                        (source.width * scale).toInt().coerceAtLeast(1),
                        (source.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    source
                }
                val output = java.io.ByteArrayOutputStream()
                resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 65, output)
                android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP)
            }.orEmpty()

            if (bitmap != null && photoData.isBlank()) {
                return@withContext false to "Could not prepare the selected photo. Please choose another image."
            }

            saveAccountProfilePhoto(context, currentUserEmail, photoData.ifBlank { null })
            currentUserPhotoUrl = photoData.ifBlank { null }
            saveUserSession(context)

            val token = authToken
            if (token.isNullOrBlank()) {
                return@withContext true to if (photoData.isBlank()) {
                    "Profile photo removed from this device."
                } else {
                    "Profile photo saved on this device. Sign in online to sync it to your account."
                }
            }

            try {
                val connection = (URL("$BASE_URL/auth/profile").openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $token")
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                val payload = JSONObject().put("profilePhoto", photoData).toString()
                OutputStreamWriter(connection.outputStream).use { it.write(payload) }

                val responseCode = connection.responseCode
                val responseStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                val responseText = responseStream?.let {
                    BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() }
                }.orEmpty()
                connection.disconnect()

                if (responseCode !in 200..299) {
                    val message = runCatching { JSONObject(responseText).optString("message") }.getOrNull()
                    return@withContext true to "Photo saved on this device, but account sync failed: ${
                        message?.takeIf { it.isNotBlank() } ?: "HTTP $responseCode"
                    }"
                }
                true to if (photoData.isBlank()) {
                    "Profile photo removed from your account."
                } else {
                    "Profile photo saved to your account."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                true to "Photo saved on this device, but account sync failed. Check your connection and try again."
            }
        }

    fun clearUserSession(context: Context) {
        try {
            val prefs = context.getSharedPreferences("citycare_user_session", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            authToken = null
            currentUserName = "Resident User"
            currentUserEmail = "resident@citycare.com"
            currentUserPhone = "0917 123 4567"
            currentUserLocation = "Barangay Carmen, Zone 3"
            currentUserBarangay = "Carmen"
            currentUserPurok = "Zone 3"
            currentUserPhotoUrl = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Persistent cache for user-submitted incident reports
    val userReportsCache = mutableListOf<WasteReportItem>()
    private val uploadLock = Any()
    private val reportsBeingUploaded = mutableSetOf<String>()
    private val reportSyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val LEGACY_REPORTS_PREFERENCES = "citycare_reports_prefs"

    private fun reportsPreferences(context: Context, email: String = currentUserEmail) =
        context.getSharedPreferences(
            "citycare_reports_${android.util.Base64.encodeToString(email.trim().lowercase().toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)}",
            Context.MODE_PRIVATE
        )

    fun bitmapToBase64(bitmap: android.graphics.Bitmap): String {
        return try {
            val byteArrayOutputStream = java.io.ByteArrayOutputStream()
            val maxDimension = 1280
            val scale = minOf(1f, maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height))
            val resizedBitmap = if (scale < 1f) {
                android.graphics.Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }
            resizedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()
            android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }

    fun base64ToBitmap(base64Str: String): android.graphics.Bitmap? {
        return try {
            if (base64Str.isBlank()) return null
            var cleanStr = base64Str.trim()
            if (cleanStr.contains(",")) {
                cleanStr = cleanStr.substringAfter(",")
            }
            cleanStr = cleanStr.replace("\n", "").replace("\r", "").replace(" ", "").replace("\\s".toRegex(), "")
            val decodedBytes = android.util.Base64.decode(cleanStr, android.util.Base64.NO_WRAP or android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    fun addLocalReport(
        context: Context,
        report: WasteReportItem,
        enqueueUpload: Boolean = true
    ) {
        val taggedReport = report.copy(userEmail = currentUserEmail)
        updateSyncedReportIds(context) { it.remove(taggedReport.id) }
        val cacheIndex = userReportsCache.indexOfFirst {
            it.id == taggedReport.id &&
                it.userEmail.equals(taggedReport.userEmail, ignoreCase = true)
        }
        if (cacheIndex == -1) {
            userReportsCache.add(0, taggedReport)
        } else {
            userReportsCache[cacheIndex] = taggedReport
        }
        saveReportsToDisk(context)
        if (enqueueUpload) enqueuePendingReportUpload(context)
    }

    fun enqueuePendingReportUpload(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<PendingReportUploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                java.util.concurrent.TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            PendingReportUploadWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    private fun updateSyncedReportIds(context: Context, update: (MutableSet<String>) -> Unit) {
        val preferences = reportsPreferences(context)
        val syncedIds = preferences.getStringSet("synced_report_ids", emptySet())?.toMutableSet()
            ?: mutableSetOf()
        update(syncedIds)
        preferences.edit().putStringSet("synced_report_ids", syncedIds).apply()
    }

    private fun responseConfirmsPersistence(
        connection: HttpURLConnection,
        requirePhoto: Boolean = false
    ): Boolean {
        return try {
            val responseCode = connection.responseCode
            val responseStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val responseBody = responseStream?.use {
                BufferedReader(InputStreamReader(it)).readText()
            }.orEmpty()
            if (responseCode !in 200..299) {
                false
            } else {
                val response = JSONObject(responseBody)
                val data = response.optJSONObject("data")
                response.optBoolean("persisted", false) &&
                    (!requirePhoto || data?.optBoolean("hasPhoto", false) == true)
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun syncPendingReport(
        context: Context,
        report: WasteReportItem,
        forceEvidenceUpload: Boolean = false
    ): Boolean {
        val preferences = reportsPreferences(context)
        if (!forceEvidenceUpload &&
            preferences.getStringSet("synced_report_ids", emptySet())?.contains(report.id) == true
        ) {
            return true
        }

        val shouldUpload = synchronized(uploadLock) {
            reportsBeingUploaded.add(report.id)
        }
        if (!shouldUpload) return false

        return try {
            if (uploadReportToCloud(report)) {
                updateSyncedReportIds(context) { it.add(report.id) }
                true
            } else {
                false
            }
        } finally {
            synchronized(uploadLock) {
                reportsBeingUploaded.remove(report.id)
            }
        }
    }

    suspend fun syncPendingReports(context: Context): Boolean = withContext(Dispatchers.IO) {
        loadReportsFromDisk(context)
        val syncedIds = reportsPreferences(context)
            .getStringSet("synced_report_ids", emptySet())
            .orEmpty()
        val pendingReports = userReportsCache.filter {
            it.userEmail.isNotBlank() &&
                it.userEmail.equals(currentUserEmail, ignoreCase = true) &&
                it.id !in syncedIds
        }

        for (report in pendingReports) {
            if (!syncPendingReport(context, report)) return@withContext false
        }
        true
    }

    fun saveReportsToDisk(context: Context) {
        try {
            val prefs = reportsPreferences(context)
            val jsonArray = JSONArray()
            userReportsCache.filter {
                it.userEmail.isNotBlank() &&
                    it.userEmail.equals(currentUserEmail, ignoreCase = true)
            }.forEach { r ->
                val obj = JSONObject()
                obj.put("id", r.id)
                obj.put("title", r.title)
                obj.put("barangay", r.barangay)
                obj.put("date", r.date)
                obj.put("status", r.status)
                obj.put("description", r.description)
                obj.put("category", r.category)
                obj.put("landmark", r.landmark)
                obj.put("userEmail", r.userEmail)
                obj.put("unitAssigned", r.unitAssigned)
                obj.put("dispatcherNotes", r.dispatcherNotes)
                obj.put("gpsCoordinates", r.gpsCoordinates)
                val base64Str = r.photoBase64 ?: r.imageBitmap?.let { bitmapToBase64(it) }
                if (!base64Str.isNullOrEmpty()) {
                    obj.put("photoBase64", base64Str)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString("saved_reports", jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadReportsFromDisk(context: Context) {
        try {
            val prefs = reportsPreferences(context)
            if (prefs.getInt("report_sync_version", 0) < 2) {
                prefs.edit()
                    .remove("synced_report_ids")
                    .putInt("report_sync_version", 2)
                    .apply()
            }
            val jsonStr = prefs.getString("saved_reports", null)
                ?: context.getSharedPreferences(LEGACY_REPORTS_PREFERENCES, Context.MODE_PRIVATE)
                    .getString("saved_reports", null)
                ?: return
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val reportEmail = obj.optString("userEmail").trim()
                if (reportEmail.isBlank() ||
                    !reportEmail.equals(currentUserEmail, ignoreCase = true)
                ) {
                    continue
                }
                val photoBase64 = obj.optString("photoBase64").takeIf { it.isNotBlank() }
                val decodedBitmap = photoBase64?.let { base64ToBitmap(it) }
                val item = WasteReportItem(
                    id = obj.optString("id"),
                    title = obj.optString("title"),
                    barangay = obj.optString("barangay"),
                    date = obj.optString("date"),
                    status = obj.optString("status"),
                    description = obj.optString("description"),
                    category = obj.optString("category"),
                    landmark = obj.optString("landmark"),
                    imageBitmap = decodedBitmap,
                    photoBase64 = photoBase64,
                    userEmail = reportEmail,
                    unitAssigned = obj.optString("unitAssigned", "Unassigned"),
                    dispatcherNotes = obj.optString("dispatcherNotes", ""),
                    gpsCoordinates = obj.optString("gpsCoordinates", "8.4822° N, 124.6175° E")
                )
                if (userReportsCache.none {
                        it.id == item.id && it.userEmail.equals(item.userEmail, ignoreCase = true)
                    }
                ) {
                    userReportsCache.add(item)
                }
            }
            if (!prefs.contains("saved_reports")) {
                saveReportsToDisk(context)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun loadLocalReports(context: Context): List<WasteReportItem> = withContext(Dispatchers.IO) {
        loadReportsFromDisk(context)
        userReportsCache.filter {
            it.userEmail.isNotBlank() && it.userEmail.equals(currentUserEmail, ignoreCase = true)
        }
    }

    /**
     * Uploads citizen waste concern report to Node.js / Express MongoDB API server.
     */
    suspend fun uploadReportToCloud(report: WasteReportItem): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = URL("$BASE_URL/reports")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (!authToken.isNullOrEmpty()) {
                    setRequestProperty("Authorization", "Bearer $authToken")
                }
                connectTimeout = 15000
                readTimeout = 20000
            }

            val photo = report.imageBitmap?.let { bitmapToBase64(it) }
                ?: report.photoBase64?.takeIf { it.isNotBlank() }
            val jsonPayload = JSONObject().apply {
                put("reportId", report.id)
                put("title", report.title)
                put("category", report.category)
                put("barangay", report.barangay)
                put("purokZone", if (report.landmark.isNotBlank()) report.landmark else report.title)
                put("landmark", report.landmark)
                put("description", report.description)
                put("gpsCoordinates", report.gpsCoordinates)
                put("userEmail", if (report.userEmail.isNotBlank()) report.userEmail else currentUserEmail)
                if (!photo.isNullOrBlank()) {
                    put("photoBase64", photo)
                }
            }.toString()

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonPayload)
                writer.flush()
            }

            responseConfirmsPersistence(connection, requirePhoto = !photo.isNullOrBlank())
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun uploadReportToCloud(report: ReportItemData): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val url = URL("$BASE_URL/reports")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (!authToken.isNullOrEmpty()) {
                    setRequestProperty("Authorization", "Bearer $authToken")
                }
                connectTimeout = 3000
                readTimeout = 3000
            }

            val jsonPayload = JSONObject().apply {
                put("reportId", report.id)
                put("title", report.title)
                put("category", "Illegal Dump Site")
                put("barangay", "Carmen")
                put("purokZone", report.location)
                put("landmark", "")
                put("description", "Report submitted via CityCare Android Mobile App.")
                put("gpsCoordinates", report.gpsCoordinates ?: "8.4822° N, 124.6175° E")
                put("userEmail", currentUserEmail)
            }.toString()

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonPayload)
                writer.flush()
            }

            responseConfirmsPersistence(connection)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Registers a new Resident user with the Node.js Express backend API (/api/auth/register-resident).
     */
    suspend fun registerCitizenOnline(account: CitizenAccount): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanName = account.name.trim()
        val cleanPass = account.password.trim()
        val cleanEmail = account.email.trim().lowercase()
        val cleanPhone = if (account.phoneNumber.isNotBlank()) {
            account.phoneNumber.trim()
        } else {
            "0917" + (1000000..9999999).random()
        }

        return@withContext try {
            val url = URL("$BASE_URL/auth/register-resident")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 3000
                readTimeout = 3000
            }

            val jsonPayload = JSONObject()
                .put("fullName", cleanName)
                .put("phoneNumber", cleanPhone)
                .put("email", cleanEmail)
                .put("barangay", account.barangay)
                .put("purokZone", account.purok)
                .put("password", cleanPass)
                .toString()

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonPayload)
                writer.flush()
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            connection.disconnect()

            if (responseCode == 201) {
                val response = JSONObject(responseText)
                val user = response.optJSONObject("data") ?: JSONObject()
                authToken = response.optString("token").takeIf { it.isNotBlank() }
                setCurrentUser(
                    name = user.optString("fullName", cleanName),
                    email = user.optString("email", cleanEmail),
                    phone = user.optString("phoneNumber", cleanPhone),
                    barangay = user.optString("barangay", account.barangay),
                    purok = user.optString("purokZone", account.purok)
                )
                Pair(true, "Registration Successful! Account created.")
            } else if (responseCode == 409) {
                Pair(false, "Conflict: Phone or Email is already registered in database.")
            } else {
                Pair(false, "Registration Failed (Code $responseCode): $responseText")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "Could not reach CityCare. Connect to the internet and try registering again.")
        }
    }

    suspend fun updateUserProfile(
        context: Context,
        name: String,
        email: String,
        phone: String,
        barangay: String,
        purok: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        val cleanEmail = email.trim().lowercase()
        val cleanPhone = phone.trim()
        val cleanBarangay = barangay.trim()
        val cleanPurok = purok.trim()

        if (cleanName.isEmpty() || cleanEmail.isEmpty() || cleanPhone.isEmpty() ||
            cleanBarangay.isEmpty() || cleanPurok.isEmpty()
        ) {
            return@withContext false to "Please complete all profile fields."
        }

        val token = authToken
        if (!token.isNullOrBlank()) {
            try {
                val connection = (URL("$BASE_URL/auth/profile").openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $token")
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                val payload = JSONObject()
                    .put("fullName", cleanName)
                    .put("email", cleanEmail)
                    .put("phoneNumber", cleanPhone)
                    .put("barangay", cleanBarangay)
                    .put("purokZone", cleanPurok)
                OutputStreamWriter(connection.outputStream).use { it.write(payload.toString()) }

                val responseCode = connection.responseCode
                val responseStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                val responseText = responseStream?.let { BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() } }.orEmpty()
                connection.disconnect()

                if (responseCode !in 200..299) {
                    val message = runCatching { JSONObject(responseText).optString("message") }.getOrNull()
                    return@withContext false to message?.takeIf { it.isNotBlank() }
                        .orEmpty().ifBlank { "Profile update failed (HTTP $responseCode)." }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext false to "Could not sync profile changes. Check your connection and try again."
            }
        }

        setCurrentUser(
            name = cleanName,
            email = cleanEmail,
            phone = cleanPhone,
            barangay = cleanBarangay,
            purok = cleanPurok
        )
        saveUserSession(context)
        if (token.isNullOrBlank()) {
            true to "Saved on this device. Sign in online to sync your profile to your account."
        } else {
            true to "Profile updated and synced to your account."
        }
    }

    suspend fun changeUserPassword(currentPassword: String, newPassword: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val token = authToken
            if (token.isNullOrBlank()) {
                return@withContext false to "Sign in online before changing your account password."
            }
            if (currentPassword.isBlank() || newPassword.length < 6) {
                return@withContext false to "Enter your current password and a new password with at least 6 characters."
            }

            try {
                val connection = (URL("$BASE_URL/auth/change-password").openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $token")
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                val payload = JSONObject()
                    .put("currentPassword", currentPassword)
                    .put("newPassword", newPassword)
                OutputStreamWriter(connection.outputStream).use { it.write(payload.toString()) }

                val responseCode = connection.responseCode
                val responseStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                val responseText = responseStream?.let {
                    BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() }
                }.orEmpty()
                connection.disconnect()
                val response = runCatching { JSONObject(responseText) }.getOrNull()
                if (responseCode in 200..299) {
                    true to response?.optString("message").orEmpty().ifBlank { "Password changed successfully." }
                } else {
                    false to response?.optString("message").orEmpty()
                        .ifBlank { "Password change failed (HTTP $responseCode)." }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false to "Could not change password. Check your connection and try again."
            }
        }

    /** Authenticates the Google ID token with the API and creates a resident profile if needed. */
    suspend fun loginWithGoogleSocial(
        idToken: String,
        context: Context
    ): Pair<Boolean, String> =
        completeSocialSignIn("google", "idToken", idToken, "Google", context)

    suspend fun loginWithFacebookAccessToken(
        accessToken: String,
        context: Context
    ): Pair<Boolean, String> =
        completeSocialSignIn("facebook", "accessToken", accessToken, "Facebook", context)

    private suspend fun completeSocialSignIn(
        endpoint: String,
        tokenField: String,
        providerToken: String,
        providerName: String,
        context: Context
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (providerToken.isBlank()) {
            return@withContext false to "$providerName did not return an authentication token."
        }
        if (!hasValidatedInternetConnection(context)) {
            return@withContext false to "Could not reach CityCare. Check the API connection and try again."
        }

        authToken = null
        val connection = try {
            (URL("$BASE_URL/auth/$endpoint").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 8000
                readTimeout = 8000
            }
        } catch (e: Exception) {
            return@withContext false to "Could not connect to CityCare: ${e.localizedMessage ?: "network error"}"
        }

        try {
            val payload = JSONObject().put(tokenField, providerToken).toString()
            OutputStreamWriter(connection.outputStream).use { it.write(payload) }

            val responseCode = connection.responseCode
            val responseStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = responseStream?.let {
                BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() }
            }.orEmpty()
            val response = runCatching { JSONObject(responseText) }.getOrNull()
            if (responseCode !in 200..299) {
                val message = response?.optString("message")?.takeIf { it.isNotBlank() }
                return@withContext false to (message ?: "$providerName sign-in failed (HTTP $responseCode).")
            }

            val token = response?.optString("token")?.takeIf { it.isNotBlank() }
                ?: return@withContext false to "$providerName sign-in returned no CityCare session."
            val user = response.optJSONObject("data")
                ?: return@withContext false to "$providerName sign-in returned incomplete account data."

            authToken = token
            setCurrentUser(
                name = user.optString("fullName", "$providerName Resident"),
                email = user.optString("email", ""),
                phone = user.optString("phoneNumber", ""),
                barangay = user.optString("barangay", "Carmen"),
                purok = user.optString("purokZone", "Zone 3"),
                photoUrl = user.optString("profilePhoto").takeIf { it.isNotBlank() }
            )
            saveUserSession(context)
            true to "$providerName sign-in successful."
        } catch (e: Exception) {
            authToken = null
            false to "Could not complete $providerName sign-in: ${e.localizedMessage ?: "network error"}"
        } finally {
            connection.disconnect()
        }
    }

    /**
     * STRICT Citizen Authentication: Validates credentials with Node.js Express backend or registered database.
     */
    suspend fun loginUserOnline(identifier: String, pass: String, context: Context): Boolean = withContext(Dispatchers.IO) {
        val cleanId = identifier.trim().lowercase()
        val cleanPass = pass.trim()

        authToken = null
        if (cleanId.isEmpty() || cleanPass.isEmpty() || !hasValidatedInternetConnection(context)) {
            return@withContext false
        }

        return@withContext try {
            val url = URL("$BASE_URL/auth/login")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 3000
                readTimeout = 3000
            }

            val jsonPayload = JSONObject()
                .put("identifier", cleanId)
                .put("password", cleanPass)
                .toString()

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonPayload)
                writer.flush()
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.let { BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() } }.orEmpty()
            connection.disconnect()
            if (responseCode !in 200..299) {
                authToken = null
                return@withContext false
            }

            val response = JSONObject(responseText)
            val token = response.optString("token").takeIf { it.isNotBlank() }
                ?: return@withContext false
            val user = response.optJSONObject("data")
                ?: return@withContext false
            authToken = token
            setCurrentUser(
                name = user.optString("fullName", currentUserName),
                email = user.optString("email", currentUserEmail),
                phone = user.optString("phoneNumber", currentUserPhone),
                barangay = user.optString("barangay", currentUserBarangay),
                purok = user.optString("purokZone", currentUserPurok),
                photoUrl = user.optString("profilePhoto").takeIf { it.isNotBlank() }
            )
            true
        } catch (e: Exception) {
            authToken = null
            false
        }
    }

    /**
     * Fetches waste reports from Node.js Express backend API (/api/reports) and merges with local disk persistence
     */
    suspend fun fetchReportDetails(reportId: String): WasteReportItem? = withContext(Dispatchers.IO) {
        try {
            val encodedId = java.net.URLEncoder.encode(reportId, "UTF-8")
            val connection = (URL("$BASE_URL/reports/$encodedId").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }
            try {
                if (connection.responseCode !in 200..299) return@withContext null
                val responseText = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                val reportJson = JSONObject(responseText).optJSONObject("data") ?: return@withContext null
                val wrappedJson = JSONObject().put("data", JSONArray().put(reportJson)).toString()
                parseReportsJson(wrappedJson).firstOrNull {
                    it.userEmail.equals(currentUserEmail, ignoreCase = true)
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun fetchReportsFromCloud(context: Context? = null): List<WasteReportItem> = withContext(Dispatchers.IO) {
        if (context != null) loadReportsFromDisk(context)

        var databaseAvailable = false
        val cloudList = try {
            val encodedEmail = java.net.URLEncoder.encode(currentUserEmail.trim().lowercase(), "UTF-8")
            val reports = mutableListOf<WasteReportItem>()
            var page = 1
            var totalPages = 1
            while (page <= totalPages) {
                val url = URL("$BASE_URL/reports?userEmail=$encodedEmail&page=$page&limit=1000")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 10000
                }

                try {
                    val responseCode = connection.responseCode
                    if (responseCode !in 200..299) break
                    val responseText = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                    val response = JSONObject(responseText)
                    databaseAvailable = true
                    totalPages = response.optInt("totalPages", 1).coerceAtLeast(1)
                    reports += parseReportsJson(responseText).filter {
                        it.userEmail.equals(currentUserEmail, ignoreCase = true)
                    }
                    page++
                } finally {
                    connection.disconnect()
                }
            }
            reports
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }

        if (context != null && databaseAvailable) {
            userReportsCache
                .filter {
                    it.userEmail.isNotBlank() &&
                        it.userEmail.equals(currentUserEmail, ignoreCase = true)
                }
                .forEach { report ->
                    val serverReport = cloudList.firstOrNull { it.id == report.id }
                    val hasLocalPhoto = !report.photoBase64.isNullOrBlank() || report.imageBitmap != null
                    val needsEvidenceUpload = hasLocalPhoto && serverReport?.hasCloudPhoto != true
                    reportSyncScope.launch {
                        syncPendingReport(context, report, forceEvidenceUpload = needsEvidenceUpload)
                    }
                }
        }

        // Merge cloud status updates from Admin into userReportsCache so status changes display in real-time
        cloudList.forEach { cloudReport ->
            if (!cloudReport.userEmail.equals(currentUserEmail, ignoreCase = true)) {
                return@forEach
            }
            val index = userReportsCache.indexOfFirst {
                it.id == cloudReport.id &&
                    it.userEmail.equals(cloudReport.userEmail, ignoreCase = true)
            }
            if (index != -1) {
                val localItem = userReportsCache[index]
                userReportsCache[index] = localItem.copy(
                    status = cloudReport.status,
                    unitAssigned = cloudReport.unitAssigned,
                    dispatcherNotes = cloudReport.dispatcherNotes,
                    gpsCoordinates = cloudReport.gpsCoordinates,
                    description = if (cloudReport.description.isNotBlank()) cloudReport.description else localItem.description,
                    category = if (cloudReport.category.isNotBlank()) cloudReport.category else localItem.category,
                    landmark = if (cloudReport.landmark.isNotBlank()) cloudReport.landmark else localItem.landmark,
                    imageBitmap = cloudReport.imageBitmap ?: localItem.imageBitmap,
                    photoBase64 = cloudReport.photoBase64 ?: localItem.photoBase64,
                    hasCloudPhoto = cloudReport.hasCloudPhoto
                )
            } else {
                userReportsCache.add(cloudReport)
            }
        }
        if (context != null) {
            saveReportsToDisk(context)
        }

        val combined = userReportsCache.filter { r ->
            r.userEmail.isNotBlank() && r.userEmail.equals(currentUserEmail, ignoreCase = true)
        }
        return@withContext combined
    }

    private fun parseReportsJson(jsonString: String): List<WasteReportItem> {
        val result = mutableListOf<WasteReportItem>()
        try {
            val jsonObject = JSONObject(jsonString)
            if (jsonObject.has("data")) {
                val dataArray = jsonObject.getJSONArray("data")
                for (i in 0 until dataArray.length()) {
                    val item = dataArray.getJSONObject(i)
                    val id = item.optString("reportId", "#CDO-2026-${1000 + i}")
                    val title = item.optString("title", "Waste Concern Report")
                    val barangay = item.optString("barangay", "Carmen")
                    val purokZone = item.optString("purokZone", "")
                    val landmark = item.optString("landmark", "Barangay Area")
                    val status = item.optString("status", "PENDING")
                    val description = item.optString("description", "Uncollected waste report.")
                    val category = item.optString("category", "Illegal Dump Site")
                    val unitAssigned = item.optString("unitAssigned", "Unassigned")
                    val dispatcherNotes = item.optString("notes", "")
                    val gpsCoordinates = item.optString("gpsCoordinates", "8.4822° N, 124.6175° E")
                    val date = item.optString("createdAt", "Today, 8:00 AM")
                    val userEmail = item.optString("userEmail", "")
                    val photoUrl = item.optString("photoUrl", "")
                    val decodedBitmap = if (photoUrl.isNotBlank()) base64ToBitmap(photoUrl) else null
                    val hasCloudPhoto = item.optBoolean("hasPhoto", photoUrl.isNotBlank())

                    result.add(
                        WasteReportItem(
                            id = id,
                            title = title,
                            barangay = barangay,
                            date = if (date.length > 10) date.substring(0, 10) else date,
                            status = status,
                            description = description,
                            category = category,
                            landmark = if (landmark.isNotBlank()) landmark else purokZone,
                            imageBitmap = decodedBitmap,
                            photoBase64 = if (photoUrl.isNotBlank()) photoUrl else null,
                            userEmail = userEmail,
                            unitAssigned = unitAssigned,
                            dispatcherNotes = dispatcherNotes,
                            gpsCoordinates = gpsCoordinates,
                            hasCloudPhoto = hasCloudPhoto
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    suspend fun fetchSchedulesFromCloud(barangay: String? = null): List<CityCareCollectionSchedule> =
        withContext(Dispatchers.IO) {
            val encodedBarangay = barangay?.let { URLEncoder.encode(it, "UTF-8") }
            val query = encodedBarangay?.let { "?barangay=$it" }.orEmpty()
            val connection = (URL("$BASE_URL/schedules$query").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }

            try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw IOException("Schedule request failed with HTTP $responseCode")
                }

                val responseText = BufferedReader(InputStreamReader(connection.inputStream)).use {
                    it.readText()
                }
                val response = JSONObject(responseText)
                if (!response.optBoolean("success", false)) {
                    throw IOException(response.optString("message", "Could not load collection schedules"))
                }

                val scheduleData = response.optJSONArray("data") ?: JSONArray()
                buildList {
                    for (index in 0 until scheduleData.length()) {
                        val item = scheduleData.optJSONObject(index) ?: continue
                        val areaData = item.optJSONArray("areaSchedules") ?: JSONArray()
                        val areaSchedules = buildList {
                            for (areaIndex in 0 until areaData.length()) {
                                val area = areaData.optJSONObject(areaIndex) ?: continue
                                val areaName = area.optString("areaName").trim()
                                if (areaName.isNotBlank()) {
                                    add(
                                        CityCareAreaCollectionSchedule(
                                            areaName = areaName,
                                            appliesToAllAreas = area.optBoolean("appliesToAllAreas", false),
                                            isSample = area.optBoolean("isSample", false),
                                            biodegradableDays = area.optString("biodegradableDays").trim(),
                                            nonBiodegradableDays = area.optString("nonBiodegradableDays").trim(),
                                            timeSlot = area.optString("timeSlot").trim(),
                                            assignedTruck = area.optString("assignedTruck").trim()
                                        )
                                    )
                                }
                            }
                        }
                        add(
                            CityCareCollectionSchedule(
                                barangay = item.optString("barangay").trim(),
                                biodegradableDays = item.optString("biodegradableDays").trim(),
                                nonBiodegradableDays = item.optString("nonBiodegradableDays").trim(),
                                timeSlot = item.optString("timeSlot").trim(),
                                assignedTruck = item.optString("assignedTruck").trim(),
                                areaSchedules = areaSchedules
                            )
                        )
                    }
                }.filter { it.barangay.isNotBlank() }
            } finally {
                connection.disconnect()
            }
        }

    suspend fun fetchCommunityContent(type: String): List<CityCareCommunityContent> =
        withContext(Dispatchers.IO) {
            val supportedTypes = setOf("recycle", "event", "tip", "blog")
            require(type in supportedTypes) { "Unsupported community content type." }

            val encodedType = URLEncoder.encode(type, "UTF-8")
            val connection = (URL("$BASE_URL/content?type=$encodedType").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 15000
            }

            try {
                val responseCode = connection.responseCode
                val responseStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                val responseText = responseStream?.let { stream ->
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                }.orEmpty()
                if (responseCode !in 200..299) {
                    val message = runCatching { JSONObject(responseText).optString("message") }.getOrNull()
                    throw IOException(message?.takeIf { it.isNotBlank() } ?: "Content request failed with HTTP $responseCode.")
                }

                val response = JSONObject(responseText)
                if (!response.optBoolean("success", false)) {
                    throw IOException(response.optString("message").ifBlank { "Could not load community content." })
                }

                val contentData = response.optJSONArray("data") ?: JSONArray()
                buildList {
                    for (index in 0 until contentData.length()) {
                        val item = contentData.optJSONObject(index) ?: continue
                        val imagesJson = item.optJSONArray("images") ?: JSONArray()
                        val images = buildList {
                            for (imageIndex in 0 until imagesJson.length()) {
                                val image = imagesJson.optJSONObject(imageIndex) ?: continue
                                val url = image.optString("url").trim()
                                if (url.isNotBlank()) {
                                    add(
                                        CityCareContentImage(
                                            id = image.optString("id"),
                                            url = url,
                                            name = image.optString("name"),
                                            mimeType = image.optString("mimeType")
                                        )
                                    )
                                }
                            }
                        }
                        add(
                            CityCareCommunityContent(
                                id = item.optString("_id"),
                                type = item.optString("type"),
                                title = item.optString("title"),
                                summary = item.optString("summary"),
                                body = item.optString("body"),
                                imageUrl = item.optString("imageUrl")
                                    .trim()
                                    .takeIf { it.startsWith("https://", ignoreCase = true) }
                                    .orEmpty(),
                                sourceUrl = item.optString("sourceUrl")
                                    .trim()
                                    .takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
                                    .orEmpty(),
                                images = images,
                                eventStartAt = item.optString("eventStartAt").takeUnless { it == "null" }.orEmpty(),
                                eventEndAt = item.optString("eventEndAt").takeUnless { it == "null" }.orEmpty(),
                                location = item.optString("location")
                            )
                        )
                    }
                }
            } finally {
                connection.disconnect()
            }
        }

    suspend fun fetchCommunityImage(imagePath: String): android.graphics.Bitmap =
        withContext(Dispatchers.IO) {
            val imageUrl = URL(URL(BASE_URL), imagePath)
            require(imageUrl.protocol == "https" || imageUrl.protocol == "http") {
                "The community image URL uses an unsupported protocol."
            }
            val connection = (imageUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 15000
            }
            try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw IOException("Image request failed with HTTP $responseCode.")
                }
                val bytes = connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var totalBytes = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        totalBytes += read
                        if (totalBytes > 2 * 1024 * 1024) {
                            throw IOException("Image exceeds the 2 MB download limit.")
                        }
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                }
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    throw IOException("The content image could not be decoded.")
                }
                val maxDimension = 1600
                var sampleSize = 1
                while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
                    sampleSize *= 2
                }
                val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    ?: throw IOException("The content image could not be decoded.")
            } finally {
                connection.disconnect()
            }
        }

    /**
     * Fetches user notifications from Node.js Express backend API (/api/notifications)
     */
    suspend fun fetchNotificationsFromCloud(): List<CityCareNotification> = withContext(Dispatchers.IO) {
        if (authToken.isNullOrBlank()) {
            throw IOException("Sign in to view your notifications.")
        }
        val connection = (URL("$BASE_URL/notifications").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 10000
            authToken?.takeIf { it.isNotBlank() }?.let {
                setRequestProperty("Authorization", "Bearer $it")
            }
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val message = runCatching { JSONObject(errorText).optString("message") }.getOrNull()
                throw IOException(message?.takeIf { it.isNotBlank() } ?: "Could not load notifications (HTTP $responseCode).")
            }
            val responseText = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            parseNotificationsJson(responseText)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun markNotificationRead(notificationId: String) {
        updateNotificationReadState(
            "$BASE_URL/notifications/${URLEncoder.encode(notificationId, "UTF-8")}/read"
        )
    }

    suspend fun markAllNotificationsRead() {
        updateNotificationReadState("$BASE_URL/notifications/read-all")
    }

    private suspend fun updateNotificationReadState(url: String) = withContext(Dispatchers.IO) {
        if (authToken.isNullOrBlank()) {
            throw IOException("Sign in to update your notifications.")
        }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            connectTimeout = 5000
            readTimeout = 10000
            setRequestProperty("Authorization", "Bearer ${authToken.orEmpty()}")
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val message = runCatching { JSONObject(errorText).optString("message") }.getOrNull()
                throw IOException(message?.takeIf { it.isNotBlank() } ?: "Could not update notification (HTTP $responseCode).")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseNotificationsJson(jsonString: String): List<CityCareNotification> {
        val result = mutableListOf<CityCareNotification>()
        try {
            val jsonObject = JSONObject(jsonString)
            if (!jsonObject.optBoolean("success", false)) {
                throw IOException(jsonObject.optString("message", "Could not load your notifications."))
            }
            val dataArray = jsonObject.optJSONArray("data") ?: JSONArray()
            for (i in 0 until dataArray.length()) {
                val item = dataArray.getJSONObject(i)
                result.add(
                    CityCareNotification(
                        id = item.optString("id"),
                        title = item.optString("title", "Notification"),
                        body = item.optString("body", ""),
                        type = item.optString("type", "GENERAL"),
                        isRead = item.optBoolean("isRead", false),
                        time = item.optString("time", "")
                    )
                )
            }
            return result
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("The notifications response could not be read.", e)
        }
    }
}
