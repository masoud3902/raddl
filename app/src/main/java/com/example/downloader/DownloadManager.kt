package com.example.downloader

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

enum class DownloadStatus {
    IDLE,
    CONNECTING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    ERROR
}

data class DownloadState(
    val url: String,
    val title: String,
    val progress: Float = 0f,
    val speed: String = "0 KB/s",
    val status: DownloadStatus = DownloadStatus.IDLE,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val errorMessage: String? = null,
    val localFilePath: String? = null
)

class DownloadManager(private val context: Context) {
    private val TAG = "DownloadManager"
    
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Settings
    var maxConcurrentDownloads = 2
    var customDownloadFolder: String = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.absolutePath 
        ?: context.filesDir.absolutePath

    fun startDownload(url: String, title: String) {
        val currentState = _downloadStates.value[url]
        if (currentState?.status == DownloadStatus.DOWNLOADING || currentState?.status == DownloadStatus.CONNECTING) {
            return // Already active
        }

        // Initialize state
        updateState(url, DownloadState(url = url, title = title, status = DownloadStatus.CONNECTING))

        val job = coroutineScope.launch {
            runDownloadLoop(url, title)
        }
        activeJobs[url] = job
    }

    fun pauseDownload(url: String) {
        val job = activeJobs[url]
        if (job != null) {
            job.cancel()
            activeJobs.remove(url)
            val currentState = _downloadStates.value[url]
            if (currentState != null) {
                updateState(url, currentState.copy(status = DownloadStatus.PAUSED, speed = "Paused"))
            }
        }
    }

    fun resumeDownload(url: String) {
        val currentState = _downloadStates.value[url]
        if (currentState != null && currentState.status == DownloadStatus.PAUSED) {
            startDownload(url, currentState.title)
        }
    }

    fun deleteDownload(url: String) {
        pauseDownload(url)
        val state = _downloadStates.value[url]
        if (state?.localFilePath != null) {
            try {
                val file = File(state.localFilePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting downloaded file", e)
            }
        }
        val mutable = _downloadStates.value.toMutableMap()
        mutable.remove(url)
        _downloadStates.value = mutable
    }

    private suspend fun runDownloadLoop(urlStr: String, title: String) {
        var connection: HttpURLConnection? = null
        var randomAccessFile: RandomAccessFile? = null
        val cleanFileName = title.replace(Regex("[^a-zA-Z0-9.\\-_]"), "_") + ".mp3"
        val folder = File(customDownloadFolder)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        val file = File(folder, cleanFileName)
        val existingLength = if (file.exists()) file.length() else 0L

        try {
            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            // Request resume using Range header if we have partial file
            if (existingLength > 0) {
                connection.setRequestProperty("Range", "bytes=$existingLength-")
            }

            val responseCode = connection.responseCode
            val isResume = responseCode == HttpURLConnection.HTTP_PARTIAL

            var totalBytes = connection.contentLengthLong
            if (totalBytes <= 0) {
                totalBytes = 0
            }

            if (isResume) {
                totalBytes += existingLength
            } else {
                // If server doesn't support partial resume, overwrite and start from scratch
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // start fresh
                }
            }

            val startByte = if (isResume) existingLength else 0L
            val inputStream = connection.inputStream

            randomAccessFile = RandomAccessFile(file, "rw")
            randomAccessFile.seek(startByte)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var downloaded = startByte
            var lastUpdate = System.currentTimeMillis()
            var bytesSinceLastUpdate = 0L

            updateState(
                urlStr,
                DownloadState(
                    url = urlStr,
                    title = title,
                    status = DownloadStatus.DOWNLOADING,
                    downloadedBytes = downloaded,
                    totalBytes = totalBytes,
                    localFilePath = file.absolutePath
                )
            )

            while (currentCoroutineContext().isActive) {
                bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break

                randomAccessFile.write(buffer, 0, bytesRead)
                downloaded += bytesRead
                bytesSinceLastUpdate += bytesRead

                val now = System.currentTimeMillis()
                val elapsed = now - lastUpdate
                if (elapsed >= 500) { // Update every 500ms
                    val speedKBps = (bytesSinceLastUpdate * 1000f) / (elapsed * 1024f)
                    val speedStr = if (speedKBps >= 1024) {
                        String.format("%.2f MB/s", speedKBps / 1024f)
                    } else {
                        String.format("%.1f KB/s", speedKBps)
                    }

                    val progress = if (totalBytes > 0) downloaded.toFloat() / totalBytes else 0f
                    updateState(
                        urlStr,
                        DownloadState(
                            url = urlStr,
                            title = title,
                            progress = progress,
                            speed = speedStr,
                            status = DownloadStatus.DOWNLOADING,
                            downloadedBytes = downloaded,
                            totalBytes = totalBytes,
                            localFilePath = file.absolutePath
                        )
                    )

                    lastUpdate = now
                    bytesSinceLastUpdate = 0
                }
            }

            if (currentCoroutineContext().isActive) {
                // Done!
                updateState(
                    urlStr,
                    DownloadState(
                        url = urlStr,
                        title = title,
                        progress = 1f,
                        speed = "Completed",
                        status = DownloadStatus.COMPLETED,
                        downloadedBytes = totalBytes,
                        totalBytes = totalBytes,
                        localFilePath = file.absolutePath
                    )
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Download error for $title", e)
            val state = _downloadStates.value[urlStr]
            updateState(
                urlStr,
                state?.copy(
                    status = DownloadStatus.ERROR,
                    errorMessage = e.message ?: "Unknown error",
                    speed = "Error"
                ) ?: DownloadState(url = urlStr, title = title, status = DownloadStatus.ERROR, errorMessage = e.message)
            )
        } finally {
            try {
                randomAccessFile?.close()
                connection?.disconnect()
            } catch (e: Exception) {
                // ignore
            }
            activeJobs.remove(urlStr)
        }
    }

    private fun updateState(url: String, state: DownloadState) {
        val current = _downloadStates.value.toMutableMap()
        current[url] = state
        _downloadStates.value = current
    }
}
