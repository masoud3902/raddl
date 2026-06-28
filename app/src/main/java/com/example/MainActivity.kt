package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.database.AppDatabase
import com.example.database.ContentEntity
import com.example.database.ContentRepository
import com.example.database.SourceEntity
import com.example.database.SiteStatsEntity
import com.example.downloader.DownloadManager
import com.example.downloader.DownloadStatus
import com.example.parser.SearchResult
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Database & Dependencies
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = ContentRepository(database.contentDao())
        val downloadManager = DownloadManager(applicationContext)

        setContent {
            MyApplicationTheme(darkTheme = false) { // High Density Theme (light lavender M3)
                MainScreen(repository, downloadManager)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(repository: ContentRepository, downloadManager: DownloadManager) {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModel.Factory(context.applicationContext as android.app.Application, repository, downloadManager)
    )

    // Observe flows
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val podcasts by viewModel.podcasts.collectAsStateWithLifecycle()
    val siteStats by viewModel.siteStats.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val notificationMessage by viewModel.notificationMessage.collectAsStateWithLifecycle()

    val searchActiveItem by viewModel.searchActiveItem.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val selectedSites by viewModel.selectedSites.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val isExtracting by viewModel.isExtracting.collectAsStateWithLifecycle()

    val activeSources by viewModel.activeSources.collectAsStateWithLifecycle()
    val selectedSourcesItem by viewModel.selectedSourcesItem.collectAsStateWithLifecycle()

    val downloadStates by downloadManager.downloadStates.collectAsStateWithLifecycle()

    // Settings
    val downloadFolder by viewModel.downloadFolder.collectAsStateWithLifecycle()
    val maxConcurrentDownloads by viewModel.maxConcurrentDownloads.collectAsStateWithLifecycle()
    val searchLimit by viewModel.searchLimit.collectAsStateWithLifecycle()
    val autoUpdateEnabled by viewModel.autoUpdateEnabled.collectAsStateWithLifecycle()
    val customRjUrl by viewModel.customRjUrl.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(0) } // 0: Songs, 1: Podcasts, 2: Active Downloads, 3: Site Stats, 4: Settings
    var selectedItemForDetail by remember { mutableStateOf<ContentEntity?>(null) }

    // Floating notifications for new content
    LaunchedEffect(notificationMessage) {
        notificationMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearNotification()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Custom High Density "RJ" Brand box
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "RJ",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Column {
                            Text(
                                text = "RJ Finder",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "جستجو و دانلود غیرمستقیم موزیک",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshFromRadioJavan() },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "بروزرسانی از رادیوجوان")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "آهنگ‌ها") },
                    label = { Text("آهنگ‌ها", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.List, contentDescription = "پادکست‌ها") },
                    label = { Text("پادکست‌ها", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { 
                        BadgedBox(badge = {
                            val activeCount = downloadStates.values.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONNECTING }
                            if (activeCount > 0) {
                                Badge { Text(activeCount.toString()) }
                            }
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "دانلودها")
                        }
                    },
                    label = { Text("دانلودها", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = activeTab == 3,
                    onClick = { activeTab = 3 },
                    icon = { Icon(Icons.Default.Star, contentDescription = "یادگیری") },
                    label = { Text("یادگیری", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                NavigationBarItem(
                    selected = activeTab == 4,
                    onClick = { activeTab = 4 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "تنظیمات") },
                    label = { Text("تنظیمات", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                    )
                )
        ) {
            when (activeTab) {
                0 -> ContentList(
                    items = songs,
                    emptyMessage = "در حال بارگذاری لیست آهنگ‌های جدید...",
                    onItemClick = { selectedItemForDetail = it }
                )
                1 -> ContentList(
                    items = podcasts,
                    emptyMessage = "در حال بارگذاری لیست پادکست‌های جدید...",
                    onItemClick = { selectedItemForDetail = it }
                )
                2 -> DownloadsScreen(
                    downloadStates = downloadStates.values.toList(),
                    onPause = { viewModel.pauseDownload(it) },
                    onResume = { viewModel.resumeDownload(it) },
                    onDelete = { viewModel.deleteDownload(it) },
                    onOpenFolder = {
                        Toast.makeText(context, "پوشه دانلود: $downloadFolder", Toast.LENGTH_LONG).show()
                    },
                    downloadFolder = downloadFolder
                )
                3 -> LearningScreen(siteStats = siteStats)
                4 -> SettingsScreen(
                    downloadFolder = downloadFolder,
                    maxConcurrent = maxConcurrentDownloads,
                    searchLimit = searchLimit,
                    autoUpdate = autoUpdateEnabled,
                    customRjUrl = customRjUrl,
                    onSave = { folder, limit, concurrent, url, autoUpdate ->
                        viewModel.updateSettings(folder, limit, concurrent, url, autoUpdate)
                        Toast.makeText(context, "تنظیمات با موفقیت ذخیره شد", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // Item Option Action Dialog/Bottom Sheet
    selectedItemForDetail?.let { item ->
        ItemDetailDialog(
            item = item,
            onDismiss = { selectedItemForDetail = null },
            onSearch = {
                viewModel.initiateSearch(item)
                selectedItemForDetail = null
            },
            onDownloadClick = {
                viewModel.viewSources(item)
                selectedItemForDetail = null
            },
            onDelete = {
                viewModel.deleteContent(item)
                selectedItemForDetail = null
                Toast.makeText(context, "آیتم حذف شد", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Search results checkboxes Dialog
    searchActiveItem?.let { item ->
        SearchAndSelectDialog(
            item = item,
            isSearching = isSearching,
            searchResults = searchResults,
            selectedSites = selectedSites,
            isExtracting = isExtracting,
            onToggleSite = { viewModel.toggleSiteSelection(it) },
            onExtract = { viewModel.extractLinksForActiveItem() },
            onCancel = { viewModel.cancelSearch() }
        )
    }

    // Quality selection Dialog
    selectedSourcesItem?.let { item ->
        DownloadSourcesDialog(
            item = item,
            sources = activeSources,
            siteStats = siteStats,
            onDownload = { source ->
                viewModel.startDownload(source, item.title)
                Toast.makeText(context, "دانلود آغاز شد. برای مشاهده پیشرفت به تب دانلودها بروید.", Toast.LENGTH_LONG).show()
            },
            onClose = { viewModel.closeSources() }
        )
    }
}

@Composable
fun ContentList(
    items: List<ContentEntity>,
    emptyMessage: String,
    onItemClick: (ContentEntity) -> Unit
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = emptyMessage,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.id }) { item ->
                ContentCard(item = item, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
fun ContentCard(item: ContentEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover Image
            AsyncImage(
                model = item.coverUrl,
                contentDescription = item.title,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.artist,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "تاریخ انتشار",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = item.publishDate,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Search Status Badge
            if (item.searched) {
                SuggestionChip(
                    onClick = {},
                    label = { Text("لینک شده", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    border = null
                )
            } else {
                SuggestionChip(
                    onClick = {},
                    label = { Text("جدید", fontSize = 11.sp) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = null
                )
            }
        }
    }
}

@Composable
fun ItemDetailDialog(
    item: ContentEntity,
    onDismiss: () -> Unit,
    onSearch: () -> Unit,
    onDownloadClick: () -> Unit,
    onDelete: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Details
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = item.title,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = item.artist,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Button(
                    onClick = onSearch,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🔍 جستجوی لینک دانلود", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDownloadClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    enabled = item.searched
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("📥 دانلود موزیک", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🗑️ حذف آیتم", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SearchAndSelectDialog(
    item: ContentEntity,
    isSearching: Boolean,
    searchResults: List<SearchResult>,
    selectedSites: Set<String>,
    isExtracting: Boolean,
    onToggleSite: (String) -> Unit,
    onExtract: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(onDismissRequest = onCancel) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "جستجوی لینک دانلود در اینترنت",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.End)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "برنامه در حال بررسی بهترین وبسایت‌های موزیک ایرانی برای دانلود آهنگ \"${item.title}\" است.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("در حال جستجو و خوشه‌بندی سایت‌ها...", fontSize = 12.sp)
                        }
                    }
                } else {
                    Text(
                        text = "سایت‌های یافته شده (انتخاب کنید):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.End)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        items(searchResults) { result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleSite(result.domain) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedSites.contains(result.domain),
                                    onCheckedChange = { onToggleSite(result.domain) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = result.domain,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = result.title,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onExtract,
                        modifier = Modifier.weight(1f),
                        enabled = !isSearching && !isExtracting && selectedSites.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isExtracting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("استخراج لینک‌ها", fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("انصراف")
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadSourcesDialog(
    item: ContentEntity,
    sources: List<SourceEntity>,
    siteStats: List<SiteStatsEntity>,
    onDownload: (SourceEntity) -> Unit,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "لینک‌های دانلود استخراج شده",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.End)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "سایت‌های با نرخ موفقیت بالاتر به صورت خودکار در صدر لیست قرار گرفته‌اند.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (sources.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("در حال بارگذاری لینک‌ها...", fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(sources) { source ->
                            val stat = siteStats.find { it.siteDomain.lowercase() == source.siteName.lowercase() }
                            val successRate = stat?.successRate ?: 50

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { onDownload(source) },
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "دانلود")
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = source.quality,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "موفقیت: $successRate%",
                                                fontSize = 11.sp,
                                                color = if (successRate >= 80) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = source.siteName,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("بستن")
                }
            }
        }
    }
}

@Composable
fun DownloadsScreen(
    downloadStates: List<com.example.downloader.DownloadState>,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenFolder: () -> Unit,
    downloadFolder: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = onOpenFolder,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Home, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("باز کردن پوشه دانلود")
            }
            Text(
                text = "لیست دانلودهای فعال",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "مسیر ذخیره: $downloadFolder",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (downloadStates.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = "هیچ دانلودی در حال اجرا نیست.\nبرای دانلود آهنگ‌ها، به تب اول بروید.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(downloadStates, key = { it.url }) { download ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (MaterialTheme.colorScheme.primary == Color(0xFF6750A4)) Color.White else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onDelete(download.url) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                                    }
                                    if (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.CONNECTING) {
                                        IconButton(onClick = { onPause(download.url) }) {
                                            Text("||", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                                        }
                                    } else if (download.status == DownloadStatus.PAUSED) {
                                        IconButton(onClick = { onResume(download.url) }) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "شروع مجدد")
                                        }
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = download.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = download.speed,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { download.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (download.status == DownloadStatus.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = when (download.status) {
                                        DownloadStatus.IDLE -> "آماده"
                                        DownloadStatus.CONNECTING -> "در حال اتصال..."
                                        DownloadStatus.DOWNLOADING -> "در حال دانلود..."
                                        DownloadStatus.PAUSED -> "متوقف شده"
                                        DownloadStatus.COMPLETED -> "کامل شد"
                                        DownloadStatus.ERROR -> "خطا در اتصال"
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "${(download.progress * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LearningScreen(siteStats: List<SiteStatsEntity>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "سیستم یادگیری و رتبه‌بندی سایت‌ها",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.align(Alignment.End)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "برنامه آمار دانلودهای موفق از هر سایت را آنالیز می‌کند و سایتهای پایدارتر و کاملتر را در اولویت جستجو و پیشنهاد قرار می‌دهد.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Right,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(20.dp))

        if (siteStats.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = "هنوز آماری ثبت نشده است.\nنرخ موفقیت سایت‌ها با اولین دانلود موفق محاسبه خواهد شد.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(siteStats.sortedByDescending { it.successRate }) { stat ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (MaterialTheme.colorScheme.primary == Color(0xFF6750A4)) Color.White else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${stat.successRate}%",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = if (stat.successRate >= 80) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                            )

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = stat.siteDomain,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "دانلود موفق: ${stat.successCount} از ${stat.totalAttempts} تلاش",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    downloadFolder: String,
    maxConcurrent: Int,
    searchLimit: Int,
    autoUpdate: Boolean,
    customRjUrl: String,
    onSave: (String, Int, Int, String, Boolean) -> Unit
) {
    var folderState by remember(downloadFolder) { mutableStateOf(downloadFolder) }
    var concurrentState by remember(maxConcurrent) { mutableStateOf(maxConcurrent.toString()) }
    var limitState by remember(searchLimit) { mutableStateOf(searchLimit.toString()) }
    var autoUpState by remember(autoUpdate) { mutableStateOf(autoUpdate) }
    var rjUrlState by remember(customRjUrl) { mutableStateOf(customRjUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "تنظیمات برنامه",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.align(Alignment.End)
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Folders Selection
        Text("📁 مسیر پوشه دانلود:", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.align(Alignment.End))
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = folderState,
            onValueChange = { folderState = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Custom RJ URL
        Text("🔗 لینک کاستوم رادیو جوان (مثل صفحه خواننده یا پلی‌لیست):", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.align(Alignment.End))
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = rjUrlState,
            onValueChange = { rjUrlState = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("مثال: https://www.radiojavan.com/playlists/playlist/mp3/xxx") },
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Search site limit
        Text("🔍 تعداد نتایج جستجو:", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.align(Alignment.End))
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = limitState,
            onValueChange = { limitState = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Max Concurrent
        Text("⚡ تعداد دانلود همزمان:", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.align(Alignment.End))
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = concurrentState,
            onValueChange = { concurrentState = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Auto check
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { autoUpState = !autoUpState }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Switch(
                checked = autoUpState,
                onCheckedChange = { autoUpState = it }
            )
            Text("بروزرسانی خودکار و زمانبندی شده", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                val lim = limitState.toIntOrNull() ?: 5
                val con = concurrentState.toIntOrNull() ?: 2
                onSave(folderState, lim, con, rjUrlState, autoUpState)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("ذخیره تنظیمات", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}
