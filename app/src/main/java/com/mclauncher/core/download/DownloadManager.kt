package com.mclauncher.core.download

import android.content.Context
import com.mclauncher.data.local.preferences.PreferencesManager
import com.mclauncher.domain.models.DownloadStatus
import com.mclauncher.domain.models.DownloadTask
import com.mclauncher.domain.models.DownloadType
import com.mclauncher.utils.HashUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
    private val preferencesManager: PreferencesManager
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .build()

    private val _downloadQueue = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloadQueue: StateFlow<List<DownloadTask>> = _downloadQueue.asStateFlow()

    private val _currentDownloads = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())
    val currentDownloads: StateFlow<Map<String, DownloadTask>> = _currentDownloads.asStateFlow()

    private val _overallProgress = MutableStateFlow(0f)
    val overallProgress: StateFlow<Float> = _overallProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private var downloadScope: CoroutineScope? = null

    private var maxConcurrentDownloads = 4
    private var verifyChecksums = true

    init {
        // Observe preferences
        CoroutineScope(ioDispatcher).launch {
            preferencesManager.preferences.collect { prefs ->
                maxConcurrentDownloads = prefs.maxConcurrentDownloads
                verifyChecksums = prefs.downloadVerifyChecksums
            }
        }
    }

    suspend fun queueDownload(task: DownloadTask) {
        _downloadQueue.update { queue ->
            if (queue.none { it.id == task.id }) {
                queue + task
            } else {
                queue
            }
        }
    }

    suspend fun queueDownloads(tasks: List<DownloadTask>) {
        _downloadQueue.update { queue ->
            val newTasks = tasks.filter { task -> queue.none { it.id == task.id } }
            queue + newTasks
        }
    }

    fun startDownloads(): Flow<DownloadProgress> = flow {
        if (_isDownloading.value) {
            Timber.w("Downloads already in progress")
            return@flow
        }

        _isDownloading.value = true
        downloadScope = CoroutineScope(ioDispatcher + SupervisorJob())

        val queue = _downloadQueue.value.toMutableList()
        var completedCount = 0
        val totalCount = queue.size

        Timber.d("Starting download of $totalCount files")

        try {
            while (queue.isNotEmpty()) {
                val activeTasks = _currentDownloads.value.size
                val availableSlots = maxConcurrentDownloads - activeTasks

                if (availableSlots > 0) {
                    val tasksToStart = queue.take(availableSlots)
                    queue.removeAll(tasksToStart.toSet())

                    tasksToStart.forEach { task ->
                        val job = downloadScope!!.launch {
                            downloadFile(task).collect { progress ->
                                updateTaskProgress(task.id, progress)
                                
                                if (progress.status == DownloadStatus.COMPLETED ||
                                    progress.status == DownloadStatus.FAILED) {
                                    completedCount++
                                    _currentDownloads.update { it - task.id }
                                    activeJobs.remove(task.id)

                                    _overallProgress.value = completedCount.toFloat() / totalCount
                                }
                            }
                        }
                        activeJobs[task.id] = job
                        _currentDownloads.update { 
                            it + (task.id to task.copy(status = DownloadStatus.DOWNLOADING)) 
                        }
                    }
                }

                emit(DownloadProgress(
                    completed = completedCount,
                    total = totalCount,
                    progress = _overallProgress.value,
                    currentTasks = _currentDownloads.value.values.toList()
                ))

                delay(100)
            }

            // Wait for remaining downloads
            while (_currentDownloads.value.isNotEmpty()) {
                emit(DownloadProgress(
                    completed = completedCount,
                    total = totalCount,
                    progress = _overallProgress.value,
                    currentTasks = _currentDownloads.value.values.toList()
                ))
                delay(100)
            }

            emit(DownloadProgress(
                completed = totalCount,
                total = totalCount,
                progress = 1f,
                currentTasks = emptyList(),
                isComplete = true
            ))

        } catch (e: CancellationException) {
            Timber.d("Downloads cancelled")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Download error")
            emit(DownloadProgress(
                completed = completedCount,
                total = totalCount,
                progress = _overallProgress.value,
                currentTasks = _currentDownloads.value.values.toList(),
                error = e.message
            ))
        } finally {
            _isDownloading.value = false
            _downloadQueue.value = emptyList()
            _currentDownloads.value = emptyMap()
            _overallProgress.value = 0f
            downloadScope?.cancel()
            downloadScope = null
        }
    }.flowOn(ioDispatcher)

    private fun downloadFile(task: DownloadTask): Flow<TaskProgress> = flow {
        emit(TaskProgress(task.id, 0f, DownloadStatus.DOWNLOADING))

        try {
            // Create parent directories
            task.destination.parentFile?.mkdirs()

            // Check if file exists and is valid
            if (task.destination.exists() && task.sha1 != null && verifyChecksums) {
                val existingHash = HashUtils.calculateSHA1(task.destination)
                if (existingHash.equals(task.sha1, ignoreCase = true)) {
                    Timber.d("File already exists and valid: ${task.destination.name}")
                    emit(TaskProgress(task.id, 1f, DownloadStatus.COMPLETED))
                    return@flow
                }
            }

            val request = Request.Builder()
                .url(task.url)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Download failed: ${response.code} ${response.message}")
                }

                val body = response.body ?: throw IOException("Empty response body")
                val contentLength = body.contentLength()
                var bytesRead = 0L

                FileOutputStream(task.destination).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var read: Int

                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read

                            val progress = if (contentLength > 0) {
                                bytesRead.toFloat() / contentLength
                            } else {
                                0f
                            }
                            emit(TaskProgress(task.id, progress, DownloadStatus.DOWNLOADING))
                        }
                    }
                }
            }

            // Verify checksum
            if (task.sha1 != null && verifyChecksums) {
                emit(TaskProgress(task.id, 1f, DownloadStatus.VERIFYING))
                val hash = HashUtils.calculateSHA1(task.destination)
                if (!hash.equals(task.sha1, ignoreCase = true)) {
                    task.destination.delete()
                    throw IOException("Checksum mismatch for ${task.destination.name}")
                }
            }

            Timber.d("Downloaded: ${task.destination.name}")
            emit(TaskProgress(task.id, 1f, DownloadStatus.COMPLETED))

        } catch (e: CancellationException) {
            task.destination.delete()
            emit(TaskProgress(task.id, 0f, DownloadStatus.CANCELLED))
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to download: ${task.url}")
            emit(TaskProgress(task.id, 0f, DownloadStatus.FAILED, e.message))
        }
    }.flowOn(ioDispatcher)

    suspend fun downloadSingleFile(
        url: String,
        destination: File,
        sha1: String? = null,
        type: DownloadType = DownloadType.LIBRARY
    ): Result<File> = withContext(ioDispatcher) {
        try {
            val task = DownloadTask(
                url = url,
                destination = destination,
                sha1 = sha1,
                type = type
            )

            var result: Result<File> = Result.failure(Exception("Download not completed"))

            downloadFile(task).collect { progress ->
                when (progress.status) {
                    DownloadStatus.COMPLETED -> result = Result.success(destination)
                    DownloadStatus.FAILED -> result = Result.failure(
                        Exception(progress.error ?: "Download failed")
                    )
                    else -> {}
                }
            }

            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun cancelDownload(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        _currentDownloads.update { it - taskId }
        _downloadQueue.update { queue -> queue.filter { it.id != taskId } }
    }

    fun cancelAllDownloads() {
        downloadScope?.cancel()
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        _downloadQueue.value = emptyList()
        _currentDownloads.value = emptyMap()
        _isDownloading.value = false
        _overallProgress.value = 0f
    }

    private fun updateTaskProgress(taskId: String, progress: TaskProgress) {
        _currentDownloads.update { map ->
            map[taskId]?.let { task ->
                map + (taskId to task.copy(
                    progress = progress.progress,
                    status = progress.status,
                    error = progress.error
                ))
            } ?: map
        }
    }

    fun clearQueue() {
        _downloadQueue.value = emptyList()
    }
}

data class DownloadProgress(
    val completed: Int,
    val total: Int,
    val progress: Float,
    val currentTasks: List<DownloadTask>,
    val isComplete: Boolean = false,
    val error: String? = null
)

data class TaskProgress(
    val taskId: String,
    val progress: Float,
    val status: DownloadStatus,
    val error: String? = null
)
