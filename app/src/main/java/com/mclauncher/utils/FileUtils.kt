package com.mclauncher.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.*
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object FileUtils {

    suspend fun deleteRecursively(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (file.isDirectory) {
                file.listFiles()?.forEach { child ->
                    deleteRecursively(child)
                }
            }
            file.delete()
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete: ${file.absolutePath}")
            false
        }
    }

    suspend fun copyFile(source: File, destination: File): Boolean = withContext(Dispatchers.IO) {
        try {
            destination.parentFile?.mkdirs()
            source.inputStream().use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy ${source.name} to ${destination.absolutePath}")
            false
        }
    }

    suspend fun moveFile(source: File, destination: File): Boolean = withContext(Dispatchers.IO) {
        try {
            destination.parentFile?.mkdirs()
            if (source.renameTo(destination)) {
                true
            } else {
                // Fallback to copy + delete
                if (copyFile(source, destination)) {
                    source.delete()
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to move ${source.name} to ${destination.absolutePath}")
            false
        }
    }

    fun getDirectorySize(directory: File): Long {
        if (!directory.exists()) return 0
        
        var size = 0L
        directory.walkTopDown().forEach { file ->
            if (file.isFile) {
                size += file.length()
            }
        }
        return size
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    suspend fun extractZip(
        zipFile: File,
        destDir: File,
        overwrite: Boolean = true
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            destDir.mkdirs()
            
            ZipFile(zipFile).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    val destFile = File(destDir, entry.name)
                    
                    // Security check - prevent zip slip
                    if (!destFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                        throw SecurityException("Zip entry is outside of target dir: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        if (destFile.exists() && !overwrite) {
                            return@forEach
                        }
                        
                        destFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract ${zipFile.name}")
            Result.failure(e)
        }
    }

    suspend fun extractZipEntry(
        zipFile: File,
        entryName: String,
        destFile: File
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            destFile.parentFile?.mkdirs()
            
            ZipFile(zipFile).use { zip ->
                val entry = zip.getEntry(entryName)
                    ?: return@withContext Result.failure(FileNotFoundException("Entry not found: $entryName"))
                
                zip.getInputStream(entry).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract $entryName from ${zipFile.name}")
            Result.failure(e)
        }
    }

    fun readTextFile(file: File): String? {
        return try {
            file.readText()
        } catch (e: Exception) {
            Timber.e(e, "Failed to read ${file.name}")
            null
        }
    }

    fun writeTextFile(file: File, content: String): Boolean {
        return try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to write ${file.name}")
            false
        }
    }

    fun listFilesRecursively(directory: File, filter: (File) -> Boolean = { true }): List<File> {
        val files = mutableListOf<File>()
        directory.walkTopDown().forEach { file ->
            if (file.isFile && filter(file)) {
                files.add(file)
            }
        }
        return files
    }

    fun getFileExtension(file: File): String {
        return file.name.substringAfterLast('.', "")
    }

    fun getFileNameWithoutExtension(file: File): String {
        return file.name.substringBeforeLast('.')
    }

    fun ensureDirectoryExists(directory: File): Boolean {
        return if (directory.exists()) {
            directory.isDirectory
        } else {
            directory.mkdirs()
        }
    }

    fun safeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    suspend fun countFiles(directory: File): Int = withContext(Dispatchers.IO) {
        if (!directory.exists()) return@withContext 0
        
        var count = 0
        directory.walkTopDown().forEach { file ->
            if (file.isFile) count++
        }
        count
    }
}

object HashUtils {
    
    fun calculateSHA1(file: File): String {
        return calculateHash(file, "SHA-1")
    }

    fun calculateSHA256(file: File): String {
        return calculateHash(file, "SHA-256")
    }

    fun calculateMD5(file: File): String {
        return calculateHash(file, "MD5")
    }

    private fun calculateHash(file: File, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun calculateSHA1(data: ByteArray): String {
        return calculateHash(data, "SHA-1")
    }

    fun calculateSHA1(text: String): String {
        return calculateSHA1(text.toByteArray())
    }

    private fun calculateHash(data: ByteArray, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    fun verifyFile(file: File, expectedHash: String, algorithm: String = "SHA-1"): Boolean {
        return try {
            val actualHash = calculateHash(file, algorithm)
            actualHash.equals(expectedHash, ignoreCase = true)
        } catch (e: Exception) {
            Timber.e(e, "Failed to verify hash for ${file.name}")
            false
        }
    }
}
