package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "site_stats")
data class SiteStatsEntity(
    @PrimaryKey val siteDomain: String, // e.g. "limusic.org"
    val successCount: Int = 0,
    val totalAttempts: Int = 0
) {
    val successRate: Int
        get() = if (totalAttempts > 0) (successCount * 100) / totalAttempts else 50 // 50% default
}
