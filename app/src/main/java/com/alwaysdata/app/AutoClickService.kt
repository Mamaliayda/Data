package com.alwaysdata.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Fallback for Xiaomi HyperOS Android 16 where hidden APIs are blocked even with WRITE_SECURE_SETTINGS.
 * When data is off, MainActivity opens the Internet panel; this service auto-clicks the toggle.
 */
class AutoClickService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!Prefs.isProtectionEnabled(this)) return
        if (MobileDataController.isMobileDataEnabled(this)) return

        val root = rootInActiveWindow ?: return
        // Try to find "Mobile data" / "داده تلفن همراه" switch or "Turn on" button in Settings panel
        if (clickIfFound(root, listOf("Mobile data", "Mobile Data", "داده تلفن", "داده همراه", "Internet", "Turn on", "روشن"))) {
            Prefs.log(this, "♿ Accessibility: auto-clicked enable")
        }
    }

    private fun clickIfFound(node: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        val text = (node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")
        for (kw in keywords) {
            if (text.contains(kw, ignoreCase = true)) {
                // Check if node is switch/button
                if (node.isClickable) { node.performAction(AccessibilityNodeInfo.ACTION_CLICK); return true }
                var p = node.parent
                while (p != null) {
                    if (p.isClickable) { p.performAction(AccessibilityNodeInfo.ACTION_CLICK); return true }
                    p = p.parent
                }
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (clickIfFound(child, keywords)) return true
        }
        return false
    }

    override fun onInterrupt() {}
}
