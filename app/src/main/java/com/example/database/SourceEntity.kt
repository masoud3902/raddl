package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val contentId: String, // foreign key pointing to ContentEntity.id
    val siteName: String,
    val pageUrl: String,
    val quality: String, // "128 kbps", "320 kbps", "FLAC", etc.
    val downloadUrl: String,
    val selected: Boolean = false,
    val lastChecked: Long = System.currentTimeMillis()
)
