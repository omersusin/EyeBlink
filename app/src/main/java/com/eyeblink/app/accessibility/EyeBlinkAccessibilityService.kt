package com.eyeblink.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Rootless UI observation. Playback control is layered: MediaSession first
 * (PlaybackController); this is the fallback for apps that expose a labelled
 * play/pause control but no usable MediaSession.
 */
class EyeBlinkAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    /**
     * Breadth-first, depth-limited search for a play/pause control.
     * Bounded on purpose: real view hierarchies are deep enough that unbounded
     * recursion risks a StackOverflowError on the accessibility thread.
     */
    fun findPlayPauseNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        var depth = 0
        while (queue.isNotEmpty() && depth < MAX_DEPTH) {
            val size = queue.size
            repeat(size) {
                val node = queue.removeFirst()
                if (isPlayPause(node)) return node
                for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
            }
            depth++
        }
        return null
    }

    private fun isPlayPause(node: AccessibilityNodeInfo): Boolean {
        val label = node.contentDescription?.toString()?.lowercase().orEmpty()
        val text = node.text?.toString()?.lowercase().orEmpty()
        return PLAY_PAUSE_WORDS.any { label.contains(it) || text.contains(it) }
    }

    private companion object {
        const val MAX_DEPTH = 12
        val PLAY_PAUSE_WORDS = listOf("play", "pause")
    }
}