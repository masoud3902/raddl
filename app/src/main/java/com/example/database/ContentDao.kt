package com.example.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ContentDao {
    @Query("SELECT * FROM contents WHERE type = 'song' ORDER BY createdAt DESC")
    fun getAllSongs(): Flow<List<ContentEntity>>

    @Query("SELECT * FROM contents WHERE type = 'podcast' ORDER BY createdAt DESC")
    fun getAllPodcasts(): Flow<List<ContentEntity>>

    @Query("SELECT * FROM contents WHERE id = :id LIMIT 1")
    suspend fun getContentById(id: String): ContentEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertContents(contents: List<ContentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContent(content: ContentEntity)

    @Update
    suspend fun updateContent(content: ContentEntity)

    @Query("DELETE FROM contents WHERE id = :id")
    suspend fun deleteContentById(id: String)

    @Query("DELETE FROM contents")
    suspend fun clearAllContents()

    // Sources
    @Query("SELECT * FROM sources WHERE contentId = :contentId")
    fun getSourcesByContentId(contentId: String): Flow<List<SourceEntity>>

    @Query("SELECT * FROM sources WHERE contentId = :contentId")
    suspend fun getSourcesByContentIdSync(contentId: String): List<SourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: SourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSources(sources: List<SourceEntity>)

    @Query("DELETE FROM sources WHERE contentId = :contentId")
    suspend fun deleteSourcesByContentId(contentId: String)

    @Update
    suspend fun updateSource(source: SourceEntity)

    // Site Stats
    @Query("SELECT * FROM site_stats")
    fun getAllSiteStats(): Flow<List<SiteStatsEntity>>

    @Query("SELECT * FROM site_stats")
    suspend fun getAllSiteStatsSync(): List<SiteStatsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSiteStat(stat: SiteStatsEntity)
}
