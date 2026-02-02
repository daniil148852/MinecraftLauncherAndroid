package com.mclauncher.core.modloader

import android.content.Context
import android.net.Uri
import com.mclauncher.MCLauncherApp
import com.mclauncher.data.repository.ModRepository
import com.mclauncher.data.repository.ProfileRepository
import com.mclauncher.domain.models.Mod
import com.mclauncher.domain.models.ModLoader
import com.mclauncher.domain.models.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModManager @Inject constructor(
    private val context: Context,
    private val profileRepository: ProfileRepository
) {
    @Inject
    lateinit var modRepository: ModRepository

    suspend fun importMod(uri: Uri, profile: Profile): Result<Mod> = withContext(Dispatchers.IO) {
        try {
            // Get file name from URI
            val fileName = getFileName(uri) ?: "mod_${UUID.randomUUID()}.jar"
            
            // Validate file extension
            if (!fileName.endsWith(".jar") && !fileName.endsWith(".zip")) {
                return@withContext Result.failure(Exception("Invalid mod file. Only .jar and .zip files are supported."))
            }

            // Get mods directory for profile
            val modsDir = getModsDirectory(profile)
            modsDir.mkdirs()

            // Copy file to mods directory
            val destFile = File(modsDir, fileName)
            
            // Check for duplicate
            if (destFile.exists()) {
                return@withContext Result.failure(Exception("A mod with this name already exists"))
            }

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Failed to read mod file"))

            // Parse mod info
            val modInfo = parseModFile(destFile, profile.modLoader)

            // Create mod entry
            val mod = Mod(
                profileId = profile.id,
                name = modInfo.name ?: destFile.nameWithoutExtension,
                fileName = fileName,
                filePath = destFile.absolutePath,
                version = modInfo.version,
                description = modInfo.description,
                authors = modInfo.authors,
                modLoader = profile.modLoader,
                gameVersion = modInfo.gameVersion,
                isEnabled = true,
                fileSize = destFile.length()
            )

            // Save to database
            modRepository.addMod(mod)

            Timber.d("Imported mod: ${mod.name}")
            Result.success(mod)

        } catch (e: Exception) {
            Timber.e(e, "Failed to import mod")
            Result.failure(e)
        }
    }

    suspend fun toggleMod(mod: Mod) = withContext(Dispatchers.IO) {
        val file = File(mod.filePath)
        if (!file.exists()) {
            throw Exception("Mod file not found")
        }

        val newEnabled = !mod.isEnabled
        val newFileName = if (newEnabled) {
            mod.fileName.removeSuffix(".disabled")
        } else {
            "${mod.fileName}.disabled"
        }

        val newFile = File(file.parentFile, newFileName)
        
        if (file.renameTo(newFile)) {
            modRepository.setModEnabled(mod.id, newEnabled)
            Timber.d("Toggled mod ${mod.name} to ${if (newEnabled) "enabled" else "disabled"}")
        } else {
            throw Exception("Failed to rename mod file")
        }
    }

    suspend fun deleteMod(mod: Mod) = withContext(Dispatchers.IO) {
        // Delete file
        val file = File(mod.filePath)
        if (file.exists()) {
            file.delete()
        }

        // Also try with .disabled extension
        val disabledFile = File("${mod.filePath}.disabled")
        if (disabledFile.exists()) {
            disabledFile.delete()
        }

        // Remove from database
        modRepository.deleteMod(mod.id)
        Timber.d("Deleted mod: ${mod.name}")
    }

    suspend fun copyModToProfile(mod: Mod, targetProfile: Profile): Result<Mod> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(mod.filePath)
            if (!sourceFile.exists()) {
                return@withContext Result.failure(Exception("Source mod file not found"))
            }

            val targetModsDir = getModsDirectory(targetProfile)
            targetModsDir.mkdirs()

            val targetFile = File(targetModsDir, mod.fileName)
            
            sourceFile.copyTo(targetFile, overwrite = true)

            val newMod = mod.copy(
                id = UUID.randomUUID().toString(),
                profileId = targetProfile.id,
                filePath = targetFile.absolutePath,
                addedAt = System.currentTimeMillis()
            )

            modRepository.addMod(newMod)

            Result.success(newMod)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateMod(mod: Mod, newFileUri: Uri): Result<Mod> = withContext(Dispatchers.IO) {
        try {
            val oldFile = File(mod.filePath)
            
            // Read new file
            val newContent = context.contentResolver.openInputStream(newFileUri)?.use { 
                it.readBytes() 
            } ?: return@withContext Result.failure(Exception("Failed to read new mod file"))

            // Write to destination
            oldFile.writeBytes(newContent)

            // Re-parse mod info
            val profile = profileRepository.getProfileById(mod.profileId)
                ?: return@withContext Result.failure(Exception("Profile not found"))
            
            val modInfo = parseModFile(oldFile, profile.modLoader)

            val updatedMod = mod.copy(
                name = modInfo.name ?: mod.name,
                version = modInfo.version,
                description = modInfo.description,
                authors = modInfo.authors,
                fileSize = oldFile.length()
            )

            modRepository.updateMod(updatedMod)

            Result.success(updatedMod)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun scanAndSyncMods(profile: Profile): Int = withContext(Dispatchers.IO) {
        val modsDir = getModsDirectory(profile)
        if (!modsDir.exists()) {
            modsDir.mkdirs()
            return@withContext 0
        }

        var addedCount = 0

        // Get all mod files in directory
        val modFiles = modsDir.listFiles()?.filter { 
            it.isFile && (it.extension == "jar" || it.extension == "zip" || it.name.endsWith(".jar.disabled"))
        } ?: emptyList()

        // Check for new mods
        modFiles.forEach { file ->
            val fileName = file.name.removeSuffix(".disabled")
            val exists = modRepository.modExists(fileName, profile.id)
            
            if (!exists) {
                val modInfo = parseModFile(file, profile.modLoader)
                val mod = Mod(
                    profileId = profile.id,
                    name = modInfo.name ?: file.nameWithoutExtension.removeSuffix(".disabled"),
                    fileName = fileName,
                    filePath = file.absolutePath,
                    version = modInfo.version,
                    description = modInfo.description,
                    authors = modInfo.authors,
                    modLoader = profile.modLoader,
                    gameVersion = modInfo.gameVersion,
                    isEnabled = !file.name.endsWith(".disabled"),
                    fileSize = file.length()
                )
                modRepository.addMod(mod)
                addedCount++
            }
        }

        Timber.d("Scanned mods for profile ${profile.name}: found $addedCount new mods")
        addedCount
    }

    private fun getModsDirectory(profile: Profile): File {
        return profile.gameDirectory?.let { File(it, "mods") }
            ?: File(MCLauncherApp.gameDirectory, "mods")
    }

    private fun getFileName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            if (nameIndex >= 0) cursor.getString(nameIndex) else null
        }
    }

    private fun parseModFile(file: File, modLoader: ModLoader): ParsedModInfo {
        return try {
            java.util.zip.ZipFile(file).use { zip ->
                when (modLoader) {
                    ModLoader.FABRIC, ModLoader.QUILT -> parseFabricMod(zip)
                    ModLoader.FORGE, ModLoader.NEOFORGE -> parseForgeMod(zip)
                    else -> ParsedModInfo()
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse mod: ${file.name}")
            ParsedModInfo()
        }
    }

    private fun parseFabricMod(zip: java.util.zip.ZipFile): ParsedModInfo {
        val entry = zip.getEntry("fabric.mod.json") ?: return ParsedModInfo()
        
        return try {
            val json = zip.getInputStream(entry).bufferedReader().readText()
            val obj = org.json.JSONObject(json)
            
            ParsedModInfo(
                name = obj.optString("name").takeIf { it.isNotEmpty() },
                version = obj.optString("version").takeIf { it.isNotEmpty() },
                description = obj.optString("description").takeIf { it.isNotEmpty() },
                authors = obj.optJSONArray("authors")?.let { arr ->
                    (0 until arr.length()).mapNotNull {
                        when (val item = arr.get(it)) {
                            is String -> item
                            is org.json.JSONObject -> item.optString("name")
                            else -> null
                        }
                    }
                } ?: emptyList(),
                gameVersion = obj.optJSONObject("depends")?.optString("minecraft")
            )
        } catch (e: Exception) {
            ParsedModInfo()
        }
    }

    private fun parseForgeMod(zip: java.util.zip.ZipFile): ParsedModInfo {
        // Try mods.toml first
        zip.getEntry("META-INF/mods.toml")?.let { entry ->
            val content = zip.getInputStream(entry).bufferedReader().readText()
            return ParsedModInfo(
                name = Regex("""displayName\s*=\s*"([^"]+)"""").find(content)?.groupValues?.get(1),
                version = Regex("""version\s*=\s*"([^"]+)"""").find(content)?.groupValues?.get(1),
                description = Regex("""description\s*=\s*'''([^']+)'''""").find(content)?.groupValues?.get(1)
                    ?: Regex("""description\s*=\s*"([^"]+)"""").find(content)?.groupValues?.get(1),
                authors = Regex("""authors\s*=\s*"([^"]+)"""").find(content)?.groupValues?.get(1)
                    ?.split(",")?.map { it.trim() } ?: emptyList()
            )
        }

        // Try mcmod.info
        zip.getEntry("mcmod.info")?.let { entry ->
            return try {
                val json = zip.getInputStream(entry).bufferedReader().readText()
                val arr = org.json.JSONArray(json)
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    ParsedModInfo(
                        name = obj.optString("name").takeIf { it.isNotEmpty() },
                        version = obj.optString("version").takeIf { it.isNotEmpty() },
                        description = obj.optString("description").takeIf { it.isNotEmpty() },
                        authors = obj.optJSONArray("authorList")?.let { authorArr ->
                            (0 until authorArr.length()).map { authorArr.getString(it) }
                        } ?: emptyList(),
                        gameVersion = obj.optString("mcversion").takeIf { it.isNotEmpty() }
                    )
                } else {
                    ParsedModInfo()
                }
            } catch (e: Exception) {
                ParsedModInfo()
            }
        }

        return ParsedModInfo()
    }
}

private data class ParsedModInfo(
    val name: String? = null,
    val version: String? = null,
    val description: String? = null,
    val authors: List<String> = emptyList(),
    val gameVersion: String? = null
)
