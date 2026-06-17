package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.example.data.TranscriptionItem
import com.example.ui.components.CleanPulsatingSoundwaves
import com.example.ui.components.GeometricRobinBirdCanvas
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel
import com.example.speech.ModelDownloadManager

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkModeState by viewModel.isDarkMode.collectAsStateWithLifecycle()
            MyApplicationTheme(darkTheme = darkModeState) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RobinMainScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun RobinMainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(0) } // 0: STT, 1: TTS, 2: History/Settings
    var showSettings by remember { mutableStateOf(false) }
    
    val uiLanguage by viewModel.uiLanguage.collectAsStateWithLifecycle()
    val statusLabel by viewModel.statusLabel.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val liveTranscript by viewModel.liveTranscript.collectAsStateWithLifecycle()
    val matchError by viewModel.matchError.collectAsStateWithLifecycle()
    val progressBar by viewModel.progressBar.collectAsStateWithLifecycle()

    // File Pick launcher to import files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val filename = it.path?.substringAfterLast("/") ?: "audio_archivo.m4a"
                    viewModel.processImportedFile(stream, filename)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error cargando archivo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showSettings) {
        SettingsDialog(viewModel = viewModel, onDismiss = { showSettings = false })
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(if (currentTab == 0) Icons.Filled.Mic else Icons.Outlined.Mic, contentDescription = "Transcripción") },
                    label = { Text(RobinTranslations.get("stt_tab", uiLanguage), style = TextStyleWithSafeSize()) }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(if (currentTab == 1) Icons.Filled.VolumeUp else Icons.Outlined.VolumeUp, contentDescription = "Síntesis") },
                    label = { Text(RobinTranslations.get("tts_tab", uiLanguage), style = TextStyleWithSafeSize()) }
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(if (currentTab == 2) Icons.Filled.History else Icons.Outlined.History, contentDescription = "Historial") },
                    label = { Text(RobinTranslations.get("history_tab", uiLanguage), style = TextStyleWithSafeSize()) }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Elegant top branding Header
            RobinHeaderSection(
                viewModel = viewModel, 
                statusText = statusLabel,
                onSettingsClick = { showSettings = true }
            )

            AnimatedVisibility(
                visible = progressBar != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                progressBar?.let {
                    LinearProgressIndicator(
                        progress = { it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            matchError?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = "⚠ $it",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Tab shifting
            when (currentTab) {
                0 -> SpeechToTextTab(
                    viewModel = viewModel,
                    isListening = isListening,
                    liveTranscript = liveTranscript,
                    onImportClick = {
                        // Open picker search
                        filePickerLauncher.launch(arrayOf("*/*"))
                    }
                )
                1 -> TextToSpeechTab(viewModel = viewModel)
                2 -> ActivityAndSettingsTab(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun RobinHeaderSection(viewModel: MainViewModel, statusText: String, onSettingsClick: () -> Unit) {
    val darkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Premium app logo box customized with the newly uploaded branding
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .clickable {
                        Toast.makeText(context, "Robin Smart Voice Assistant", Toast.LENGTH_SHORT).show()
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "Robin App Logo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Robin",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Text(
                    text = "v2.3.1-debug",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        letterSpacing = 1.sp
                    )
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Display motor active tag
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(
                    text = statusText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Configuraciones",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun SpeechToTextTab(
    viewModel: MainViewModel,
    isListening: Boolean,
    liveTranscript: String,
    onImportClick: () -> Unit
) {
    val currentEngine by viewModel.chosenEngine.collectAsStateWithLifecycle()
    val currentLanguage by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val isVoskDownloaded by viewModel.isVoskDownloaded.collectAsStateWithLifecycle()
    val isWhisperDownloaded by viewModel.isWhisperDownloaded.collectAsStateWithLifecycle()
    val isPiperDownloaded by viewModel.isPiperDownloaded.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScrollAndCompactLayout(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Native Engines Status Card (Geometric Balance Theme Style)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFD4E8E0) // Soft Pastel Mint Green (#D4E8E0)
            ),
            shape = RoundedCornerShape(24.dp), // rounded-3xl
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Native Engines",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155), // slate-700
                            letterSpacing = (-0.3).sp
                        )
                    )
                    Surface(
                        color = Color.White.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Ready Offline",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF047857), // emerald-700
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Grid of 4 native processes (Vosk STT, Whisper.cpp, Piper TTS, Android TTS)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EngineListItem(name = "Vosk STT", downloaded = isVoskDownloaded, modifier = Modifier.weight(1f))
                        EngineListItem(name = "Whisper.cpp", downloaded = isWhisperDownloaded, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EngineListItem(name = "Piper TTS", downloaded = isPiperDownloaded, modifier = Modifier.weight(1f))
                        EngineListItem(name = "Android TTS", downloaded = true, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // 2. STT Engine configuration
        val uiLanguage by viewModel.uiLanguage.collectAsStateWithLifecycle()
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = RobinTranslations.get("choose_engine", uiLanguage),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val engines = listOf(
                        "android_stt" to (if (uiLanguage == "en") "Native" else "Nativo"),
                        "vosk" to "Vosk STT",
                        "whisper_cpp" to "Whisper CPP",
                        "whisper_api" to "Whisper API",
                        "gemini_cloud" to "Gemini Cloud"
                    )
                    engines.forEach { (key, label) ->
                        Button(
                            onClick = { viewModel.chosenEngine.value = key },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentEngine == key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (currentEngine == key) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(label, style = TextStyleWithSafeSize().copy(fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }

                // 2.5 Model Missing Banner warn context (Geometric theme aligned)
                if ((currentEngine == "vosk" && !isVoskDownloaded) || (currentEngine == "whisper_cpp" && !isWhisperDownloaded)) {
                    val missingModelId = if (currentEngine == "vosk") "vosk" else "whisper"
                    val missingModelName = if (currentEngine == "vosk") "Vosk" else "Whisper"
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Alerta",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = RobinTranslations.get("warn_missing_model", uiLanguage) + " ($missingModelName)",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                )
                                Text(
                                    text = RobinTranslations.get("warn_missing_desc", uiLanguage),
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
                                )
                            }
                            Button(
                                onClick = { viewModel.downloadModel(missingModelId) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(RobinTranslations.get("btn_down", uiLanguage), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiLanguage == "en") "Audio speech language:" else "Idioma local:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val langs = listOf("es" to "ES", "en" to "EN", "pt" to "PT")
                        langs.forEach { (code, label) ->
                            FilterChip(
                                selected = currentLanguage == code,
                                onClick = { viewModel.currentLanguage.value = code },
                                label = { Text(label, style = TextStyleWithSafeSize().copy(fontWeight = FontWeight.Bold)) }
                            )
                        }
                    }
                }
            }
        }

        // 3. Central Recording Sphere Centerpiece with Concentric Circles (Geometric Balance Theme)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            // Ambient outer rings
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .background(Color.Transparent)
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                // Ring 2 border-[#FF8F50]/10
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .padding(2.dp)
                        .align(Alignment.Center)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    ) {}
                }
                
                // Ring 1 border-[#FF8F50]/20
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .padding(2.dp)
                        .align(Alignment.Center)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent,
                        shape = CircleShape,
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {}
                }

                // Active big centerpiece button (120dp diameter)
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .align(Alignment.Center)
                        .clickable {
                            if (isListening) {
                                viewModel.stopRecording()
                            } else {
                                viewModel.startRecording()
                            }
                        }
                        .background(
                            color = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color.Transparent,
                        shape = CircleShape,
                        border = BorderStroke(4.dp, Color.White)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "Interacción Grabador",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                }
            }
        }

        // Subtitle indicators below button
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isListening) "Grabando... Toca para finalizar" else "Toca el micrófono para grabar",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Text(
                text = "Almacenamiento Local: 18.2 MB / 209 MB de Modelos",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // Animated soundwave
        CleanPulsatingSoundwaves(isPulsing = isListening)

        // 4. Input / Output text result container with subtle clip line clamp (latest result snippet style)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "LATEST RESULT • OFFLINE ENGINE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = liveTranscript.ifBlank { "Presiona grabar o ingresa archivos de audio para transcribir localmente." },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 20.sp,
                        color = if (liveTranscript.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }

        // 5. Secondary Quick-Action Row buttons (Upload file, copy, share, etc.)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Load file local trigger
            OutlinedButton(
                onClick = onImportClick,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = "Subir", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cargar Archivo", style = TextStyleWithSafeSize().copy(fontWeight = FontWeight.Bold))
            }

            if (liveTranscript.isNotBlank() && liveTranscript != "Listo para hablar..." && liveTranscript != "Escuchando...") {
                // Copy selection
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Robin Transcript", liveTranscript))
                        Toast.makeText(context, "Copiado al portapapeles", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copiar", style = TextStyleWithSafeSize().copy(fontWeight = FontWeight.Bold))
                }

                // Share element
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, liveTranscript)
                        }
                        context.startActivity(Intent.createChooser(intent, "Compartir transcripción"))
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Compartir", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Compartir", style = TextStyleWithSafeSize().copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@Composable
fun EngineListItem(name: String, downloaded: Boolean? = null, modifier: Modifier = Modifier) {
    val statusColor = when (downloaded) {
        true -> Color(0xFF10B981) // emerald-500
        false -> Color(0xFFEF4444) // red-500
        null -> Color(0xFF10B981)
    }
    val statusLabel = when (downloaded) {
        true -> " (Ok)"
        false -> " (Falta)"
        null -> ""
    }
    Surface(
        color = Color.White.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusColor, CircleShape)
            )
            Text(
                text = "$name$statusLabel",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B) // slate-800
            )
        }
    }
}

@Composable
fun TextToSpeechTab(viewModel: MainViewModel) {
    var textToSpeak by remember { mutableStateOf("") }
    val currentTtsEngine by viewModel.chosenTtsEngine.collectAsStateWithLifecycle()
    val currentLanguage by viewModel.currentLanguage.collectAsStateWithLifecycle()
    val speechRate by viewModel.currentTtsSpeechRate.collectAsStateWithLifecycle()
    val pitch by viewModel.currentTtsPitch.collectAsStateWithLifecycle()
    val isPiperDownloaded by viewModel.isPiperDownloaded.collectAsStateWithLifecycle()
    val uiLanguage by viewModel.uiLanguage.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScrollAndCompactLayout()
    ) {
        Text(
            text = RobinTranslations.get("title_tts", uiLanguage),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = RobinTranslations.get("desc_tts", uiLanguage),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // TTS Engine selection
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = RobinTranslations.get("choose_tts_engine", uiLanguage),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.chosenTtsEngine.value = "android" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentTtsEngine == "android") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (currentTtsEngine == "android") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Android TTS (Offline)", style = TextStyleWithSafeSize())
                    }

                    Button(
                        onClick = { viewModel.chosenTtsEngine.value = "piper" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentTtsEngine == "piper") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (currentTtsEngine == "piper") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Piper (ONNX Local)", style = TextStyleWithSafeSize())
                    }
                }
            }
        }

        if (currentTtsEngine == "piper" && !isPiperDownloaded) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Alerta",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = RobinTranslations.get("warn_missing_model", uiLanguage) + " (Piper)",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        )
                        Text(
                            text = RobinTranslations.get("warn_missing_desc", uiLanguage),
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
                        )
                    }
                    Button(
                        onClick = { viewModel.downloadModel("piper") },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(RobinTranslations.get("btn_down", uiLanguage), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Input Box
        OutlinedTextField(
            value = textToSpeak,
            onValueChange = { textToSpeak = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            placeholder = { 
                Text(
                    text = if (uiLanguage == "en") 
                        "Write custom text here... e.g., Hello Robin, this is synthesized locally and securely inside my device." 
                    else 
                        "Escribe el texto aquí... Ej: Hola Robin, esto se procesa de manera segura y confidencial en mi dispositivo."
                ) 
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Speech Tuning Parameters
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (uiLanguage == "en") "Prosody & Pitch Controls" else "Ajustes de Prosodia y Tono",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Rate slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(RobinTranslations.get("speech_rate", uiLanguage), fontSize = 13.sp)
                        Text(text = "%.1fx".format(speechRate), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = speechRate,
                        onValueChange = { viewModel.setSpeechRate(it) },
                        valueRange = 0.5f..2.0f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Pitch slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(RobinTranslations.get("pitch", uiLanguage), fontSize = 13.sp)
                        Text(text = "%.1fx".format(pitch), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = pitch,
                        onValueChange = { viewModel.setPitch(it) },
                        valueRange = 0.5f..1.5f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Trigger Button
        Button(
            onClick = { viewModel.speakText(textToSpeak) },
            enabled = textToSpeak.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Filled.VolumeUp, contentDescription = "Hablar")
            Spacer(modifier = Modifier.width(8.dp))
            Text(RobinTranslations.get("speak_btn", uiLanguage), style = TextStyleWithSafeSize())
        }
    }
}

@Composable
fun ActivityAndSettingsTab(viewModel: MainViewModel) {
    val history by viewModel.historyItems.collectAsStateWithLifecycle()
    val totalTrans by viewModel.totalTranscriptions.collectAsStateWithLifecycle()
    val totalSecs by viewModel.totalSecondsProcessed.collectAsStateWithLifecycle()
    val estimatedCost by viewModel.totalCloudCost.collectAsStateWithLifecycle()
    val uiLanguage by viewModel.uiLanguage.collectAsStateWithLifecycle()
    
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    val filteredHistory = remember(history, searchQuery) {
        if (searchQuery.isBlank()) {
            history
        } else {
            history.filter { it.text.contains(searchQuery, ignoreCase = true) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = RobinTranslations.get("history_title", uiLanguage),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = RobinTranslations.get("history_desc", uiLanguage),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // Stats Row widget
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(RobinTranslations.get("stats_trans", uiLanguage), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$totalTrans", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    }
                    Box(modifier = Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.surfaceVariant))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(RobinTranslations.get("stats_secs", uiLanguage), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${totalSecs}s", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    }
                    Box(modifier = Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.surfaceVariant))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(RobinTranslations.get("stats_cost", uiLanguage), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$%.4f".format(estimatedCost), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(RobinTranslations.get("search_hist", uiLanguage)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        if (filteredHistory.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = "Vacio",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = RobinTranslations.get("empty_hist", uiLanguage),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredHistory) { item ->
                TranscriptionItemRow(
                    item = item,
                    onFavoriteToggle = { viewModel.toggleFavorite(item) },
                    onDelete = { viewModel.deleteItem(item) },
                    onSpeak = { viewModel.speakText(item.text) }
                )
            }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        item {
            Button(
                onClick = { viewModel.clearAllHistory() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Limpiar Todo")
                Spacer(modifier = Modifier.width(8.dp))
                Text(RobinTranslations.get("clear_all", uiLanguage), style = TextStyleWithSafeSize())
            }
        }
    }
}

@Composable
fun TranscriptionItemRow(
    item: TranscriptionItem,
    onFavoriteToggle: () -> Unit,
    onDelete: () -> Unit,
    onSpeak: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (item.engine.contains("Nativo") || item.engine.contains("Offline")) Icons.Default.Lock else Icons.Default.Public,
                        contentDescription = "Modo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.engine,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row {
                    IconButton(onClick = onFavoriteToggle, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorito",
                            tint = if (item.isFavorite) Color(0xFFF9AB00) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onSpeak,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.VolumeUp, contentDescription = "Escuchar", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Repetir Voz", style = TextStyleWithSafeSize().copy(fontSize = 12.sp))
                    }
                }
            }
        }
    }
}

// Elegant Dialog representing configurations panel with bilingual preferences
@Composable
fun SettingsDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val uiLanguage by viewModel.uiLanguage.collectAsStateWithLifecycle()
    val darkModeState by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val whisperApiKey by viewModel.whisperApiKey.collectAsStateWithLifecycle()
    val whisperApiEndpoint by viewModel.whisperApiEndpoint.collectAsStateWithLifecycle()
    val ttsModelName by viewModel.ttsModelName.collectAsStateWithLifecycle()

    val isVoskDownloaded by viewModel.isVoskDownloaded.collectAsStateWithLifecycle()
    val isWhisperDownloaded by viewModel.isWhisperDownloaded.collectAsStateWithLifecycle()
    val isPiperDownloaded by viewModel.isPiperDownloaded.collectAsStateWithLifecycle()

    val progressMap by ModelDownloadManager.downloadProgressMap.collectAsStateWithLifecycle()
    val statusMap by ModelDownloadManager.downloadStatusMap.collectAsStateWithLifecycle()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configuraciones",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = RobinTranslations.get("settings_title", uiLanguage),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Text(
                    text = RobinTranslations.get("settings_sub", uiLanguage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                // Scrollable configs column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (uiLanguage == "en") "Appearance & Locale" else "Apariencia e Idioma",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    )

                    // Theme selector row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(if (darkModeState) Icons.Default.DarkMode else Icons.Default.LightMode, contentDescription = "Tema")
                            Text(
                                text = RobinTranslations.get("theme_label", uiLanguage),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Switch(
                            checked = darkModeState,
                            onCheckedChange = { viewModel.toggleDarkMode() }
                        )
                    }

                    // Language toggler
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Language, contentDescription = "Idioma")
                            Text(
                                text = RobinTranslations.get("lang_label", uiLanguage),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("es" to "Español", "en" to "English").forEach { (code, name) ->
                                Button(
                                    onClick = { viewModel.setUiLanguage(code) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (uiLanguage == code) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (uiLanguage == code) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    Text(
                        text = RobinTranslations.get("whisper_config", uiLanguage),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    )

                    OutlinedTextField(
                        value = whisperApiKey,
                        onValueChange = { viewModel.setWhisperApiKey(it) },
                        label = { Text("Whisper API Key") },
                        placeholder = { Text(RobinTranslations.get("whisper_key_placeholder", uiLanguage)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = whisperApiEndpoint,
                        onValueChange = { viewModel.setWhisperApiEndpoint(it) },
                        label = { Text("Whisper Endpoint URL") },
                        placeholder = { Text(RobinTranslations.get("whisper_endpoint_placeholder", uiLanguage)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = ttsModelName,
                        onValueChange = { viewModel.setTtsModelName(it) },
                        label = { Text("TTS Model Name Config") },
                        placeholder = { Text(RobinTranslations.get("tts_model_placeholder", uiLanguage)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    // STT Category Section
                    Text(
                        text = if (uiLanguage == "en") "Offline STT Models (Speech-to-Text)" else "Modelos STT Offline (Voz a Texto)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    )

                    val overlayModels = listOf(
                        Triple("vosk", "STT", Triple("Vosk STT Mini", "es-0.22 • 42 MB", isVoskDownloaded)),
                        Triple("whisper", "STT", Triple("Whisper GGML-Tiny", "es • 75 MB", isWhisperDownloaded)),
                        Triple("piper", "TTS", Triple("Piper TTS Neural", "es_ES • 48 MB", isPiperDownloaded))
                    )

                    overlayModels.filter { it.second == "STT" }.forEach { (id, _, details) ->
                        val (name, desc, state) = details
                        ModelDownloadRow(
                            id = id,
                            name = name,
                            description = desc,
                            isDownloaded = state,
                            progress = progressMap[id],
                            customStatus = statusMap[id],
                            viewModel = viewModel,
                            uiLanguage = uiLanguage
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // TTS Category Section
                    Text(
                        text = if (uiLanguage == "en") "Offline TTS Models (Text-to-Speech)" else "Modelos TTS Offline (Texto a Voz)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    )

                    overlayModels.filter { it.second == "TTS" }.forEach { (id, _, details) ->
                        val (name, desc, state) = details
                        ModelDownloadRow(
                            id = id,
                            name = name,
                            description = desc,
                            isDownloaded = state,
                            progress = progressMap[id],
                            customStatus = statusMap[id],
                            viewModel = viewModel,
                            uiLanguage = uiLanguage
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(bottom = 8.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (uiLanguage == "en") "Save & Apply" else "Guardar y Aplicar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ModelDownloadRow(
    id: String,
    name: String,
    description: String,
    isDownloaded: Boolean,
    progress: Float?,
    customStatus: String?,
    viewModel: MainViewModel,
    uiLanguage: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (progress != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (uiLanguage == "en") "Downloading..." else "Descargando...",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            } else {
                if (isDownloaded) {
                    Button(
                        onClick = { viewModel.deleteModel(id) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(RobinTranslations.get("btn_del", uiLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { viewModel.downloadModel(id) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(RobinTranslations.get("btn_down", uiLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        if (customStatus != null || isDownloaded) {
            Text(
                text = customStatus ?: (if (uiLanguage == "en") "Installed offline" else "Instalado offline"),
                fontSize = 11.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = if (isDownloaded) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            Text(
                text = if (uiLanguage == "en") "Not downloaded" else "No descargado",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// Bilingüal localization translations mapping
object RobinTranslations {
    fun get(key: String, lang: String): String {
        val strings = mapOf(
            "app_subtitle" to Pair("Robin Smart Assistant for voice text processing", "Asistente Inteligente Robin para procesamiento de voz y texto"),
            "stt_tab" to Pair("STT (Voice)", "STT (Voz)"),
            "tts_tab" to Pair("TTS (Text)", "TTS (Texto)"),
            "history_tab" to Pair("History", "Historial"),
            "title_stt" to Pair("Local STT Voice Processor", "Procesador de Voz STT Local"),
            "desc_stt" to Pair("Speak and let Robin transcribe offline or via custom API configurations.", "Habla y deja que Robin transcriba en modo offline o mediante APIs configuradas."),
            "choose_engine" to Pair("Select Voice Engine", "Selección de Motor de Voz"),
            "title_tts" to Pair("Text-to-Speech Synthesis", "Síntesis de Voz (Text-to-Speech)"),
            "desc_tts" to Pair("Write plain text to convert it into high-fidelity offline voice.", "Escribe texto plano para convertirlo en voz offline con alta calidad de entonación."),
            "choose_tts_engine" to Pair("Select TTS Engine", "Selección de Motor TTS"),
            "speech_rate" to Pair("Speed Rate", "Velocidad de Voz"),
            "pitch" to Pair("Vocal Pitch", "Tono de Voz"),
            "speak_btn" to Pair("Synthesize Speech", "Sintetizar Voz"),
            "history_title" to Pair("Processing History", "Historial de Procesamientos"),
            "history_desc" to Pair("Your audio recordings and processing logs are securely saved on local SQLite disk storage.", "Registros guardados de manera segura en la base de datos local SQLite del dispositivo."),
            "search_hist" to Pair("Search in history...", "Buscar en transcripciones..."),
            "empty_hist" to Pair("History is empty", "Historial vacío"),
            "clear_all" to Pair("Clear All History", "Limpiar Todo el Historial"),
            "stats_trans" to Pair("Transcriptions", "Transcr."),
            "stats_secs" to Pair("Total Seconds", "Horas/Secs"),
            "stats_cost" to Pair("Cloud Cost Estim.", "Costo Cloud"),
            "models_title" to Pair("On-Demand Offline Models", "Modelos Offline On-Demand"),
            "models_desc" to Pair("Download or delete voice models to optimize your local device storage limit.", "Descarga o elimina los modelos para ajustar el almacenamiento de tu dispositivo."),
            "settings_title" to Pair("Robin AI Preferences", "Configuraciones Robin AI"),
            "settings_sub" to Pair("Manage theme, locale language, and network service parameters dynamically.", "Gestión de interfaz, idioma local y parámetros de red en tiempo real."),
            "theme_label" to Pair("Dark Aesthetic Theme", "Tema Estético Oscuro"),
            "lang_label" to Pair("Interface Language", "Language / Idioma"),
            "btn_down" to Pair("Download", "Descargar"),
            "btn_del" to Pair("Delete", "Borrar"),
            "warn_missing_model" to Pair("Missing Speech Model", "Falta modelo de procesamiento"),
            "warn_missing_desc" to Pair("Download the local model to utilize this offline selection without internet.", "Descarga el modelo local para usar este motor sin internet."),
            "whisper_config" to Pair("Whisper API & Endpoint Customization", "Personalización de OpenAI Whisper API"),
            "whisper_key_placeholder" to Pair("Enter custom Whisper API Key...", "Introduce la clave API de Whisper..."),
            "whisper_endpoint_placeholder" to Pair("Enter custom endpoint URL...", "Introduce la URL del endpoint de Whisper..."),
            "tts_model_placeholder" to Pair("Enter TTS Model Name...", "Introduce el nombre del modelo TTS...")
        )
        val value = strings[key] ?: return key
        return if (lang == "en") value.first else value.second
    }
}

// Layout optimization helpers supporting adaptive heights smoothly
@Composable
fun TextStyleWithSafeSize() = MaterialTheme.typography.bodyMedium.copy(
    fontSize = 14.sp
)

@Composable
fun Modifier.verticalScrollAndCompactLayout() = this
    .fillMaxSize()
    .verticalScroll(rememberScrollState())
    .padding(bottom = 12.dp)

fun Modifier.horizontalScrollContainer() = this
    .fillMaxWidth()
