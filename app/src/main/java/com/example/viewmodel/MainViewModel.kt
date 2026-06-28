package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.example.database.AppDatabase
import com.example.database.ContentEntity
import com.example.database.ContentRepository
import com.example.database.SourceEntity
import com.example.database.SiteStatsEntity
import com.example.downloader.DownloadManager
import com.example.parser.RadioJavanParser
import com.example.parser.SearchEngine
import com.example.parser.SearchResult
import com.example.parser.SiteScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(
    application: Application,
    private val repository: ContentRepository,
    val downloadManager: DownloadManager
) : AndroidViewModel(application) {

    // UI States
    val songs: StateFlow<List<ContentEntity>> = repository.allSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val podcasts: StateFlow<List<ContentEntity>> = repository.allPodcasts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val siteStats: StateFlow<List<SiteStatsEntity>> = repository.allSiteStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _notificationMessage = MutableStateFlow<String?>(null)
    val notificationMessage: StateFlow<String?> = _notificationMessage.asStateFlow()

    // Search and Source extraction states
    private val _searchActiveItem = MutableStateFlow<ContentEntity?>(null)
    val searchActiveItem: StateFlow<ContentEntity?> = _searchActiveItem.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _selectedSites = MutableStateFlow<Set<String>>(emptySet()) // set of domains
    val selectedSites: StateFlow<Set<String>> = _selectedSites.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()

    private val prefs = application.getSharedPreferences("rj_finder_prefs", android.content.Context.MODE_PRIVATE)

    // Settings States
    val downloadFolder = MutableStateFlow(prefs.getString("download_folder", downloadManager.customDownloadFolder) ?: downloadManager.customDownloadFolder)
    val maxConcurrentDownloads = MutableStateFlow(prefs.getInt("max_concurrent", downloadManager.maxConcurrentDownloads))
    val searchLimit = MutableStateFlow(prefs.getInt("search_limit", 5))
    val autoUpdateEnabled = MutableStateFlow(prefs.getBoolean("auto_update", false))
    val customRjUrl = MutableStateFlow(prefs.getString("custom_rj_url", "") ?: "")

    // Current viewed sources for selected item in detail modal
    private val _activeSources = MutableStateFlow<List<SourceEntity>>(emptyList())
    val activeSources: StateFlow<List<SourceEntity>> = _activeSources.asStateFlow()

    private val _selectedSourcesItem = MutableStateFlow<ContentEntity?>(null)
    val selectedSourcesItem: StateFlow<ContentEntity?> = _selectedSourcesItem.asStateFlow()

    init {
        // Apply persisted settings to download manager
        downloadManager.customDownloadFolder = downloadFolder.value
        downloadManager.maxConcurrentDownloads = maxConcurrentDownloads.value

        // Automatically check/update on start if enabled
        refreshFromRadioJavan()
    }

    fun clearNotification() {
        _notificationMessage.value = null
    }

    fun refreshFromRadioJavan(clearOld: Boolean = false) {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (clearOld) {
                    repository.clearAllContents()
                }

                val currentSongs = songs.value.map { it.id }.toSet()
                val currentPodcasts = podcasts.value.map { it.id }.toSet()

                val rjUrlToFetch = customRjUrl.value.ifBlank { null }
                val newSongs = RadioJavanParser.fetchFeaturedSongs(rjUrlToFetch)
                val newPodcasts = RadioJavanParser.fetchFeaturedPodcasts(rjUrlToFetch)

                // Filter out existing ones
                val addedSongs = newSongs.filter { it.id !in currentSongs }
                val addedPodcasts = newPodcasts.filter { it.id !in currentPodcasts }

                repository.insertContents(newSongs + newPodcasts)

                // Set notification message in Persian as requested:
                // 🔔 3 آهنگ جدید پیدا شد.
                // 🔔 1 پادکست جدید پیدا شد.
                if (addedSongs.isNotEmpty() || addedPodcasts.isNotEmpty()) {
                    val songText = if (addedSongs.isNotEmpty()) "🔔 ${addedSongs.size} آهنگ جدید پیدا شد." else ""
                    val podText = if (addedPodcasts.isNotEmpty()) "🔔 ${addedPodcasts.size} پادکست جدید پیدا شد." else ""
                    _notificationMessage.value = listOf(songText, podText).filter { it.isNotEmpty() }.joinToString("\n")
                } else if (clearOld) {
                    val fallbackText = "🔔 نتایجی یافت نشد یا سایت تغییر ساختار داده."
                    _notificationMessage.value = fallbackText
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun initiateSearch(item: ContentEntity) {
        _searchActiveItem.value = item
        _searchResults.value = emptyList()
        _selectedSites.value = emptySet()
        _isSearching.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val results = SearchEngine.searchForSong(item.artist, item.title, searchLimit.value)
                _searchResults.value = results.take(searchLimit.value)
                
                // Pre-select known top-performing sites or all by default for convenience
                val domains = results.map { it.domain }.toSet()
                _selectedSites.value = domains
            } catch (e: Exception) {
                // Ignore
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun cancelSearch() {
        _searchActiveItem.value = null
        _searchResults.value = emptyList()
        _selectedSites.value = emptySet()
    }

    fun toggleSiteSelection(domain: String) {
        val current = _selectedSites.value.toMutableSet()
        if (current.contains(domain)) {
            current.remove(domain)
        } else {
            current.add(domain)
        }
        _selectedSites.value = current
    }

    fun extractLinksForActiveItem() {
        val item = _searchActiveItem.value ?: return
        val selected = _searchResults.value.filter { it.domain in _selectedSites.value }
        if (selected.isEmpty()) return

        _isExtracting.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val extractedSources = mutableListOf<SourceEntity>()
                for (site in selected) {
                    val links = SiteScraper.extractDownloadLinks(site.domain, site.pageUrl, item.id)
                    extractedSources.addAll(links)
                }

                if (extractedSources.isNotEmpty()) {
                    repository.deleteSourcesByContentId(item.id)
                    repository.insertSources(extractedSources)
                    
                    // Mark item as searched
                    repository.insertContent(item.copy(searched = true))
                    
                    // View sources directly
                    viewSources(item)
                }
                _searchActiveItem.value = null // Close search dialog
            } catch (e: Exception) {
                // Ignore
            } finally {
                _isExtracting.value = false
            }
        }
    }

    fun viewSources(item: ContentEntity) {
        _selectedSourcesItem.value = item
        viewModelScope.launch(Dispatchers.IO) {
            repository.getSourcesForContent(item.id).collectLatest { sourcesList ->
                // Sort sources by the learning site stats success rate!
                val sortedStats = repository.getSiteStatsSortedSync()
                val sortedSources = sourcesList.sortedWith(compareByDescending { source ->
                    sortedStats.find { it.siteDomain.lowercase() == source.siteName.lowercase() }?.successRate ?: 50
                })
                _activeSources.value = sortedSources
            }
        }
    }

    fun closeSources() {
        _selectedSourcesItem.value = null
        _activeSources.value = emptyList()
    }

    fun startDownload(source: SourceEntity, itemTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Track download attempt
            repository.recordSiteAttempt(source.siteName, success = true) // started downloading counts as positive intent
            
            // Start download via DownloadManager
            downloadManager.startDownload(source.downloadUrl, "$itemTitle - ${source.quality}")
        }
    }

    fun pauseDownload(url: String) {
        downloadManager.pauseDownload(url)
    }

    fun resumeDownload(url: String) {
        downloadManager.resumeDownload(url)
    }

    fun deleteDownload(url: String) {
        downloadManager.deleteDownload(url)
    }

    fun deleteContent(item: ContentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteContent(item.id)
        }
    }

    fun updateSettings(folder: String, limit: Int, concurrent: Int, customUrl: String, autoUpdate: Boolean) {
        val oldUrl = customRjUrl.value
        
        downloadFolder.value = folder
        searchLimit.value = limit
        maxConcurrentDownloads.value = concurrent
        customRjUrl.value = customUrl
        autoUpdateEnabled.value = autoUpdate
        
        downloadManager.customDownloadFolder = folder
        downloadManager.maxConcurrentDownloads = concurrent
        
        prefs.edit().apply {
            putString("download_folder", folder)
            putInt("search_limit", limit)
            putInt("max_concurrent", concurrent)
            putString("custom_rj_url", customUrl)
            putBoolean("auto_update", autoUpdate)
            apply()
        }
        
        // If the URL changed, trigger a refresh automatically
        if (oldUrl != customUrl) {
            refreshFromRadioJavan(clearOld = true)
        }
    }

    // Factory helper
    class Factory(
        private val application: Application,
        private val repository: ContentRepository,
        private val downloadManager: DownloadManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(application, repository, downloadManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
