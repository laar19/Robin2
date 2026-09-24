package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speech.OnDeviceSpeechRecognizerHelper
import com.example.speech.accessibility.VoiceAccessibilityService
import com.example.speech.floating.FloatingVoiceService

@Composable
fun QuickAccessSystemScreen(uiLanguage: String) {
    val context = LocalContext.current

    val isOnDeviceAvailable = remember {
        OnDeviceSpeechRecognizerHelper.isOnDeviceAvailable(context)
    }

    var isFloatingActive by remember {
        mutableStateOf(FloatingVoiceService.isServiceRunning)
    }

    var isAccessibilityActive by remember {
        mutableStateOf(VoiceAccessibilityService.isServiceRunning())
    }

    var testInputText by remember { mutableStateOf("") }
    var showTileGuideDialog by remember { mutableStateOf(false) }

    // Periodically update active statuses when returning to UI
    LaunchedEffect(Unit) {
        isFloatingActive = FloatingVoiceService.isServiceRunning
        isAccessibilityActive = VoiceAccessibilityService.isServiceRunning()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Dictado Global",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = if (uiLanguage == "en") "OS-Wide Voice Dictation" else "Dictado en Todo el Sistema",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (uiLanguage == "en")
                                "Use Robin in WhatsApp, Word, Notes and any application"
                            else
                                "Usa Robin en WhatsApp, Word, Notas o cualquier app del sistema",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 1. On-Device Hardware Check Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "Acelerador",
                            tint = if (isOnDeviceAvailable) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (uiLanguage == "en") "On-Device Engine Acceleration" else "Motor On-Device Sin Conexión",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Surface(
                        color = if (isOnDeviceAvailable) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isOnDeviceAvailable) "DISPONIBLE ✓" else "COMPATIBLE",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isOnDeviceAvailable) Color(0xFF059669) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isOnDeviceAvailable) {
                        if (uiLanguage == "en")
                            "Your device supports Google's ultra-low latency on-device STT with 100% offline local processing."
                        else
                            "Tu terminal cuenta con soporte para SpeechRecognizer On-Device de Google con procesamiento 100% local sin internet."
                    } else {
                        if (uiLanguage == "en")
                            "Hardware offline flag preferred. Audio is processed using Android's native speech recognition subsystem."
                        else
                            "Preferencia sin conexión activada. El reconocimiento se ejecuta a través del subsistema de voz nativo de Android."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2. Custom IME Keyboard Module
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "Teclado",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (uiLanguage == "en") "1. Robin Offline Voice Keyboard (IME)" else "1. Teclado de Voz Robin (IME)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Text(
                    text = if (uiLanguage == "en")
                        "Allows dictating directly through an offline keyboard layout. Transcribed text streams directly into any text box without internet."
                    else
                        "Permite dictar directamente a través de un teclado de voz minimalista. Transcribe en tiempo real en cualquier aplicación del teléfono.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "No se pudo abrir ajustes de teclado", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("enable_ime_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (uiLanguage == "en") "Enable in Settings" else "Habilitar en Ajustes", style = MaterialTheme.typography.labelMedium)
                    }

                    Button(
                        onClick = {
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            imm?.showInputMethodPicker()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("select_ime_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (uiLanguage == "en") "Switch Keyboard" else "Elegir Teclado", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // 3. Floating Overlay Bubble (SYSTEM_ALERT_WINDOW)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Burbuja Flotante",
                            tint = if (isFloatingActive) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = if (uiLanguage == "en") "2. Floating Overlay Bubble" else "2. Burbuja Flotante en Pantalla",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (isFloatingActive) "Servicio Activo en Pantalla" else "Desactivada",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isFloatingActive) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isFloatingActive,
                        onCheckedChange = { enable ->
                            if (enable) {
                                if (!Settings.canDrawOverlays(context)) {
                                    Toast.makeText(
                                        context,
                                        "Otorga el permiso de 'Superponer sobre otras apps'",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    try {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        ).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error abriendo permisos", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    FloatingVoiceService.start(context)
                                    isFloatingActive = true
                                    Toast.makeText(context, "Burbuja flotante activada", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                FloatingVoiceService.stop(context)
                                isFloatingActive = false
                                Toast.makeText(context, "Burbuja flotante detenida", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("floating_bubble_switch")
                    )
                }

                Text(
                    text = if (uiLanguage == "en")
                        "Draggable bubble on the screen edge. Tap to record, and the result automatically copies to clipboard or injects into the active field."
                    else
                        "Burbuja circular arrastrable que flota sobre cualquier app. Toca para dictar y el texto se copiará al portapapeles o se inyectará directamente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 4. Quick Settings Notification Tile Guide
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DashboardCustomize,
                            contentDescription = "Tile",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (uiLanguage == "en") "3. Quick Settings Tile" else "3. Mosaico de Ajustes Rápidos",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    TextButton(
                        onClick = { showTileGuideDialog = true },
                        modifier = Modifier.testTag("view_tile_guide_button")
                    ) {
                        Text(if (uiLanguage == "en") "How to add?" else "¿Cómo agregarlo?")
                    }
                }

                Text(
                    text = if (uiLanguage == "en")
                        "Robin includes a Quick Settings Tile. Pull down your notification shade from the top of the phone to toggle voice dictation in 1 tap."
                    else
                        "Accede al dictado bajando la cortina de notificaciones. Toca 'Dictado Robin' para encender la burbuja flotante al instante.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 5. Accessibility Service Direct Injection
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessibilityNew,
                            contentDescription = "Accesibilidad",
                            tint = if (isAccessibilityActive) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = if (uiLanguage == "en") "4. Auto-Injection (Accessibility)" else "4. Inyección Automática de Texto",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (isAccessibilityActive) "Servicio Conectado ✓" else "Opcional / Inactivo",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isAccessibilityActive) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error abriendo accesibilidad", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("accessibility_settings_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (uiLanguage == "en") "Configure" else "Activar", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Text(
                    text = if (uiLanguage == "en")
                        "Enables Robin to automatically type the spoken words directly into the focused field in WhatsApp, Telegram, Google Docs, etc."
                    else
                        "Permite a Robin pegar el texto transcrito directamente en la caja de texto activa de cualquier app sin que tengas que usar 'Pegar'.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 6. Interactive Local Test Sandbox
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = "Test",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (uiLanguage == "en") "Interactive Testing Sandbox" else "Banco de Prueba en Vivo",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Text(
                    text = if (uiLanguage == "en")
                        "Focus the text field below to test the Robin Keyboard or activate the Floating Bubble to verify injection:"
                    else
                        "Toca el campo inferior para abrir el Teclado Robin o activa la Burbuja Flotante para probar el dictado aquí mismo:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = testInputText,
                    onValueChange = { testInputText = it },
                    placeholder = {
                        Text(if (uiLanguage == "en") "Tap to test keyboard or floating bubble..." else "Toca para probar el teclado o la burbuja...")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("test_input_field"),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (testInputText.isNotEmpty()) {
                            IconButton(onClick = { testInputText = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                            }
                        }
                    },
                    minLines = 3,
                    maxLines = 6
                )
            }
        }
    }

    if (showTileGuideDialog) {
        AlertDialog(
            onDismissRequest = { showTileGuideDialog = false },
            icon = { Icon(Icons.Default.DashboardCustomize, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = {
                Text(
                    text = if (uiLanguage == "en") "How to add Quick Settings Tile" else "Cómo agregar el Mosaico",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Desliza hacia abajo la barra de estado superior dos veces para abrir todos los ajustes rápidos.")
                    Text("2. Toca el icono de lápiz (Editar) en la parte inferior o superior.")
                    Text("3. Busca el mosaico llamado 'Dictado Robin'.")
                    Text("4. Arrástralo hacia arriba entre tus accesos rápidos preferidos.")
                    Text("5. ¡Listo! Ahora podrás activar el dictado de voz desde cualquier lugar con un solo toque.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showTileGuideDialog = false }) {
                    Text("Entendido")
                }
            }
        )
    }
}
