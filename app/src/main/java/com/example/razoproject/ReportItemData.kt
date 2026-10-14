package com.example.razoproject

import android.graphics.Bitmap

data class ReportItemData(
    val id: String,
    val title: String,
    val location: String,
    val status: String,
    val time: String,
    val unitAssigned: String,
    val photoBitmap: Bitmap? = null,
    val gpsCoordinates: String? = null,
    val isCloudSynced: Boolean = true
)
