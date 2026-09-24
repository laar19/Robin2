package com.example.speech.tile

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import com.example.speech.floating.FloatingVoiceService

/**
 * Quick Settings Tile service that allows enabling/disabling the floating voice
 * dictation bubble directly from the Android Quick Settings panel.
 */
class QuickTileVoiceService : TileService() {

    private val TAG = "QuickTileVoiceService"

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Se requiere permiso de superposición para la burbuja", Toast.LENGTH_LONG).show()
            val overlayIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this, 0, overlayIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(overlayIntent)
            }
            return
        }

        if (FloatingVoiceService.isServiceRunning) {
            FloatingVoiceService.stop(this)
            setTileInactive()
            Toast.makeText(this, "Burbuja de dictado desactivada", Toast.LENGTH_SHORT).show()
        } else {
            FloatingVoiceService.start(this)
            setTileActive()
            Toast.makeText(this, "Burbuja de dictado activada sobre la pantalla", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        if (FloatingVoiceService.isServiceRunning) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Dictado Robin"
            tile.contentDescription = "Dictado por voz activo"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Dictado Robin"
            tile.contentDescription = "Dictado por voz inactivo"
        }
        tile.updateTile()
    }

    private fun setTileActive() {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.label = "Dictado Robin"
        tile.updateTile()
    }

    private fun setTileInactive() {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.label = "Dictado Robin"
        tile.updateTile()
    }
}
