package com.example.razoproject

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.location.Geocoder
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// Color Palette matching Eco UI Design
val EcoHeaderDark = Color(0xFF0F382C)
val EcoHeaderGradientEnd = Color(0xFF1E5641)
val EcoPrimaryGreen = Color(0xFF1E7A38)
val EcoMintAccent = Color(0xFF2EC4B6)
val EcoLimeAccent = Color(0xFF80ED99)
val EcoBannerBg = Color(0xFFE2ECFF)
val EcoCardBorder = Color(0xFFE2E8F0)
val EcoTextDark = Color(0xFF1E293B)
val EcoTextMuted = Color(0xFF64748B)
val EcoHomeTabYellow = Color(0xFFFACC15)

// Backward Compatibility Aliases
val DashboardGreenPrimary = EcoPrimaryGreen
val DashboardGreenDark = Color(0xFF14532D)
val DashboardBannerBg = EcoBannerBg
val DashboardBannerIconBg = Color(0xFF16A34A)
val DashboardCardBorder = EcoCardBorder
val DashboardTextDark = EcoTextDark
val DashboardTextMuted = EcoTextMuted
val DashboardHomeTabYellow = EcoHomeTabYellow

private fun normalizedAreaName(value: String): String =
    value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()

private fun findMatchingAreaSchedule(
    schedule: CityCareCollectionSchedule?,
    residentArea: String
): CityCareAreaCollectionSchedule? {
    val normalizedResidentArea = normalizedAreaName(residentArea)
    if (schedule == null) return null
    if (normalizedResidentArea.isBlank()) {
        return schedule.areaSchedules.firstOrNull { it.appliesToAllAreas }
    }

    val paddedResidentArea = " $normalizedResidentArea "
    val exactMatch = schedule.areaSchedules
        .filter { area ->
            if (area.appliesToAllAreas) return@filter false
            val normalizedScheduleArea = normalizedAreaName(area.areaName)
            normalizedScheduleArea.isNotBlank() &&
                paddedResidentArea.contains(" $normalizedScheduleArea ")
        }
        .maxByOrNull { normalizedAreaName(it.areaName).length }
    return exactMatch ?: schedule.areaSchedules.firstOrNull {
        it.appliesToAllAreas
    }
}

enum class DashboardSubScreen {
    Main,
    NewReport,
    TicketDetails,
    Notifications,
    CollectionSchedule,
    CommunityContent,
    Tips,
    Profile,
    Support,
    MoreDetails
}

data class ActionGridItem(
    val title: String,
    val icon: ImageVector,
    val iconColor: Color
)

data class WasteReportItem(
    val id: String,
    val title: String,
    val barangay: String,
    val date: String,
    val status: String, // "PENDING", "ASSIGNED", "RESOLVED"
    val description: String = "Uncollected waste piled near the drainage. Causing obstruction on the sidewalk.",
    val category: String = "Illegal Dump Site",
    val landmark: String = "Near Barangay Health Center",
    val userEmail: String = "",
    val unitAssigned: String = "Unassigned",
    val dispatcherNotes: String = "",
    val gpsCoordinates: String = "8.4822° N, 124.6175° E",
    val hasCloudPhoto: Boolean = false,
    val photoBase64: String? = null,
    val imageBitmap: Bitmap? = null
)

data class TipItem(
    val title: String,
    val description: String,
    val imageType: String
)

data class NavTabItem(
    val label: String,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector,
    val tabIndex: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityCareDashboardScreen(
    onLogout: () -> Unit = {},
    onRegisterBackHandler: (((() -> Boolean)?) -> Unit) = {}
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0: Home, 1: Reports, 2: More
    var currentSubScreen by rememberSaveable { mutableStateOf(DashboardSubScreen.Main) }
    var selectedMoreItem by rememberSaveable { mutableStateOf<String>("") }
    var reportStatusFilter by rememberSaveable { mutableStateOf("ALL") }
    var showExitDialog by remember { mutableStateOf(false) }
    var webViewBackHandler by remember { mutableStateOf<(() -> Boolean)?>(null) }

    // Register Back Navigation Handler with parent Activity
    DisposableEffect(currentSubScreen, selectedTab) {
        onRegisterBackHandler {
            when {
                currentSubScreen != DashboardSubScreen.Main -> {
                    val handledByWebView = webViewBackHandler?.invoke() ?: false
                    if (!handledByWebView) currentSubScreen = DashboardSubScreen.Main
                    true
                }
                selectedTab != 0 -> {
                    selectedTab = 0
                    true
                }
                else -> {
                    false
                }
            }
        }
        onDispose {
            onRegisterBackHandler(null)
        }
    }

    // Quick Services Grid state & modals
    var selectedCommunityContentType by rememberSaveable { mutableStateOf("recycle") }
    var showReportDialog by remember { mutableStateOf(false) }
    var showReportSuccessModal by remember { mutableStateOf(false) }
    var capturedPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var reportTitleInput by remember { mutableStateOf("") }

    // Reports State List - loaded permanently from disk & MongoDB
    val reportList = remember { mutableStateListOf<WasteReportItem>() }

    var selectedReport by remember {
        mutableStateOf(
            reportList.firstOrNull() ?: WasteReportItem(
                id = "#CDO-2026-0412",
                title = "Brgy. Carmen, Purok 2",
                barangay = "Carmen",
                date = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US).format(Date()),
                status = "PENDING",
                userEmail = CityCareApiService.currentUserEmail
            )
        )
    }

    fun openReportDetails(report: WasteReportItem) {
        selectedReport = report
        currentSubScreen = DashboardSubScreen.TicketDetails
        coroutineScope.launch {
            val latestReport = CityCareApiService.fetchReportDetails(report.id) ?: return@launch
            if (selectedReport.id != latestReport.id) return@launch
            selectedReport = latestReport
            val reportIndex = reportList.indexOfFirst { it.id == latestReport.id }
            if (reportIndex >= 0) {
                reportList[reportIndex] = latestReport
                val cacheIndex = CityCareApiService.userReportsCache.indexOfFirst { it.id == latestReport.id }
                if (cacheIndex >= 0) CityCareApiService.userReportsCache[cacheIndex] = latestReport
                CityCareApiService.saveReportsToDisk(context)
            }
        }
    }

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (currentCoroutineContext().isActive) {
                val locallySaved = CityCareApiService.loadLocalReports(context)
                if (locallySaved.isNotEmpty()) {
                    reportList.clear()
                    reportList.addAll(locallySaved)
                    selectedReport = reportList.firstOrNull { it.id == selectedReport.id }
                        ?: reportList.first()
                }

                val loaded = CityCareApiService.fetchReportsFromCloud(context)
                if (loaded.isNotEmpty()) {
                    reportList.clear()
                    reportList.addAll(loaded)
                    selectedReport = reportList.firstOrNull { it.id == selectedReport.id }
                        ?: reportList.first()
                }
                delay(15_000)
            }
        }
    }

    // Quick Action Grid List
    val actionGridList = remember {
        listOf(
            ActionGridItem("Schedule", Icons.Outlined.Schedule, Color(0xFF334155)),
            ActionGridItem("Recycle", Icons.Outlined.Autorenew, Color(0xFF16A34A)),
            ActionGridItem("Events", Icons.Outlined.Event, Color(0xFFA855F7)),
            ActionGridItem("Tips", Icons.Outlined.Lightbulb, Color(0xFFEAB308)),
            ActionGridItem("Blog", Icons.AutoMirrored.Outlined.Article, Color(0xFF0284C7))
        )
    }

    Scaffold(
        containerColor = Color(0xFFF8FAFC),
        bottomBar = {
            if (currentSubScreen == DashboardSubScreen.Main ||
                currentSubScreen == DashboardSubScreen.Notifications
            ) {
                // Elevated Center Floating Camera FAB Bottom Navigation Bar
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Surface(
                        color = Color.White,
                        shadowElevation = 12.dp,
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Home Tab
                            NavTabItemComposable(
                                label = "Home",
                                activeIcon = Icons.Default.Home,
                                inactiveIcon = Icons.Outlined.Home,
                                isSelected = selectedTab == 0,
                                onClick = {
                                    currentSubScreen = DashboardSubScreen.Main
                                    selectedTab = 0
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // 2. Reports Tab
                            NavTabItemComposable(
                                label = "Reports",
                                activeIcon = Icons.AutoMirrored.Filled.Assignment,
                                inactiveIcon = Icons.AutoMirrored.Outlined.Assignment,
                                isSelected = selectedTab == 1,
                                onClick = {
                                    currentSubScreen = DashboardSubScreen.Main
                                    selectedTab = 1
                                },
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            NavTabItemComposable(
                                label = "Notification",
                                activeIcon = Icons.Default.Notifications,
                                inactiveIcon = Icons.Outlined.Notifications,
                                isSelected = currentSubScreen == DashboardSubScreen.Notifications,
                                onClick = { currentSubScreen = DashboardSubScreen.Notifications },
                                modifier = Modifier.weight(1f)
                            )

                            // More Tab
                            NavTabItemComposable(
                                label = "More",
                                activeIcon = Icons.Default.MoreHoriz,
                                inactiveIcon = Icons.Outlined.MoreHoriz,
                                isSelected = selectedTab == 2,
                                onClick = {
                                    currentSubScreen = DashboardSubScreen.Main
                                    selectedTab = 2
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Center Elevated Floating Camera FAB Button
                    Surface(
                        onClick = { currentSubScreen = DashboardSubScreen.NewReport },
                        shape = CircleShape,
                        color = EcoPrimaryGreen,
                        shadowElevation = 8.dp,
                        border = androidx.compose.foundation.BorderStroke(3.dp, Color.White),
                        modifier = Modifier
                            .offset(y = (-24).dp)
                            .size(56.dp)
                            .align(Alignment.TopCenter)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = "Report Waste Camera",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentSubScreen) {
                DashboardSubScreen.NewReport -> {
                    NewWasteReportScreen(
                        onBack = { currentSubScreen = DashboardSubScreen.Main },
                        onSubmit = { newReport ->
                            reportList.add(0, newReport)
                            selectedReport = newReport
                            selectedTab = 1 // Go to Reports
                            currentSubScreen = DashboardSubScreen.Main
                        }
                    )
                }

                DashboardSubScreen.TicketDetails -> {
                    TicketDetailsScreen(
                        report = selectedReport,
                        onBack = { currentSubScreen = DashboardSubScreen.Main }
                    )
                }

                DashboardSubScreen.Notifications -> {
                    NotificationsScreen(
                        onBack = { currentSubScreen = DashboardSubScreen.Main }
                    )
                }

                DashboardSubScreen.CollectionSchedule -> {
                    CollectionScheduleScreen(
                        onBack = { currentSubScreen = DashboardSubScreen.Main }
                    )
                }

                DashboardSubScreen.CommunityContent -> {
                    CommunityContentScreen(
                        contentType = selectedCommunityContentType,
                        onBack = { currentSubScreen = DashboardSubScreen.Main },
                        onRegisterWebViewBackHandler = { handler -> webViewBackHandler = handler }
                    )
                }

                DashboardSubScreen.Tips -> {
                    CommunityContentScreen(
                        contentType = "tip",
                        onBack = { currentSubScreen = DashboardSubScreen.Main },
                        onRegisterWebViewBackHandler = { handler -> webViewBackHandler = handler }
                    )
                }

                DashboardSubScreen.Profile -> {
                    ProfileScreenContent(
                        onBack = { currentSubScreen = DashboardSubScreen.Main },
                        onLogout = onLogout
                    )
                }

                DashboardSubScreen.Support -> {
                    SupportScreenContent(
                        onBack = { currentSubScreen = DashboardSubScreen.Main },
                        onOpenNotifications = { currentSubScreen = DashboardSubScreen.Notifications },
                        onSelectReport = { report -> openReportDetails(report) }
                    )
                }

                DashboardSubScreen.MoreDetails -> {
                    MoreSettingsDetailScreen(
                        title = selectedMoreItem.ifBlank { "Settings" },
                        onBack = { currentSubScreen = DashboardSubScreen.Main }
                    )
                }

                DashboardSubScreen.Main -> {
                    when (selectedTab) {
                        0 -> HomeScreenContent(
                            actionGridList = actionGridList,
                            reports = reportList,
                            onSelectGridFeature = { featureTitle ->
                                when (featureTitle) {
                                    "Tips" -> currentSubScreen = DashboardSubScreen.Tips
                                    "Schedule" -> currentSubScreen = DashboardSubScreen.CollectionSchedule
                                    "Recycle" -> {
                                        selectedCommunityContentType = "recycle"
                                        currentSubScreen = DashboardSubScreen.CommunityContent
                                    }
                                    "Events" -> {
                                        selectedCommunityContentType = "event"
                                        currentSubScreen = DashboardSubScreen.CommunityContent
                                    }
                                    "Blog" -> {
                                        selectedCommunityContentType = "blog"
                                        currentSubScreen = DashboardSubScreen.CommunityContent
                                    }
                                }
                            },
                            onOpenNewReport = { currentSubScreen = DashboardSubScreen.NewReport },
                            onOpenProfile = { currentSubScreen = DashboardSubScreen.Profile },
                            onSelectReport = { report ->
                                openReportDetails(report)
                            },
                            onViewAllReports = {
                                reportStatusFilter = "ALL"
                                selectedTab = 1
                            },
                            onViewReportsByStatus = { status ->
                                reportStatusFilter = status
                                selectedTab = 1
                            }
                        )

                        1 -> MyReportsScreenContent(
                            reports = reportList,
                            initialStatusFilter = reportStatusFilter,
                            onOpenProfile = { currentSubScreen = DashboardSubScreen.Profile },
                            onSelectReport = { report ->
                                openReportDetails(report)
                            }
                        )

                        2 -> MoreScreenContent(
                            onOpenProfile = { currentSubScreen = DashboardSubScreen.Profile },
                            onOpenSupport = { currentSubScreen = DashboardSubScreen.Support },
                            onOpenItem = { item ->
                                selectedMoreItem = item
                                currentSubScreen = DashboardSubScreen.MoreDetails
                            }
                        )
                    }
                }
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit CityCare CDO?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to close the application?") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        (context as? Activity)?.finish()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("EXIT APP", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showExitDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

// -----------------------------------------------------------------------------
// STANDARD USER PROFILE AVATAR COMPOSABLE
// -----------------------------------------------------------------------------
@Composable
fun UserProfileAvatarComposable(
    modifier: Modifier = Modifier.size(36.dp),
    onClick: () -> Unit = {}
) {
    val initial = CityCareApiService.currentUserName.take(1).uppercase()
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(EcoPrimaryGreen)
            .border(1.5.dp, Color.White, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial.ifEmpty { "U" },
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

// -----------------------------------------------------------------------------
// 1. HOME TAB SCREEN CONTENT
// -----------------------------------------------------------------------------
@Composable
fun HomeScreenContent(
    actionGridList: List<ActionGridItem>,
    reports: List<WasteReportItem>,
    onSelectGridFeature: (String) -> Unit,
    onOpenNewReport: () -> Unit,
    onOpenProfile: () -> Unit,
    onSelectReport: (WasteReportItem) -> Unit,
    onViewAllReports: () -> Unit,
    onViewReportsByStatus: (String) -> Unit = {}
) {
    fun normalizedStatus(report: WasteReportItem) =
        report.status.uppercase().replace('-', '_').replace(' ', '_')

    val pendingCount = reports.count { normalizedStatus(it) == "PENDING" }
    val assignedCount = reports.count { normalizedStatus(it) == "ASSIGNED" }
    val doneCount = reports.count { normalizedStatus(it) in setOf("RESOLVED", "DONE", "COMPLETED") }
    var residentSchedule by remember { mutableStateOf<CityCareCollectionSchedule?>(null) }
    var residentAreaSchedule by remember { mutableStateOf<CityCareAreaCollectionSchedule?>(null) }
    var isLoadingResidentSchedule by remember { mutableStateOf(true) }
    var residentScheduleUnavailable by remember { mutableStateOf(false) }

    LaunchedEffect(CityCareApiService.currentUserBarangay, CityCareApiService.currentUserPurok) {
        isLoadingResidentSchedule = true
        residentScheduleUnavailable = false
        try {
            residentSchedule = CityCareApiService
                .fetchSchedulesFromCloud(CityCareApiService.currentUserBarangay)
                .firstOrNull()
            residentAreaSchedule = findMatchingAreaSchedule(
                residentSchedule,
                CityCareApiService.currentUserPurok
            )
        } catch (error: Exception) {
            residentSchedule = null
            residentAreaSchedule = null
            residentScheduleUnavailable = true
        } finally {
            isLoadingResidentSchedule = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Profile & Hero Emerald Section
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(EcoHeaderDark, EcoHeaderGradientEnd)
                        )
                    )
                    .padding(horizontal = 18.dp, vertical = 20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Profile Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.clickable { onOpenProfile() }
                        ) {
                            UserProfileAvatarComposable(
                                modifier = Modifier.size(44.dp),
                                onClick = onOpenProfile
                            )

                            Column {
                                Text(
                                    text = "Hello, ${CityCareApiService.currentUserName.substringBefore(" ")}!",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = EcoLimeAccent,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = CityCareApiService.currentUserLocation,
                                        fontSize = 11.sp,
                                        color = Color(0xFFE2E8F0)
                                    )
                                }
                            }
                        }

                    }

                    // Eco Impact & eWaste Stats Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Eco Score & Recycling", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                                Text("12.5 kg Recycled", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                Text("🌱 +85 Eco Impact Points", fontSize = 10.sp, color = EcoLimeAccent, fontWeight = FontWeight.Bold)
                            }

                            Surface(
                                color = EcoLimeAccent,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Active Citizen",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EcoHeaderDark,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Padding container for lower items
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 2. Collection Schedule Banner
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = EcoBannerBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF16A34A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (residentAreaSchedule?.isSample == true) {
                                    "SAMPLE schedule — not official:"
                                } else {
                                    "CLENRO Collection Schedule:"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = EcoTextDark
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when {
                                    isLoadingResidentSchedule -> "Loading schedule..."
                                    residentScheduleUnavailable -> "Schedule is currently unavailable."
                                    residentSchedule == null -> "No schedule published for ${CityCareApiService.currentUserBarangay}."
                                    CityCareApiService.currentUserPurok.isBlank() &&
                                        residentAreaSchedule?.appliesToAllAreas != true ->
                                        "Add your purok, zone, or street in your profile to find its collection route."
                                    residentAreaSchedule == null ->
                                        "No area-specific schedule is published for ${CityCareApiService.currentUserPurok}."
                                    else -> buildString {
                                        if (residentAreaSchedule?.isSample == true) {
                                            append("SAMPLE ONLY — not an official CLENRO schedule.\n")
                                        }
                                        append("Area: ${residentAreaSchedule?.areaName.orEmpty()}")
                                        append("\nBiodegradable: ${residentAreaSchedule?.biodegradableDays.orEmpty()}")
                                        append("\nNon-biodegradable: ${residentAreaSchedule?.nonBiodegradableDays.orEmpty()}")
                                        append("\nTime: ${residentAreaSchedule?.timeSlot.orEmpty()}")
                                        append("\nTruck: ${residentAreaSchedule?.assignedTruck.orEmpty()}")
                                    }
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = EcoTextMuted,
                                lineHeight = 15.sp
                            )
                        }

                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = Color(0xFF93C5FD),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // 3. Dynamic Quick Status Summary Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        onClick = { onViewReportsByStatus("PENDING") },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EcoCardBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$pendingCount",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFEAB308)
                            )
                            Text(
                                text = "PENDING",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = EcoTextMuted
                            )
                        }
                    }

                    Card(
                        onClick = { onViewReportsByStatus("ASSIGNED") },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EcoCardBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$assignedCount",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0284C7)
                            )
                            Text(
                                text = "ASSIGNED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = EcoTextMuted
                            )
                        }
                    }

                    Card(
                        onClick = { onViewReportsByStatus("DONE") },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EcoCardBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$doneCount",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF16A34A)
                            )
                            Text(
                                text = "DONE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = EcoTextMuted
                            )
                        }
                    }
                }

                // 4. Quick Services Action Feature Grid
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Quick Services",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = EcoTextDark
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        actionGridList.chunked(3).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowItems.forEach { item ->
                                QuickActionCard(
                                    item = item,
                                    onClick = { onSelectGridFeature(item.title) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(3 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                        }
                    }
                }

                // 5. Recent Reports Header & Item
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Reports",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = EcoTextDark
                    )
                    Text(
                        text = "View All",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF16A34A),
                        modifier = Modifier.clickable { onViewAllReports() }
                    )
                }

                // Recent Report Card (Loads latest submitted report permanently)
                val latestReport = reports.firstOrNull() ?: WasteReportItem(
                    id = "#CDO-2026-0412",
                    title = "Brgy. Carmen, Purok 2",
                    barangay = "Carmen",
                    date = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US).format(Date()),
                    status = "PENDING",
                    userEmail = CityCareApiService.currentUserEmail
                )

                Card(
                    onClick = { onSelectReport(latestReport) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EcoCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusBadgeChip(status = latestReport.status)

                            Text(
                                text = latestReport.date,
                                fontSize = 11.sp,
                                color = EcoTextMuted
                            )
                        }

                        Text(
                            text = latestReport.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = EcoTextDark
                        )

                        Text(
                            text = "📍 ${latestReport.barangay}, Cagayan de Oro City",
                            fontSize = 12.sp,
                            color = EcoTextMuted
                        )

                        MapPreviewBox(
                            heightDp = 100,
                            locationName = "${latestReport.barangay}, Cagayan de Oro City",
                            gpsCoordinates = latestReport.gpsCoordinates
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 2. NEW WASTE REPORT FORM SCREEN WITH 100% ACCURATE REAL-TIME TIMESTAMP & PERMANENT STORAGE
// -----------------------------------------------------------------------------
@Composable
private fun QuickActionCard(
    item: ActionGridItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = item.iconColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = EcoTextDark
            )
        }
    }
}

@Composable
fun NewWasteReportScreen(
    onBack: () -> Unit,
    onSubmit: (WasteReportItem) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var capturedPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedCategory by rememberSaveable { mutableStateOf("Illegal Dump Site") }
    var gpsCoordinates by rememberSaveable { mutableStateOf("") }
    var barangayInput by rememberSaveable { mutableStateOf("") }
    var purokInput by rememberSaveable { mutableStateOf("") }
    var locationStatus by rememberSaveable { mutableStateOf("Getting your current location...") }
    var isFetchingLocation by remember { mutableStateOf(false) }
    var locationFetchJob by remember { mutableStateOf<Job?>(null) }
    var landmarkInput by rememberSaveable { mutableStateOf("") }
    var remarksInput by rememberSaveable { mutableStateOf("") }

    fun fetchCurrentLocation() {
        locationFetchJob?.cancel()
        gpsCoordinates = ""
        barangayInput = ""
        purokInput = ""
        isFetchingLocation = true
        locationStatus = "Getting your current location..."
        locationFetchJob = coroutineScope.launch {
            try {
                val currentLocation = fetchRealDeviceLocation(context)
                gpsCoordinates = currentLocation.gpsCoordinates
                barangayInput = currentLocation.barangay
                purokInput = currentLocation.purokZone
                locationStatus = if (currentLocation.barangay == "GPS location") {
                    "GPS found; street address is unavailable"
                } else {
                    "Current GPS location found"
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                locationStatus = e.message ?: "Could not get your current location. Try again."
            } finally {
                if (locationFetchJob == currentCoroutineContext()[Job]) {
                    isFetchingLocation = false
                }
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasPermission =
            permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasPermission) {
            fetchCurrentLocation()
        } else {
            locationStatus = "Allow location access to place this report at your current location."
        }
    }

    LaunchedEffect(Unit) {
        if (hasDeviceLocationPermission(context)) {
            fetchCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            capturedPhotoBitmap = bitmap
            if (hasDeviceLocationPermission(context)) {
                fetchCurrentLocation()
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                cameraLauncher.launch(null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun safeLaunchCamera() {
        if (hasDeviceLocationPermission(context)) {
            fetchCurrentLocation()
        }
        try {
            cameraLauncher.launch(null)
        } catch (_: SecurityException) {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val categories = listOf(
        "Clogged Drainage Waste",
        "Fallen Tree",
        "Illegal Dump Site",
        "Overflowing Bins",
        "Road Repair",
        "Street Lighting",
        "Uncollected Household Trash",
        "Waste Collection"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        Surface(
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = EcoTextDark
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "New Waste Report",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = EcoTextDark
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(10.dp)) }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Photo Evidence",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = EcoTextDark
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF1F5F9))
                            .border(
                                width = 1.dp,
                                color = Color(0xFFCBD5E1),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { safeLaunchCamera() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (capturedPhotoBitmap != null) {
                            Image(
                                bitmap = capturedPhotoBitmap!!.asImageBitmap(),
                                contentDescription = "Trash Evidence",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoCamera,
                                    contentDescription = null,
                                    tint = EcoTextMuted,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Tap camera to capture trash image",
                                    fontSize = 12.sp,
                                    color = EcoTextMuted
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EcoCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = EcoPrimaryGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Location Details (GPS Anti-Fraud Locked)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = EcoTextDark
                            )
                        }

                        if (gpsCoordinates.isNotBlank()) {
                                MapPreviewBox(
                                heightDp = 110,
                                locationName = listOf(purokInput, barangayInput)
                                    .filter { it.isNotBlank() }
                                    .joinToString(", ")
                                    .ifBlank { "Current GPS location" },
                                gpsCoordinates = gpsCoordinates
                            )
                            Text(
                                text = "GPS coordinates: $gpsCoordinates",
                                fontSize = 11.sp,
                                color = EcoTextMuted
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFF1F5F9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Map will show after GPS is found",
                                    fontSize = 12.sp,
                                    color = EcoTextMuted
                                )
                            }
                            Text(
                                text = locationStatus,
                                fontSize = 12.sp,
                                color = EcoTextMuted
                            )
                            TextButton(
                                onClick = {
                                    if (hasDeviceLocationPermission(context)) {
                                        fetchCurrentLocation()
                                    } else {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                }
                            ) {
                                Text("Retry current location")
                            }
                        }

                        Text("Barangay (Auto-Detected from Map)", fontSize = 11.sp, color = EcoPrimaryGreen, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = barangayInput,
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            trailingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = "Anti-Fraud Locked", tint = EcoPrimaryGreen)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = EcoTextDark,
                                disabledBorderColor = EcoPrimaryGreen,
                                disabledLabelColor = EcoPrimaryGreen,
                                disabledContainerColor = Color(0xFFF0FDF4)
                            )
                        )

                        Text("Purok / Zone / Street (Auto-Detected from Map)", fontSize = 11.sp, color = EcoPrimaryGreen, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = purokInput,
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            trailingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = "Anti-Fraud Locked", tint = EcoPrimaryGreen)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = EcoTextDark,
                                disabledBorderColor = EcoPrimaryGreen,
                                disabledLabelColor = EcoPrimaryGreen,
                                disabledContainerColor = Color(0xFFF0FDF4)
                            )
                        )

                        Text("Optional Landmark", fontSize = 11.sp, color = EcoTextMuted)
                        OutlinedTextField(
                            value = landmarkInput,
                            onValueChange = { landmarkInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Incident Category",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = EcoTextDark
                        )
                        Text(
                            text = "Select one",
                            fontSize = 10.sp,
                            color = EcoTextMuted
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CategoryChip(
                                label = categories[0],
                                selected = selectedCategory == categories[0],
                                onClick = { selectedCategory = categories[0] }
                            )
                            CategoryChip(
                                label = categories[1],
                                selected = selectedCategory == categories[1],
                                onClick = { selectedCategory = categories[1] }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CategoryChip(
                                label = categories[2],
                                selected = selectedCategory == categories[2],
                                onClick = { selectedCategory = categories[2] }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CategoryChip(
                                label = categories[3],
                                selected = selectedCategory == categories[3],
                                onClick = { selectedCategory = categories[3] }
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Additional Remarks",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = EcoTextDark
                    )

                    OutlinedTextField(
                        value = remarksInput,
                        onValueChange = { remarksInput = it },
                        placeholder = { Text("Describe concern...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            item {
                Button(
                    enabled = gpsCoordinates.isNotBlank() && !isFetchingLocation,
                    onClick = {
                        if (gpsCoordinates.isBlank()) {
                            Toast.makeText(
                                context,
                                "Wait for your current GPS location before submitting.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@Button
                        }
                        val newId = "#CDO-2026-" + (1000..9999).random()
                        // 100% Accurate Real-Time Posted Timestamp
                        val accuratePostedTime = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US).format(Date())
                        val photoBase64Str = capturedPhotoBitmap?.let { CityCareApiService.bitmapToBase64(it) }

                        val reportLocationLabel = listOf(barangayInput, purokInput)
                            .filterNot { it == "GPS location" || it == "Address unavailable" }
                            .filter { it.isNotBlank() }
                            .joinToString(", ")
                        val newReport = WasteReportItem(
                            id = newId,
                            title = reportLocationLabel.ifBlank { "Location: $gpsCoordinates" },
                            barangay = barangayInput,
                            date = accuratePostedTime,
                            status = "PENDING",
                            description = remarksInput,
                            category = selectedCategory,
                            landmark = landmarkInput,
                            userEmail = CityCareApiService.currentUserEmail,
                            gpsCoordinates = gpsCoordinates,
                            photoBase64 = photoBase64Str,
                            imageBitmap = capturedPhotoBitmap
                        )

                        // Save permanently to local disk storage & MongoDB database
                        CityCareApiService.addLocalReport(context, newReport)
                        coroutineScope.launch {
                            val synced = CityCareApiService.syncPendingReport(context, newReport)
                            val message = if (synced) {
                                "Report submitted to CLENRO."
                            } else {
                                "Report saved on this device; upload will retry when connected."
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }

                        Toast.makeText(context, "Report saved. Connecting to CLENRO...", Toast.LENGTH_SHORT).show()
                        onSubmit(newReport)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EcoPrimaryGreen)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SUBMIT INCIDENT REPORT",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private data class ReportDeviceLocation(
    val gpsCoordinates: String,
    val barangay: String,
    val purokZone: String
)

private fun hasDeviceLocationPermission(context: android.content.Context): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

private suspend fun fetchRealDeviceLocation(
    context: android.content.Context
): ReportDeviceLocation = withContext(Dispatchers.IO) {
    if (!hasDeviceLocationPermission(context)) {
        throw SecurityException("Location permission is required to place this report.")
    }

    val locationClient =
        com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
    val location = try {
        com.google.android.gms.tasks.Tasks.await(
            locationClient.getCurrentLocation(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                com.google.android.gms.tasks.CancellationTokenSource().token
            ),
            30,
            TimeUnit.SECONDS
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw IOException("Could not get your current GPS location. Check that location is enabled and retry.", e)
    } ?: throw IOException("No GPS fix was returned. Move to an open area and retry.")

    val latitude = location.latitude
    val longitude = location.longitude
    val latitudeDirection = if (latitude >= 0) "N" else "S"
    val longitudeDirection = if (longitude >= 0) "E" else "W"
    val formattedGps = String.format(
        Locale.US,
        "%.6f° %s, %.6f° %s",
        kotlin.math.abs(latitude),
        latitudeDirection,
        kotlin.math.abs(longitude),
        longitudeDirection
    )

    val address = try {
        if (Geocoder.isPresent()) {
            @Suppress("DEPRECATION")
            Geocoder(context, Locale.getDefault())
                .getFromLocation(latitude, longitude, 1)
                ?.firstOrNull()
        } else {
            null
        }
    } catch (e: IOException) {
        android.util.Log.w("CityCareLocation", "Reverse geocoding is unavailable", e)
        null
    }

    ReportDeviceLocation(
        gpsCoordinates = formattedGps,
        barangay = address?.subLocality
            ?: address?.subAdminArea
            ?: address?.locality
            ?: "GPS location",
        purokZone = address?.thoroughfare
            ?: address?.featureName
            ?: "Address unavailable"
    )
}

// -----------------------------------------------------------------------------
// 3. MY REPORTS TAB CONTENT
// -----------------------------------------------------------------------------
@Composable
fun MyReportsScreenContent(
    reports: List<WasteReportItem>,
    initialStatusFilter: String = "ALL",
    onOpenProfile: () -> Unit,
    onSelectReport: (WasteReportItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember(initialStatusFilter) { mutableStateOf(initialStatusFilter) }

    val filteredReports = reports.filter { report ->
        val normalizedStatus = report.status.uppercase().replace('-', '_').replace(' ', '_')
        val matchesStatus = when (selectedFilter) {
            "ALL" -> true
            "DONE" -> normalizedStatus in setOf("RESOLVED", "DONE", "COMPLETED")
            else -> normalizedStatus == selectedFilter
        }
        val matchesSearch = report.title.contains(searchQuery, ignoreCase = true) ||
                report.id.contains(searchQuery, ignoreCase = true)
        val matchesUser = report.userEmail.isEmpty() || report.userEmail.equals(CityCareApiService.currentUserEmail, ignoreCase = true)
        matchesStatus && matchesSearch && matchesUser
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(20.dp))

            // Standard Top Header Bar with Safe Clearance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Reports",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = EcoTextDark
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    UserProfileAvatarComposable(
                        modifier = Modifier.size(36.dp),
                        onClick = onOpenProfile
                    )

                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search street or ID...", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = EcoTextMuted)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, EcoCardBorder),
                    modifier = Modifier
                        .size(52.dp)
                        .clickable { }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Filter",
                            tint = EcoTextDark
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                listOf("ALL", "PENDING", "ASSIGNED", "IN_PROGRESS", "DONE", "REJECTED").forEach { filter ->
                    val isSelected = selectedFilter == filter
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { selectedFilter = filter }
                    ) {
                        Text(
                            text = if (filter == "ALL") filter else filter.replace('_', ' '),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (isSelected) EcoPrimaryGreen else EcoTextMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(2.dp)
                                .background(if (isSelected) EcoPrimaryGreen else Color.Transparent)
                        )
                    }
                }
            }
        }

        items(filteredReports) { report ->
            Card(
                onClick = { onSelectReport(report) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, EcoCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReportImageThumbnail(
                        bitmap = report.imageBitmap,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = report.id,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = EcoTextMuted
                            )
                            StatusBadgeChip(status = report.status)
                        }

                        Text(
                            text = report.title,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = EcoTextDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "🕒 ${report.date}",
                            fontSize = 10.sp,
                            color = EcoTextMuted
                        )
                    }
                }
            }
        }

        if (filteredReports.isEmpty()) {
            item {
                Text(
                    text = if (reports.isEmpty()) {
                        "No reports are saved on this device yet."
                    } else {
                        "No reports match your search or selected status."
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    color = EcoTextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

// -----------------------------------------------------------------------------
// 4. SUPPORT TAB CONTENT
// -----------------------------------------------------------------------------
@Composable
fun SupportScreenContent(
    onBack: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onSelectReport: (WasteReportItem) -> Unit = {}
) {
    val context = LocalContext.current
    var selectedYear by remember { mutableStateOf("2026") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = EcoPrimaryGreen
                    )
                }
                Text("Support", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = EcoPrimaryGreen)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF1F5F9)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        drawRect(color = Color(0xFF3B3B58), topLeft = Offset(w * 0.15f, h * 0.55f), size = androidx.compose.ui.geometry.Size(w * 0.7f, h * 0.35f))
                        drawCircle(color = Color(0xFF2C2C3E), radius = h * 0.22f, center = Offset(w * 0.5f, h * 0.32f))
                    }
                    Icon(
                        imageVector = Icons.Default.SupportAgent,
                        contentDescription = "Support Agent",
                        tint = Color(0xFF1E293B),
                        modifier = Modifier.size(54.dp)
                    )
                }

                Text(
                    text = "Need help? Contact CLENRO or review your complaint history.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = EcoTextDark,
                    lineHeight = 19.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            var showContactChoiceDialog by remember { mutableStateOf(false) }

            OutlinedButton(
                    onClick = { showContactChoiceDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2D4B3E)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2D4B3E)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text(
                        text = "Contact CLENRO",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

            if (showContactChoiceDialog) {
                AlertDialog(
                    onDismissRequest = { showContactChoiceDialog = false },
                    title = {
                        Text(
                            text = "CLENRO Customer Support",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = EcoTextDark
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Choose how you would like to reach the City Environment & Natural Resources Office:",
                                fontSize = 12.sp,
                                color = EcoTextMuted
                            )

                            // Option 1: Call Landline
                            Card(
                                onClick = {
                                    showContactChoiceDialog = false
                                    try {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:0888572280"))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Hotline: (088) 857-2280", Toast.LENGTH_LONG).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = EcoPrimaryGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text("Call CLENRO Landline", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EcoTextDark)
                                        Text("(088) 857-2280 / (088) 857-4140", fontSize = 11.sp, color = EcoTextMuted)
                                    }
                                }
                            }

                            // Option 2: Email Gmail
                            Card(
                                onClick = {
                                    showContactChoiceDialog = false
                                    try {
                                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:clenro@cagayandeoro.gov.ph")).apply {
                                            putExtra(Intent.EXTRA_EMAIL, arrayOf("clenro@cagayandeoro.gov.ph", "clenro.cdo@gmail.com"))
                                            putExtra(Intent.EXTRA_SUBJECT, "CityCare Resident Support Inquiry")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Email: clenro@cagayandeoro.gov.ph", Toast.LENGTH_LONG).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        tint = EcoPrimaryGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text("Email CLENRO Support", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EcoTextDark)
                                        Text("clenro@cagayandeoro.gov.ph", fontSize = 11.sp, color = EcoTextMuted)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showContactChoiceDialog = false }) {
                            Text("CLOSE", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Complaint History",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = EcoTextDark
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EcoCardBorder),
                    color = Color.White
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedYear,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = EcoTextDark
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = EcoTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        item {
            Card(
                onClick = {
                    onSelectReport(
                        WasteReportItem(
                            id = "#CDO-2026-0412",
                            title = "Bin Not Collected on Time",
                            barangay = "Carmen",
                            date = "Today, 8:45 AM",
                            status = "PENDING",
                            description = "Missed pickup for household waste."
                        )
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, EcoCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Bin Not Collected on Time",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = EcoTextDark
                        )
                        Text(
                            text = "Missed Pickup",
                            fontSize = 11.sp,
                            color = EcoTextMuted
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = EcoTextDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

// -----------------------------------------------------------------------------
// 5. MORE TAB CONTENT
// -----------------------------------------------------------------------------
@Composable
fun MoreScreenContent(
    onOpenProfile: () -> Unit = {},
    onOpenSupport: () -> Unit = {},
    onOpenItem: (String) -> Unit = {}
) {
    val moreItems = listOf(
        "Profile",
        "Support",
        "Privacy Settings",
        "Payment Methods",
        "Legal Information"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(14.dp))
        }

        items(moreItems) { title ->
            Card(
                onClick = {
                    if (title == "Profile") {
                        onOpenProfile()
                    } else if (title == "Support") {
                        onOpenSupport()
                    } else {
                        onOpenItem(title)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, EcoCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = EcoTextDark
                    )

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = EcoTextDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, EcoCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "App Version",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = EcoTextDark
                    )

                    Text(
                        text = "1.0.1",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = EcoTextDark
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MoreSettingsDetailScreen(
    title: String,
    onBack: () -> Unit
) {
    val details = remember(title) {
        when (title) {
            "Privacy Settings" -> listOf(
                "Profile visibility is set to Private",
                "Location access is used only for service matching",
                "Personal data is used to improve city waste alerts"
            )
            "Payment Methods" -> listOf(
                "No active payment method linked yet",
                "Wallet balance: Php 0.00",
                "Payment setup can be completed under billing settings"
            )
            "Legal Information" -> listOf(
                "Terms of service updated for 2026",
                "Privacy policy is available in the app help center",
                "Data handling follows local digital service standards"
            )
            else -> emptyList()
        }
    }

    val summary = remember(title) {
        when (title) {
            "Privacy Settings" -> "Manage your profile visibility, data sharing, and account protection."
            "Payment Methods" -> "Review all your saved billing and payment preferences."
            "Legal Information" -> "Read the policies and legal guidance for using CityCare."
            else -> "View your CityCare account settings."
        }
    }

    Surface(
        color = Color(0xFFF8FAFC),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EcoHeaderDark,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EcoCardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = title,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = EcoTextDark
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = summary,
                                fontSize = 13.sp,
                                color = EcoTextMuted
                            )
                        }
                    }
                }

                items(details) { detail ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EcoCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EcoPrimaryGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = detail,
                                fontSize = 13.sp,
                                color = EcoTextDark,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                item {
                    Button(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = EcoPrimaryGreen)
                    ) {
                        Text("Back to More")
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 6. TICKET DETAILS SCREEN
// -----------------------------------------------------------------------------
@Composable
fun TicketDetailsScreen(
    report: WasteReportItem,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val reportStatus = report.status.uppercase().replace('-', '_').replace(' ', '_')
    val currentStep = when (reportStatus) {
        "ASSIGNED" -> 3
        "IN_PROGRESS" -> 4
        "RESOLVED" -> 5
        "REJECTED" -> 2
        else -> 2
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        Surface(
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = EcoTextDark
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ticket Details",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = EcoPrimaryGreen
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(10.dp)) }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Ticket ${report.id}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = EcoPrimaryGreen
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { Toast.makeText(context, "Share link copied!", Toast.LENGTH_SHORT).show() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { Toast.makeText(context, "Ticket cancellation requested.", Toast.LENGTH_SHORT).show() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cancel", fontSize = 12.sp)
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EcoCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(10.dp))
                        ) {
                            ReportImageThumbnail(
                                bitmap = report.imageBitmap,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                color = Color(0xFF0284C7),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .padding(8.dp)
                                    .align(Alignment.TopStart)
                            ) {
                                StatusBadgeChip(status = report.status)
                            }
                        }

                        Text(
                            text = "Reported Issue",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = EcoTextDark
                        )
                        Text(
                            text = report.description,
                            fontSize = 11.sp,
                            color = EcoTextMuted,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EcoCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MapPreviewBox(
                            heightDp = 110,
                            locationName = "${report.barangay}, Cagayan de Oro",
                            gpsCoordinates = report.gpsCoordinates
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = EcoPrimaryGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "District 1 - ${report.barangay}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EcoTextDark
                                )
                                Text(
                                    text = report.title,
                                    fontSize = 11.sp,
                                    color = EcoTextMuted
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EcoCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Resolution Progress",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = EcoTextDark
                            )

                            val (statusLabel, statusColor) = when (reportStatus) {
                                "ASSIGNED" -> "Assigned" to Color(0xFF0284C7)
                                "IN_PROGRESS" -> "In Progress" to Color(0xFF7C3AED)
                                "RESOLVED" -> "Resolved" to Color(0xFF16A34A)
                                "REJECTED" -> "Rejected" to Color(0xFFDC2626)
                                else -> "Pending Review" to Color(0xFFD97706)
                            }
                            Surface(color = statusColor, shape = RoundedCornerShape(12.dp)) {
                                Text(
                                    text = statusLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        TimelineStepItem(
                            stepNumber = 1,
                            title = "Report Submitted",
                            subtitle = report.date,
                            isCompleted = true,
                            isCurrent = false
                        )

                        TimelineStepItem(
                            stepNumber = 2,
                            title = if (reportStatus == "REJECTED") {
                                "Report rejected by CLENRO"
                            } else {
                                "Acknowledged by Barangay/CLENRO"
                            },
                            subtitle = when (reportStatus) {
                                "PENDING" -> "Awaiting CLENRO review"
                                "REJECTED" -> report.dispatcherNotes.ifBlank { "Contact support for more information" }
                                else -> "Reviewed by CLENRO"
                            },
                            isCompleted = currentStep > 2,
                            isCurrent = currentStep == 2
                        )

                        TimelineStepItem(
                            stepNumber = 3,
                            title = "Assigned to Dispatch Driver",
                            subtitle = when {
                                reportStatus == "REJECTED" -> "Dispatch is not proceeding for this report"
                                report.unitAssigned == "Unassigned" -> "Awaiting unit assignment"
                                else -> "Assigned to ${report.unitAssigned}"
                            },
                            isCompleted = currentStep > 3,
                            isCurrent = currentStep == 3,
                            extraContent = {
                                if (report.unitAssigned != "Unassigned") {
                                    Surface(
                                        color = Color(0xFFEFF6FF),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF0284C7)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.LocalShipping,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(report.unitAssigned, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EcoTextDark)
                                                Text("Assigned Dispatch Unit", fontSize = 10.sp, color = EcoTextMuted)
                                            }
                                        }
                                    }
                                }
                            }
                        )

                        TimelineStepItem(
                            stepNumber = 4,
                            title = "Resolution In-Progress",
                            subtitle = when (reportStatus) {
                                "IN_PROGRESS" -> "CLENRO team is working on your report"
                                "RESOLVED" -> "Field work has been completed"
                                "REJECTED" -> "No field work will be scheduled"
                                else -> "Awaiting field work"
                            },
                            isCompleted = currentStep > 4,
                            isCurrent = currentStep == 4
                        )

                        TimelineStepItem(
                            stepNumber = 5,
                            title = "Cleanup Verified & Closed",
                            subtitle = if (reportStatus == "RESOLVED") "Resolved by CLENRO" else "Awaiting resolution",
                            isCompleted = reportStatus == "RESOLVED",
                            isCurrent = reportStatus == "RESOLVED",
                            isLast = true
                        )
                    }
                }
            }

            if (report.dispatcherNotes.isNotBlank()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EcoCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Update from CLENRO",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = EcoTextDark
                            )
                            Text(
                                text = report.dispatcherNotes,
                                fontSize = 12.sp,
                                color = EcoTextMuted
                            )
                        }
                    }
                }
            }

            item {
                var showSupportDialog by remember { mutableStateOf(false) }

                OutlinedButton(
                    onClick = { showSupportDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EcoPrimaryGreen)
                ) {
                    Icon(Icons.Default.HeadsetMic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CONTACT SUPPORT / HOTLINE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                if (showSupportDialog) {
                    AlertDialog(
                        onDismissRequest = { showSupportDialog = false },
                        title = {
                            Text("CLENRO Customer Service", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Contact City Environment & Natural Resources Office:", fontSize = 12.sp, color = EcoTextMuted)

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .clickable {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:0888572280"))
                                            context.startActivity(intent)
                                        }
                                        .padding(10.dp)
                                ) {
                                    Text("Landline Hotline", fontSize = 10.sp, color = EcoTextMuted)
                                    Text("(088) 857-2280 / (088) 857-4140", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EcoTextDark)
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .clickable {
                                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:clenro@cagayandeoro.gov.ph"))
                                            context.startActivity(intent)
                                        }
                                        .padding(10.dp)
                                ) {
                                    Text("Customer Service Email", fontSize = 10.sp, color = EcoTextMuted)
                                    Text("clenro@cagayandeoro.gov.ph", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EcoTextDark)
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showSupportDialog = false }) {
                                Text("CLOSE", fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// -----------------------------------------------------------------------------
// COLLECTION SCHEDULE SCREEN
// -----------------------------------------------------------------------------
@Composable
fun CollectionScheduleScreen(
    onBack: () -> Unit
) {
    var schedules by remember { mutableStateOf<List<CityCareCollectionSchedule>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        isLoading = true
        loadError = null
        try {
            schedules = CityCareApiService.fetchSchedulesFromCloud()
        } catch (error: Exception) {
            loadError = "Could not load collection schedules. Check your connection and try again."
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        Surface(
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = EcoTextDark
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Collection Schedule",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = EcoPrimaryGreen
                )
            }
        }

        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = EcoPrimaryGreen)
            }

            loadError != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = loadError.orEmpty(),
                    color = EcoTextMuted,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { refreshKey++ },
                    colors = ButtonDefaults.buttonColors(containerColor = EcoPrimaryGreen)
                ) {
                    Text("Retry")
                }
            }

            schedules.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No collection schedules have been published yet.",
                    color = EcoTextMuted,
                    textAlign = TextAlign.Center
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                contentPadding = PaddingValues(vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Collection routes by barangay, including any clearly labeled sample schedules.",
                        fontSize = 13.sp,
                        color = EcoTextMuted
                    )
                }
                items(schedules, key = { it.barangay }) { schedule ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, EcoCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Barangay ${schedule.barangay}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = EcoTextDark
                            )
                            if (schedule.areaSchedules.isEmpty()) {
                                Text(
                                    text = "No purok-, zone-, or street-specific route has been published.",
                                    fontSize = 12.sp,
                                    color = EcoTextMuted
                                )
                            } else {
                                schedule.areaSchedules.forEach { areaSchedule ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = areaSchedule.areaName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = EcoPrimaryGreen
                                        )
                                        if (areaSchedule.isSample) {
                                            Text(
                                                text = "SAMPLE ONLY — NOT AN OFFICIAL OR CONFIRMED CLENRO ROUTE",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF9A3412)
                                            )
                                        }
                                        if (areaSchedule.appliesToAllAreas) {
                                            Text(
                                                text = "Example fallback only; exact area routes take priority.",
                                                fontSize = 11.sp,
                                                color = EcoTextMuted
                                            )
                                        }
                                        CollectionScheduleDetail(
                                            label = "Biodegradable",
                                            value = areaSchedule.biodegradableDays
                                        )
                                        CollectionScheduleDetail(
                                            label = "Non-biodegradable",
                                            value = areaSchedule.nonBiodegradableDays
                                        )
                                        CollectionScheduleDetail(
                                            label = "Collection time",
                                            value = areaSchedule.timeSlot
                                        )
                                        CollectionScheduleDetail(
                                            label = "Assigned truck",
                                            value = areaSchedule.assignedTruck
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionScheduleDetail(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$label:",
            modifier = Modifier.width(132.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = EcoTextMuted
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            color = EcoTextDark
        )
    }
}

@Composable
fun NotificationsScreen(
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        Surface(
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = EcoTextDark
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Notifications",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = EcoPrimaryGreen
                    )
                }

                Surface(
                    color = EcoPrimaryGreen,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "2 New",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(10.dp)) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("RECENT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EcoTextMuted)
                    Text("Mark all as read", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EcoPrimaryGreen)
                }
            }

            item {
                NotificationCardItem(
                    title = "Your report #CDO-0412 in Zone 3 Carmen was marked RESOLVED.",
                    time = "10 mins ago",
                    icon = Icons.Default.CheckCircle,
                    iconBg = Color(0xFFDCFCE7),
                    iconColor = Color(0xFF166534),
                    borderColor = Color(0xFF22C55E)
                )
            }

            item {
                NotificationCardItem(
                    title = "CLENRO dispatched Truck #4 to your reported location in Lapasan.",
                    time = "2 hours ago",
                    icon = Icons.Default.LocalShipping,
                    iconBg = Color(0xFFDBEAFE),
                    iconColor = Color(0xFF1E40AF),
                    borderColor = Color(0xFF0284C7)
                )
            }

            item {
                NotificationCardItem(
                    title = "Reminder: Schedule change for waste collection in Barangay Macasandig this holiday weekend.",
                    time = "Yesterday, 08:00 AM",
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    iconBg = Color(0xFFE0EBFF),
                    iconColor = Color(0xFF2563EB),
                    borderColor = Color.Transparent
                )
            }

            item {
                NotificationCardItem(
                    title = "Report #CDO-0401 collection is delayed due to heavy traffic on CM Recto Avenue.",
                    time = "Yesterday, 02:30 PM",
                    icon = Icons.Default.Warning,
                    iconBg = Color(0xFFFEF08A),
                    iconColor = Color(0xFF854D0E),
                    borderColor = Color.Transparent
                )
            }

            item {
                OutlinedButton(
                    onClick = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Text("Load More Older Notifications", fontSize = 12.sp, color = EcoPrimaryGreen)
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 8. TIPS SCREEN
// -----------------------------------------------------------------------------
@Composable
fun TipsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val tipsList = remember {
        listOf(
            TipItem(
                title = "Waste Reduction Tips",
                description = "Reduce waste by transforming your unused items into practical solutions with these tips.",
                imageType = "reduction"
            ),
            TipItem(
                title = "Recycling Tips",
                description = "Make your own produce bags to reduce single-use plastic waste.",
                imageType = "recycling"
            ),
            TipItem(
                title = "Composting Tips",
                description = "Turn food scraps into nutrient-rich compost for your garden.",
                imageType = "composting"
            ),
            TipItem(
                title = "Reusing and Upcycling Tips",
                description = "Upcycling tips that turn the ordinary into the extraordinary.",
                imageType = "upcycling"
            ),
            TipItem(
                title = "Waste Management Awareness Tips",
                description = "Tips about awareness and ideas with regards to waste management process",
                imageType = "awareness"
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Surface(
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = EcoTextDark
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Tips",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = EcoTextDark
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2E8F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2, size.height / 2)
                            drawCircle(color = Color(0xFF22543D), radius = size.width * 0.45f, center = center)
                            drawCircle(color = Color(0xFF38A169), radius = size.width * 0.32f, center = Offset(center.x - 10, center.y + 10))
                            drawCircle(color = Color(0xFFF6AD55), radius = size.width * 0.12f, center = Offset(center.x + 18, center.y - 12))
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Helpful tips, courtesy of",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF16A34A)
                        )

                        Text(
                            text = "Dr. Adam Smith, Ph.D.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = EcoTextDark
                        )

                        Text(
                            text = "Environmental Engineer & Sustainability Consultant",
                            fontSize = 10.sp,
                            color = EcoTextMuted,
                            lineHeight = 14.sp
                        )

                        Text(
                            text = "Adam.Smith@gmail.com",
                            fontSize = 11.sp,
                            color = Color(0xFF16A34A),
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable {
                                Toast.makeText(context, "Emailing Dr. Adam Smith...", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            items(tipsList) { tip ->
                Card(
                    onClick = {
                        Toast.makeText(context, "Opening ${tip.title} guide...", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EcoCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TipThumbnailBox(
                            imageType = tip.imageType,
                            modifier = Modifier
                                .width(94.dp)
                                .height(68.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = tip.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = EcoTextDark
                            )

                            Text(
                                text = tip.description,
                                fontSize = 10.sp,
                                color = EcoTextMuted,
                                lineHeight = 14.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        Toast.makeText(context, "Loading additional tips...", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 30.dp, vertical = 8.dp)
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4E6E5D))
                ) {
                    Text(
                        text = "Load More",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun TipThumbnailBox(
    imageType: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, iconVector) = when (imageType) {
        "reduction" -> Pair(Color(0xFF52796F), Icons.Outlined.Autorenew)
        "recycling" -> Pair(Color(0xFF84A98C), Icons.Default.Recycling)
        "composting" -> Pair(Color(0xFF354F52), Icons.Outlined.Park)
        "upcycling" -> Pair(Color(0xFF2F3E46), Icons.Outlined.Build)
        else -> Pair(Color(0xFF6B8A7A), Icons.Outlined.Lightbulb)
    }

    Box(
        modifier = modifier.background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = Color.White.copy(alpha = 0.15f), radius = size.width * 0.4f)
        }
        Icon(
            imageVector = iconVector,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
    }
}

// -----------------------------------------------------------------------------
// 9. PROFILE TAB CONTENT
// -----------------------------------------------------------------------------
@Composable
fun ProfileScreenContent(
    onBack: () -> Unit = {},
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showEditAccountDialog by remember { mutableStateOf(false) }
    var showEditLocationDialog by remember { mutableStateOf(false) }
    var isSavingProfile by remember { mutableStateOf(false) }
    var profileRevision by remember { mutableIntStateOf(0) }
    val profilePreferences = remember {
        context.getSharedPreferences("citycare_profile_preferences", android.content.Context.MODE_PRIVATE)
    }
    var showNotificationPreferences by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var supportDialogTitle by remember { mutableStateOf<String?>(null) }
    var isChangingPassword by remember { mutableStateOf(false) }
    var currentPasswordInput by remember { mutableStateOf("") }
    var newPasswordInput by remember { mutableStateOf("") }
    var confirmNewPasswordInput by remember { mutableStateOf("") }
    var collectionNotificationsEnabled by remember {
        mutableStateOf(profilePreferences.getBoolean("collection_notifications", true))
    }
    var reportNotificationsEnabled by remember {
        mutableStateOf(profilePreferences.getBoolean("report_notifications", true))
    }
    var editName by remember { mutableStateOf(CityCareApiService.currentUserName) }
    var editEmail by remember { mutableStateOf(CityCareApiService.currentUserEmail) }
    var editPhone by remember { mutableStateOf(CityCareApiService.currentUserPhone) }
    var editBarangay by remember { mutableStateOf(CityCareApiService.currentUserBarangay) }
    var editPurok by remember { mutableStateOf(CityCareApiService.currentUserPurok) }
    profileRevision

    var profileBitmap by remember { mutableStateOf(CityCareApiService.loadCurrentProfilePhoto(context)) }
    var coverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showProfilePicker by remember { mutableStateOf(false) }
    var showCoverPicker by remember { mutableStateOf(false) }

    fun saveProfilePhoto(bitmap: Bitmap?) {
        coroutineScope.launch {
            isSavingProfile = true
            val result = CityCareApiService.updateProfilePhoto(context, bitmap)
            isSavingProfile = false
            Toast.makeText(context, result.second, Toast.LENGTH_LONG).show()
            if (result.first) {
                profileBitmap = bitmap
                profileRevision++
            }
        }
    }

    val profileGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val bitmap = loadBitmapFromUri(context, uri)
            if (bitmap == null) {
                Toast.makeText(context, "Could not open that image. Please try another photo.", Toast.LENGTH_LONG).show()
            } else {
                saveProfilePhoto(bitmap)
            }
        }
    }

    val profileCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            saveProfilePhoto(bitmap)
        }
    }

    val coverGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coverBitmap = loadBitmapFromUri(context, uri)
        }
    }

    val coverCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            coverBitmap = bitmap
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))

            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = EcoPrimaryGreen
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "User Profile",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = EcoPrimaryGreen
                    )
                }

            }
        }

        // Profile Main Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, EcoCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Cover Banner Image Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .background(Color(0xFFE2E8F0))
                            .clickable { showCoverPicker = true }
                    ) {
                        if (coverBitmap != null) {
                            Image(
                                bitmap = coverBitmap!!.asImageBitmap(),
                                contentDescription = "Cover Image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height
                                drawRect(color = Color(0xFFE8E5DA))
                                drawCircle(color = Color(0xFF90A997).copy(alpha = 0.85f), radius = h * 0.95f, center = Offset(w * 0.25f, h * 0.5f))
                                drawCircle(color = Color(0xFFB3C5B7).copy(alpha = 0.65f), radius = h * 0.75f, center = Offset(w * 0.35f, h * 0.3f))
                                drawCircle(color = Color(0xFFD3DEC8).copy(alpha = 0.75f), radius = h * 0.85f, center = Offset(w * 0.85f, h * 0.6f))
                                drawCircle(color = Color(0xFFE1E8D5).copy(alpha = 0.55f), radius = h * 1.15f, center = Offset(w * 0.7f, h * 0.8f))
                            }
                        }

                        Surface(
                            onClick = { showCoverPicker = true },
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier
                                .padding(10.dp)
                                .align(Alignment.BottomEnd)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Change Cover",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (coverBitmap != null) "Edit Cover" else "Add Cover",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Surface(
                            onClick = onBack,
                            shape = CircleShape,
                            color = Color.White,
                            shadowElevation = 3.dp,
                            modifier = Modifier
                                .padding(12.dp)
                                .size(32.dp)
                                .align(Alignment.TopStart)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = EcoTextDark,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Surface(
                            onClick = { Toast.makeText(context, "Profile link copied!", Toast.LENGTH_SHORT).show() },
                            shape = CircleShape,
                            color = Color.White,
                            shadowElevation = 3.dp,
                            modifier = Modifier
                                .padding(12.dp)
                                .size(32.dp)
                                .align(Alignment.TopEnd)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share Profile",
                                    tint = EcoTextDark,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Overlapping Avatar with Camera Badge
                    Box(
                        modifier = Modifier
                            .offset(y = (-40).dp)
                            .size(88.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(EcoPrimaryGreen)
                                .border(3.dp, Color.White, CircleShape)
                                .align(Alignment.Center)
                                .clickable { showProfilePicker = true },
                            contentAlignment = Alignment.Center
                        ) {
                            if (profileBitmap != null) {
                                Image(
                                    bitmap = profileBitmap!!.asImageBitmap(),
                                    contentDescription = "Profile Photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = CityCareApiService.currentUserName.take(1).uppercase().ifEmpty { "U" },
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(EcoPrimaryGreen)
                                .border(2.dp, Color.White, CircleShape)
                                .align(Alignment.BottomEnd)
                                .clickable { showProfilePicker = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = "Edit Profile Photo",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .offset(y = (-30).dp)
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = CityCareApiService.currentUserName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = EcoTextDark
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Email, contentDescription = null, tint = EcoTextMuted, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = CityCareApiService.currentUserEmail, fontSize = 12.sp, color = EcoTextMuted)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = EcoTextMuted, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = CityCareApiService.currentUserPhone, fontSize = 12.sp, color = EcoTextMuted)
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Surface(
                            color = Color(0xFFDCFCE7),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = EcoPrimaryGreen, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = CityCareApiService.currentUserLocation, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EcoPrimaryGreen)
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ACCOUNT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EcoTextMuted)

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EcoCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        ProfileMenuRow(
                            icon = Icons.Outlined.Edit,
                            title = "Edit Account Details",
                            onClick = {
                                editName = CityCareApiService.currentUserName
                                editEmail = CityCareApiService.currentUserEmail
                                editPhone = CityCareApiService.currentUserPhone
                                showEditAccountDialog = true
                            }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        ProfileMenuRow(
                            icon = Icons.Outlined.Map,
                            title = "Update Default Barangay / Purok",
                            onClick = {
                                editBarangay = CityCareApiService.currentUserBarangay
                                editPurok = CityCareApiService.currentUserPurok
                                showEditLocationDialog = true
                            }
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PREFERENCES & SECURITY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EcoTextMuted)

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EcoCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        ProfileMenuRow(
                            icon = Icons.Outlined.Notifications,
                            title = "Push Notification Preferences",
                            onClick = { showNotificationPreferences = true }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        ProfileMenuRow(
                            icon = Icons.Outlined.Lock,
                            title = "Change Password",
                            onClick = {
                                currentPasswordInput = ""
                                newPasswordInput = ""
                                confirmNewPasswordInput = ""
                                showChangePasswordDialog = true
                            }
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SUPPORT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EcoTextMuted)

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EcoCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        ProfileMenuRow(
                            icon = Icons.Outlined.Description,
                            title = "Terms of Service & Privacy Policy",
                            onClick = { supportDialogTitle = "Terms of Service & Privacy Policy" }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        ProfileMenuRow(
                            icon = Icons.Outlined.HelpOutline,
                            title = "Help & FAQ",
                            onClick = { supportDialogTitle = "Help & FAQ" }
                        )
                    }
                }
            }
        }

        item {
            TextButton(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = Color(0xFFDC2626))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Logout", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showNotificationPreferences) {
        AlertDialog(
            onDismissRequest = { showNotificationPreferences = false },
            title = { Text("Push Notification Preferences", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Choose which CityCare updates you want enabled on this device.",
                        fontSize = 13.sp,
                        color = EcoTextMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Collection reminders", modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Switch(
                            checked = collectionNotificationsEnabled,
                            onCheckedChange = {
                                collectionNotificationsEnabled = it
                                profilePreferences.edit().putBoolean("collection_notifications", it).apply()
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Report status updates", modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Switch(
                            checked = reportNotificationsEnabled,
                            onCheckedChange = {
                                reportNotificationsEnabled = it
                                profilePreferences.edit().putBoolean("report_notifications", it).apply()
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotificationPreferences = false }) {
                    Text("Done")
                }
            }
        )
    }

    if (showChangePasswordDialog) {
        AlertDialog(
            onDismissRequest = { if (!isChangingPassword) showChangePasswordDialog = false },
            title = { Text("Change Password", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = currentPasswordInput,
                        onValueChange = { currentPasswordInput = it },
                        label = { Text("Current password") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = { newPasswordInput = it },
                        label = { Text("New password (at least 6 characters)") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = confirmNewPasswordInput,
                        onValueChange = { confirmNewPasswordInput = it },
                        label = { Text("Confirm new password") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !isChangingPassword,
                    onClick = {
                        when {
                            currentPasswordInput.isBlank() -> Toast.makeText(context, "Enter your current password.", Toast.LENGTH_SHORT).show()
                            newPasswordInput.length < 6 -> Toast.makeText(context, "New password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                            newPasswordInput != confirmNewPasswordInput -> Toast.makeText(context, "New passwords do not match.", Toast.LENGTH_SHORT).show()
                            else -> coroutineScope.launch {
                                isChangingPassword = true
                                val result = CityCareApiService.changeUserPassword(currentPasswordInput, newPasswordInput)
                                isChangingPassword = false
                                Toast.makeText(context, result.second, Toast.LENGTH_LONG).show()
                                if (result.first) showChangePasswordDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EcoPrimaryGreen)
                ) {
                    Text(if (isChangingPassword) "Saving..." else "Change")
                }
            },
            dismissButton = {
                TextButton(enabled = !isChangingPassword, onClick = { showChangePasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    supportDialogTitle?.let { title ->
        AlertDialog(
            onDismissRequest = { supportDialogTitle = null },
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (title == "Help & FAQ") {
                        "To report uncollected waste, use the camera button and submit the location and details. Open My Reports to follow the status. For account support, contact your barangay help desk."
                    } else {
                        "CityCare uses your account details and chosen location to manage waste reports and service updates. Keep your account information accurate, protect your password, and only submit information needed to resolve a service issue."
                    },
                    fontSize = 14.sp,
                    color = EcoTextDark
                )
            },
            confirmButton = {
                TextButton(onClick = { supportDialogTitle = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (showEditAccountDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSavingProfile) showEditAccountDialog = false },
            title = { Text("Edit Account Details", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Full name") }, singleLine = true)
                    OutlinedTextField(value = editEmail, onValueChange = { editEmail = it }, label = { Text("Email") }, singleLine = true)
                    OutlinedTextField(value = editPhone, onValueChange = { editPhone = it }, label = { Text("Phone number") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSavingProfile,
                    onClick = {
                        coroutineScope.launch {
                            isSavingProfile = true
                            val result = CityCareApiService.updateUserProfile(
                                context = context,
                                name = editName,
                                email = editEmail,
                                phone = editPhone,
                                barangay = CityCareApiService.currentUserBarangay,
                                purok = CityCareApiService.currentUserPurok
                            )
                            isSavingProfile = false
                            Toast.makeText(context, result.second, Toast.LENGTH_LONG).show()
                            if (result.first) {
                                profileRevision++
                                showEditAccountDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EcoPrimaryGreen)
                ) {
                    Text(if (isSavingProfile) "Saving..." else "Save")
                }
            },
            dismissButton = {
                TextButton(enabled = !isSavingProfile, onClick = { showEditAccountDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditLocationDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSavingProfile) showEditLocationDialog = false },
            title = { Text("Update Default Location", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = editBarangay, onValueChange = { editBarangay = it }, label = { Text("Barangay") }, singleLine = true)
                    OutlinedTextField(value = editPurok, onValueChange = { editPurok = it }, label = { Text("Purok / Zone") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSavingProfile,
                    onClick = {
                        coroutineScope.launch {
                            isSavingProfile = true
                            val result = CityCareApiService.updateUserProfile(
                                context = context,
                                name = CityCareApiService.currentUserName,
                                email = CityCareApiService.currentUserEmail,
                                phone = CityCareApiService.currentUserPhone,
                                barangay = editBarangay,
                                purok = editPurok
                            )
                            isSavingProfile = false
                            Toast.makeText(context, result.second, Toast.LENGTH_LONG).show()
                            if (result.first) {
                                profileRevision++
                                showEditLocationDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EcoPrimaryGreen)
                ) {
                    Text(if (isSavingProfile) "Saving..." else "Save")
                }
            },
            dismissButton = {
                TextButton(enabled = !isSavingProfile, onClick = { showEditLocationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showProfilePicker) {
        AlertDialog(
            onDismissRequest = { showProfilePicker = false },
            title = { Text("Profile Picture", fontWeight = FontWeight.Bold) },
            text = { Text("Choose a new photo or remove your current profile picture.") },
            confirmButton = {
                Button(
                    enabled = !isSavingProfile,
                    onClick = {
                        showProfilePicker = false
                        profileGalleryLauncher.launch("image/*")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EcoPrimaryGreen)
                ) {
                    Text(if (isSavingProfile) "Saving..." else "Choose from Gallery", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        enabled = !isSavingProfile,
                        onClick = {
                            showProfilePicker = false
                            profileCameraLauncher.launch(null)
                        }
                    ) {
                        Text("Camera")
                    }
                    if (profileBitmap != null) {
                        TextButton(
                            enabled = !isSavingProfile,
                            onClick = {
                                showProfilePicker = false
                                saveProfilePhoto(null)
                            }
                        ) {
                            Text("Remove", color = Color(0xFFDC2626))
                        }
                    }
                }
            }
        )
    }

    if (showCoverPicker) {
        AlertDialog(
            onDismissRequest = { showCoverPicker = false },
            title = { Text("Update Background Cover", fontWeight = FontWeight.Bold) },
            text = { Text("Choose how you would like to update your profile background photo.") },
            confirmButton = {
                Button(
                    onClick = {
                        showCoverPicker = false
                        coverGalleryLauncher.launch("image/*")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EcoPrimaryGreen)
                ) {
                    Text("Choose from Gallery", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showCoverPicker = false
                        coverCameraLauncher.launch(null)
                    }
                ) {
                    Text("Take Photo")
                }
            }
        )
    }
}

// -----------------------------------------------------------------------------
// HELPER COMPOSABLES
// -----------------------------------------------------------------------------
@Composable
fun NavTabItemComposable(
    label: String,
    activeIcon: ImageVector,
    inactiveIcon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        color = if (isSelected) EcoHomeTabYellow else Color.Transparent,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = if (isSelected) activeIcon else inactiveIcon,
                contentDescription = label,
                tint = EcoTextDark,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (isSelected) EcoTextDark else EcoTextMuted,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center
            )
        }
    }
}

fun loadBitmapFromUri(context: android.content.Context, uri: android.net.Uri): Bitmap? {
    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
            android.graphics.ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun StatusBadgeChip(status: String) {
    val normalizedStatus = status.uppercase().replace('-', '_').replace(' ', '_')
    val (bgColor, textColor) = when (normalizedStatus) {
        "PENDING" -> Pair(Color(0xFFFEF08A), Color(0xFF854D0E))
        "ASSIGNED" -> Pair(Color(0xFFDBEAFE), Color(0xFF1E40AF))
        "IN_PROGRESS" -> Pair(Color(0xFFE0F2FE), Color(0xFF0369A1))
        "RESOLVED", "DONE" -> Pair(Color(0xFFDCFCE7), Color(0xFF166534))
        "REJECTED" -> Pair(Color(0xFFFEE2E2), Color(0xFFB91C1C))
        else -> Pair(Color(0xFFF1F5F9), Color(0xFF475569))
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = normalizedStatus.replace('_', ' '),
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) EcoPrimaryGreen else Color.White,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (selected) EcoPrimaryGreen else Color(0xFFCBD5E1)
        )
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else EcoTextDark,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

fun parseGpsCoordinates(gpsStr: String): Pair<Double, Double> {
    try {
        val regex = Regex("""([+-]?\d+\.?\d*)[^\d]*([NSns])?,?\s*([+-]?\d+\.?\d*)[^\d]*([EWew])?""")
        val match = regex.find(gpsStr)
        if (match != null) {
            var lat = match.groupValues[1].toDoubleOrNull() ?: 8.4822
            val latDir = match.groupValues[2].uppercase()
            if (latDir == "S") lat = -Math.abs(lat)

            var lng = match.groupValues[3].toDoubleOrNull() ?: 124.6175
            val lngDir = match.groupValues[4].uppercase()
            if (lngDir == "W") lng = -Math.abs(lng)

            return Pair(lat, lng)
        }
    } catch (e: Exception) {
        // Fallback to Cagayan de Oro Center
    }
    return Pair(8.4822, 124.6175)
}

@Composable
fun MapPreviewBox(
    heightDp: Int,
    locationName: String = "Carmen, Cagayan de Oro",
    gpsCoordinates: String = "8.4822° N, 124.6175° E",
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val (lat, lng) = remember(gpsCoordinates) { parseGpsCoordinates(gpsCoordinates) }
    val position = remember(lat, lng) { LatLng(lat, lng) }
    val cameraPositionState = rememberCameraPositionState {
        this.position = CameraPosition.fromLatLngZoom(position, 16f)
    }

    LaunchedEffect(position) {
        cameraPositionState.position = CameraPosition.fromLatLngZoom(position, 16f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFEBF3FF))
    ) {
        // Embedded Live Interactive Google Map showing actual location
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = false),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                scrollGesturesEnabled = false,
                zoomGesturesEnabled = false,
                tiltGesturesEnabled = false,
                rotationGesturesEnabled = false
            )
        ) {
            Marker(
                state = MarkerState(position = position),
                title = locationName,
                snippet = gpsCoordinates
            )
        }

        // Overlay Box to make the map card clickable to open full Google Maps app view
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    if (onClick != null) {
                        onClick()
                    } else {
                        try {
                            val encodedLabel = Uri.encode(locationName)
                            val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($encodedLabel)&z=18")
                            val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                                setPackage("com.google.android.apps.maps")
                            }
                            if (mapIntent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(mapIntent)
                            } else {
                                val browserUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
                                val browserIntent = Intent(Intent.ACTION_VIEW, browserUri)
                                context.startActivity(browserIntent)
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Location: $locationName ($gpsCoordinates)", Toast.LENGTH_LONG).show()
                        }
                    }
                }
        )

        Surface(
            color = Color.White.copy(alpha = 0.92f),
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 2.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = EcoPrimaryGreen,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Tap for Full View",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = EcoPrimaryGreen
                )
            }
        }
    }
}

@Composable
fun ReportImageThumbnail(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(Color(0xFFE2E8F0)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFCBD5E1)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun TimelineStepItem(
    stepNumber: Int,
    title: String,
    subtitle: String,
    isCompleted: Boolean,
    isCurrent: Boolean,
    isLast: Boolean = false,
    extraContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isCompleted -> EcoPrimaryGreen
                            isCurrent -> Color(0xFF0284C7)
                            else -> Color(0xFFE2E8F0)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                } else if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(38.dp)
                        .background(if (isCompleted) EcoPrimaryGreen else Color(0xFFE2E8F0))
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (isCompleted || isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCompleted || isCurrent) EcoTextDark else EcoTextMuted
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = EcoTextMuted
                )
            }
            extraContent?.invoke()
        }
    }
}

@Composable
fun NotificationCardItem(
    title: String,
    time: String,
    icon: ImageVector,
    iconBg: Color,
    iconColor: Color,
    borderColor: Color
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (borderColor != Color.Transparent) borderColor else EcoCardBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = EcoTextDark,
                    lineHeight = 16.sp
                )
                Text(
                    text = time,
                    fontSize = 10.sp,
                    color = EcoTextMuted
                )
            }
        }
    }
}

@Composable
fun ProfileMenuRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEFF6FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = EcoPrimaryGreen,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = EcoTextDark
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = EcoTextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun CityCareDashboardScreenPreview() {
    MaterialTheme {
        CityCareDashboardScreen()
    }
}
