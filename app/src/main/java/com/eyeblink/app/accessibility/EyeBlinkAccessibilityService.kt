package com.eyeblink.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class EyeBlinkAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun findPlayPauseNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val desc = root.contentDescription?.toString()?.lowercase() ?: ""
        if (desc.contains("play") || desc.contains("pause")) return root
        for (i in 0 until root.childCount) {
            val found = findPlayPauseNode(root.getChild(i))
            if (found != null) return found
        }
        return null
    }
}
EOF
echo done