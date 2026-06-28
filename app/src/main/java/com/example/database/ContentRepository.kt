package com.example.database

import kotlinx.coroutines.flow.Flow

class ContentRepository(private val contentDao: ContentDao) {
    val allSongs: Flow<List<ContentEntity>> = contentDao.getAllSongs()
    val allPodcasts: Flow<List<ContentEntity>> = contentDao.getAllPodcasts()
    val allSiteStats: Flow<List<SiteStatsEntity>> = contentDao.getAllSiteStats()

    suspend fun getContentById(id: String) = contentDao.getContentById(id)

    suspend fun insertContents(contents: List<ContentEntity>) {
        contentDao.insertContents(contents)
    }

    suspend fun insertContent(content: ContentEntity) {
        contentDao.insertContent(content)
    }

    suspend fun updateContent(content: ContentEntity) {
        contentDao.updateContent(content)
    }

    suspend fun deleteContent(id: String) {
        contentDao.deleteContentById(id)
        contentDao.deleteSourcesByContentId(id)
    }

    suspend fun clearAllContents() {
        contentDao.clearAllContents()
    }

    fun getSourcesForContent(contentId: String): Flow<List<SourceEntity>> {
        return contentDao.getSourcesByContentId(contentId)
    }

    suspend fun getSourcesForContentSync(contentId: String): List<SourceEntity> {
        return contentDao.getSourcesByContentIdSync(contentId)
    }

    suspend fun insertSources(sources: List<SourceEntity>) {
        contentDao.insertSources(sources)
    }

    suspend fun deleteSourcesByContentId(contentId: String) {
        contentDao.deleteSourcesByContentId(contentId)
    }

    suspend fun updateSource(source: SourceEntity) {
        contentDao.updateSource(source)
    }

    suspend fun recordSiteAttempt(domain: String, success: Boolean) {
        val statsList = contentDao.getAllSiteStatsSync()
        val existing = statsList.find { it.siteDomain.lowercase() == domain.lowercase() }
        val updated = if (existing != null) {
            existing.copy(
                successCount = existing.successCount + (if (success) 1 else 0),
                totalAttempts = existing.totalAttempts + 1
            )
        } else {
            SiteStatsEntity(
                siteDomain = domain,
                successCount = if (success) 1 else 0,
                totalAttempts = 1
            )
        }
        contentDao.insertSiteStat(updated)
    }

    suspend fun getSiteStatsSortedSync(): List<SiteStatsEntity> {
        return contentDao.getAllSiteStatsSync().sortedByDescending { it.successRate }
    }
}
