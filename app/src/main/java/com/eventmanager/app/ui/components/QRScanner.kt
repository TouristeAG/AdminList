package com.eventmanager.app.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Camera
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.eventmanager.app.data.models.Volunteer
import com.eventmanager.app.ui.utils.*
import com.eventmanager.app.R
import kotlinx.coroutines.delay

data class QRCodeData(
    val type: String,
    val version: Int,
    val id: String,
    val sheetsId: String?,
    val name: String,
    val abbr: String?
)

/**
 * Check if this is an NVIDIA Shield tablet
 */
fun isNvidiaShieldTablet(): Boolean {
    val manufacturer = android.os.Build.MANUFACTURER.lowercase()
    val model = android.os.Build.MODEL.lowercase()
    val device = android.os.Build.DEVICE.lowercase()
    
    return manufacturer.contains("nvidia") && 
           (model.contains("shield") || device.contains("shield"))
}

/**
 * Get device-specific camera initialization delay
 * NVIDIA Shield tablets may need more time to initialize
 */
fun getCameraInitializationDelay(): Long {
    return if (isNvidiaShieldTablet()) {
        1000L // 1 second for NVIDIA Shield
    } else {
        500L  // 500ms for other devices
    }
}

/**
 * Check if camera hardware is available using modern CameraManager API
 * This is much more reliable than the old Camera.open() approach
 */
fun isCameraAvailable(context: Context): Boolean {
    return try {
        println("🔍 Testing camera availability using CameraManager...")
        
        // First check if camera hardware exists
        val packageManager = context.packageManager
        val hasCamera = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
        println("📱 Camera hardware feature available: $hasCamera")
        
        if (!hasCamera) {
            println("❌ No camera hardware detected")
            return false
        }
        
        // Use modern CameraManager API (Android 5.0+) for reliable checking
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            if (cameraManager != null) {
                val cameraIds = cameraManager.cameraIdList
                println("📸 Found ${cameraIds.size} camera(s)")
                
                if (cameraIds.isNotEmpty()) {
                    // Check if at least one camera is available
                    for (cameraId in cameraIds) {
                        try {
                            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                            println("✅ Camera $cameraId available and accessible")
                            return true
                        } catch (e: Exception) {
                            println("⚠️ Camera $cameraId not accessible: ${e.message}")
                        }
                    }
                }
            } else {
                println("⚠️ CameraManager not available, falling back to legacy check")
                return hasCamera
            }
        } else {
            // Fallback for older devices - just rely on PackageManager check
            println("⚠️ Using PackageManager check (legacy Android version)")
            return hasCamera
        }
        
        false
    } catch (e: Exception) {
        println("⚠️ Camera availability check failed: ${e.message}")
        // On error, assume camera might be available - let the view handle it
        true
    }
}

/**
 * Try alternative camera initialization for older devices
 * Deprecated: This is no longer needed with modern CameraManager approach
 */
fun tryAlternativeCameraInitialization(context: Context): Boolean {
    // With modern CameraManager, this is now just a redundant check
    return isCameraAvailable(context)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerDialog(
    onDismiss: () -> Unit,
    onVolunteerFound: (Volunteer) -> Unit,
    volunteers: List<Volunteer>
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    var cameraAvailable by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showManualInput by remember { mutableStateOf(false) }
    
    // Check camera permission
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (!isGranted) {
            errorMessage = context.getString(R.string.camera_permission_required)
        } else {
            // Check camera availability after permission is granted
            cameraAvailable = isCameraAvailable(context)
            if (!cameraAvailable) {
                println("🔍 Standard camera check failed, trying alternative method...")
                cameraAvailable = tryAlternativeCameraInitialization(context)
                if (!cameraAvailable) {
                    errorMessage = context.getString(R.string.camera_not_available)
                }
            }
        }
    }
    
    // Check permission and camera availability on first load
    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        
        if (hasPermission) {
            cameraAvailable = isCameraAvailable(context)
            if (!cameraAvailable) {
                println("🔍 Standard camera check failed, trying alternative method...")
                cameraAvailable = tryAlternativeCameraInitialization(context)
                if (!cameraAvailable) {
                    errorMessage = context.getString(R.string.camera_not_available)
                }
            }
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Scan QR Code",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (hasPermission && cameraAvailable) {
                    QRScannerView(
                        onQRCodeScanned = { qrData ->
                            try {
                                // Debug logging
                                println("🔍 QR Code scanned - ID: '${qrData.id}', Name: '${qrData.name}'")
                                println("🔍 Available volunteers (${volunteers.size} total):")
                                volunteers.forEach { v ->
                                    println("  - ID: ${v.id} (${v.id.toString()}), Name: ${v.name}, Active: ${v.isActive}")
                                }
                                
                                // Find volunteer by ID - try multiple approaches
                                val volunteer = volunteers.find { volunteer ->
                                    val volunteerIdStr = volunteer.id.toString()
                                    val qrIdStr = qrData.id
                                    println("🔍 Comparing: volunteer ID '$volunteerIdStr' with QR ID '$qrIdStr'")
                                    
                                    // Check for ID 0 issue
                                    if (volunteer.id == 0L) {
                                        println("⚠️ Warning: Volunteer '${volunteer.name}' has ID 0 - this might be a sync issue")
                                    }
                                    
                                    volunteerIdStr == qrIdStr
                                }
                                
                                if (volunteer != null) {
                                    println("✅ Found volunteer: ${volunteer.name}")
                                    onVolunteerFound(volunteer)
                                    onDismiss()
                                } else {
                                    println("❌ Volunteer not found for ID: '${qrData.id}'")
                                    println("❌ Available volunteer IDs: ${volunteers.map { it.id.toString() }}")
                                    
                                    // Try fallback matching by name
                                    val volunteerByName = volunteers.find { it.name.equals(qrData.name, ignoreCase = true) }
                                    if (volunteerByName != null) {
                                        println("✅ Found volunteer by name fallback: ${volunteerByName.name}")
                                        onVolunteerFound(volunteerByName)
                                        onDismiss()
                                    } else {
                                        println("❌ No volunteer found with name '${qrData.name}' either")
                                        errorMessage = context.getString(R.string.volunteer_not_found, qrData.name, qrData.id)
                                    }
                                }
                            } catch (e: Exception) {
                                println("❌ Error processing QR code: ${e.message}")
                                errorMessage = context.getString(R.string.error_processing_qr_code, e.message ?: "")
                            }
                        },
                        onError = { message ->
                            errorMessage = message
                        }
                    )
                } else {
                // Permission denied state (polished)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = context.getString(R.string.camera_permission_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "We need access to your camera to scan QR codes.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                Text("Grant permission")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = { showManualInput = true }) {
                                Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(context.getString(R.string.enter_id_manually))
                            }
                        }
                    }
                }
                }
                
                // Error message
                errorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
    
    // Manual input dialog
    if (showManualInput) {
        ManualVolunteerInputDialog(
            onDismiss = { showManualInput = false },
            onVolunteerFound = { volunteer ->
                onVolunteerFound(volunteer)
                onDismiss()
            },
            volunteers = volunteers
        )
    }
}

@Composable
fun QRScannerView(
    onQRCodeScanned: (QRCodeData) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    var barcodeView by remember { mutableStateOf<DecoratedBarcodeView?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var cameraInitialized by remember { mutableStateOf(false) }
    val scanBoxSize = 240.dp
    val containerHeight = 320.dp
    val overlayColor = Color.Black.copy(alpha = 0.5f)
    
    // Add a delay for camera initialization on older devices
    LaunchedEffect(Unit) {
        val delay = getCameraInitializationDelay()
        println("⏱️ Camera initialization delay: ${delay}ms for ${if (isNvidiaShieldTablet()) "NVIDIA Shield" else "other device"}")
        kotlinx.coroutines.delay(delay) // Give camera time to initialize
        cameraInitialized = true
    }
    
    val callback = object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult) {
            try {
                val qrData = parseQRCodeData(result.text)
                onQRCodeScanned(qrData)
            } catch (e: Exception) {
                onError(context.getString(R.string.invalid_qr_code_format, e.message ?: ""))
            }
        }
        
        override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>?) {
            // Optional: Handle possible result points for UI feedback
        }
    }
    
    // Background camera preview with styled overlay
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(containerHeight)
            .clip(RoundedCornerShape(12.dp))
    ) {
        if (cameraInitialized) {
            AndroidView(
            factory = { ctx ->
                try {
                    println("🔍 Initializing DecoratedBarcodeView...")
                    if (isNvidiaShieldTablet()) {
                        println("🛡️ Detected NVIDIA Shield tablet - using enhanced initialization")
                    }
                    
                    val barcodeView = DecoratedBarcodeView(ctx)
                    println("✅ DecoratedBarcodeView created")
                    
                    // Set up the barcode view
                    val formats = listOf(com.google.zxing.BarcodeFormat.QR_CODE)
                    val decoderFactory = com.journeyapps.barcodescanner.DefaultDecoderFactory(formats)
                    barcodeView.decoderFactory = decoderFactory
                    println("✅ Decoder factory set")
                    
                    // For NVIDIA Shield, try a more conservative approach with retries
                    if (isNvidiaShieldTablet()) {
                        println("🛡️ Applying NVIDIA Shield-specific camera settings...")
                        
                        // Try to resume the camera with error handling and retries
                        var retryCount = 0
                        val maxRetries = 3
                        var lastException: Exception? = null
                        
                        while (retryCount < maxRetries) {
                            try {
                                barcodeView.resume()
                                println("✅ NVIDIA Shield camera resumed successfully (attempt ${retryCount + 1})")
                                
                                // Start continuous decoding
                                barcodeView.decodeContinuous(callback)
                                println("✅ NVIDIA Shield continuous decoding started")
                                break // Success, exit retry loop
                            } catch (e: Exception) {
                                lastException = e
                                retryCount++
                                println("⚠️ NVIDIA Shield camera resume attempt $retryCount failed: ${e.message}")
                                
                                if (retryCount < maxRetries) {
                                    // Wait before retrying
                                    Thread.sleep(300)
                                    try {
                                        barcodeView.pause()
                                        Thread.sleep(200)
                                    } catch (e2: Exception) {
                                        println("⚠️ Error pausing before retry: ${e2.message}")
                                    }
                                }
                            }
                        }
                        
                        // If all retries failed, log but don't crash
                        if (retryCount >= maxRetries && lastException != null) {
                            println("❌ NVIDIA Shield camera failed after $maxRetries attempts: ${lastException.message}")
                            cameraError = "Camera service temporarily unavailable. Try again in a moment."
                        }
                    } else {
                        // Standard initialization for other devices with error handling
                        try {
                            barcodeView.resume()
                            println("✅ Camera resumed successfully")
                            barcodeView.decodeContinuous(callback)
                            println("✅ Continuous decoding started")
                        } catch (e: Exception) {
                            println("⚠️ Camera resume failed: ${e.message}")
                            cameraError = "Camera failed to initialize: ${e.message}"
                        }
                    }
                    
                    barcodeView
                } catch (e: Exception) {
                    println("❌ Error initializing camera: ${e.message}")
                    println("❌ Exception type: ${e.javaClass.simpleName}")
                    e.printStackTrace()
                    cameraError = "Failed to initialize camera: ${e.message}"
                    onError("Failed to initialize camera: ${e.message}")
                    // Return a placeholder view instead of null
                    DecoratedBarcodeView(ctx)
                }
            },
            modifier = Modifier.matchParentSize(),
            update = { view -> 
                try {
                    println("🔍 Updating camera view...")
                    view?.resume()
                    println("✅ Camera view updated successfully")
                } catch (e: Exception) {
                    println("❌ Error updating camera: ${e.message}")
                    cameraError = "Camera error: ${e.message}"
                    onError("Camera error: ${e.message}")
                }
            }
        )
        } else {
            // Show loading indicator while camera initializes
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Initializing camera...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Dimmed overlays around scan window
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((containerHeight - scanBoxSize) / 2)
                .align(Alignment.TopCenter)
                .background(overlayColor)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((containerHeight - scanBoxSize) / 2)
                .align(Alignment.BottomCenter)
                .background(overlayColor)
        )
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .height(scanBoxSize)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(overlayColor)
            )
            Box(
                modifier = Modifier
                    .width(scanBoxSize)
                    .fillMaxHeight()
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(overlayColor)
            )
        }

        // Scan window border and corners
        Box(
            modifier = Modifier
                .size(scanBoxSize)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(12.dp))
                .border(width = 2.dp, color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(12.dp))
        )

        // Corner accents
        val cornerLen = 20.dp
        val cornerThickness = 3.dp
        // Top-Left
        Box(modifier = Modifier.size(scanBoxSize).align(Alignment.Center)) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(cornerLen, cornerThickness)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(cornerThickness, cornerLen)
                    .background(MaterialTheme.colorScheme.primary)
            )
            // Top-Right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(cornerLen, cornerThickness)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(cornerThickness, cornerLen)
                    .background(MaterialTheme.colorScheme.primary)
            )
            // Bottom-Left
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(cornerLen, cornerThickness)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(cornerThickness, cornerLen)
                    .background(MaterialTheme.colorScheme.primary)
            )
            // Bottom-Right
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(cornerLen, cornerThickness)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(cornerThickness, cornerLen)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }

        // Animated scanning line
        val transition = rememberInfiniteTransition(label = "scanLine")
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "scanLineProgress"
        )
        Box(
            modifier = Modifier
                .size(scanBoxSize)
                .align(Alignment.Center)
        ) {
            val lineOffset = scanBoxSize * progress
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .offset(y = lineOffset.coerceAtMost(scanBoxSize - 2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }

    // Show error message if camera failed to initialize
    cameraError?.let { error ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Camera Error",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
    
    // Handle lifecycle events using DisposableEffect
    DisposableEffect(Unit) {
        onDispose {
            barcodeView?.pause()
        }
    }
}

fun parseQRCodeData(qrText: String): QRCodeData {
    return try {
        println("🔍 Parsing QR code text: '$qrText'")
        val gson = Gson()
        val jsonMap = gson.fromJson(qrText, Map::class.java) as Map<String, Any>
        println("🔍 Parsed JSON map: $jsonMap")
        
        val qrData = QRCodeData(
            type = jsonMap["type"] as? String ?: "",
            version = (jsonMap["version"] as? Double)?.toInt() ?: 1,
            id = when (val idValue = jsonMap["id"]) {
                is String -> idValue
                is Number -> idValue.toString()
                else -> ""
            },
            sheetsId = jsonMap["sheetsId"] as? String,
            name = jsonMap["name"] as? String ?: "",
            abbr = jsonMap["abbr"] as? String
        )
        println("🔍 Parsed QR data: $qrData")
        qrData
    } catch (e: JsonSyntaxException) {
        println("❌ JSON syntax error: ${e.message}")
        throw IllegalArgumentException("Invalid JSON format in QR code")
    } catch (e: Exception) {
        println("❌ Parsing error: ${e.message}")
        throw IllegalArgumentException("Failed to parse QR code data: ${e.message}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualVolunteerInputDialog(
    onDismiss: () -> Unit,
    onVolunteerFound: (Volunteer) -> Unit,
    volunteers: List<Volunteer>
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = context.getString(R.string.manual_volunteer_input),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = context.getString(R.string.enter_volunteer_id_manually),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { 
                        inputText = it
                        errorMessage = null
                    },
                    label = { Text(context.getString(R.string.volunteer_id)) },
                    placeholder = { Text("e.g., 12345") },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (volunteers.isNotEmpty()) {
                    Text(
                        text = context.getString(R.string.available_volunteers),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(volunteers.take(10)) { volunteer ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    inputText = volunteer.id.toString()
                                }
                            ) {
                                Text(
                                    text = "${volunteer.id} - ${volunteer.name}",
                                    modifier = Modifier.padding(8.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val volunteerId = inputText.trim().toLongOrNull()
                        if (volunteerId == null) {
                            errorMessage = "Please enter a valid number"
                            return@Button
                        }
                        
                        val volunteer = volunteers.find { it.id == volunteerId }
                        if (volunteer != null) {
                            onVolunteerFound(volunteer)
                            onDismiss()
                        } else {
                            errorMessage = "Volunteer with ID $volunteerId not found"
                        }
                    } catch (e: Exception) {
                        errorMessage = "Invalid input: ${e.message}"
                    }
                },
                enabled = inputText.isNotBlank()
            ) {
                Text(context.getString(R.string.find_volunteer))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
