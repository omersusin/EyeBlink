package com.eyeblink.app.context

import android.app.usage.UsageStatsManager
import android.content.Context

class ContextEngine(private val context: Context) {
    fun foregroundPackage(): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now)
        return stats?.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    fun isInteractive(): Boolean = true // placeholder; extend with keyguard/phone state
}
EOF
echo done