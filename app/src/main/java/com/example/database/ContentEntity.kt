package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contents")
data class ContentEntity(
    @PrimaryKey val id: String, // E.g., RJ URL path or unique name
    val type: String, // "song" or "podcast"
    val title: String,
    val artist: String,
    val coverUrl: String,
    val rjUrl: String,
    val publishDate: String,
    val searched: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
