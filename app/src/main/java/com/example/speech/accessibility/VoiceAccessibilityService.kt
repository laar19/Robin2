package com.example.speech.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * AccessibilityService that enables direct text injection into any currently focused
 * editable view across the Android OS.
 */
class VoiceAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "VoiceAccessibility"
        @Volatile
        private var instance: VoiceAccessibilityService? = null

        /**
         * Returns true if the service is currently enabled and connected.
         */
        fun isServiceRunning(): Boolean = instance != null

        /**
         * Injects the specified text into the currently active editable input field.
         */
        fun injectTextIntoFocusedView(text: String): Boolean {
            val service = instance
            if (service == null) {
                Log.w(TAG, "VoiceAccessibilityService is not connected")
                return false
            }
            return service.performInjection(text)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "VoiceAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Monitored for window/focus changes if needed
    }

    override fun onInterrupt() {
        Log.w(TAG, "VoiceAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.d(TAG, "VoiceAccessibilityService destroyed")
    }

    private fun performInjection(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        try {
            // Attempt 1: Standard input focus search
            var targetNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            
            // Attempt 2: If standard focus not found, search recursively for an editable or focused node
            if (targetNode == null) {
                targetNode = findEditableNode(root)
            }

            if (targetNode != null && targetNode.isEditable) {
                val existingText = targetNode.text?.toString() ?: ""
                val prefix = if (existingText.isNotEmpty() && !existingText.endsWith(" ")) " " else ""
                val combinedText = existingText + prefix + text

                val bundle = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, combinedText)
                }

                val success = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                Log.d(TAG, "Injected text successfully: $success")
                targetNode.recycle()
                return success
            } else {
                Log.w(TAG, "No editable focused node found to inject text")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing text injection", e)
        }
        return false
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableNode(child)
            if (found != null) {
                return found
            }
            child.recycle()
        }
        return null
    }
}
