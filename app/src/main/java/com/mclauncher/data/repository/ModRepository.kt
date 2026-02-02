package com.mclauncher.data.repository

import com.mclauncher.data.local.database.dao.ModDao
import com.mclauncher.data.local.database.entities.ModEntity
import com.mclauncher.data.remote.api.FabricApi
import com.mclauncher.data.remote.api.ForgeApi
import com.mclauncher.domain.models.Mod
import com.mclauncher.domain.models.ModLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModRepository @Inject constructor(
    private val modDao: ModDao,
    private val profileRepository: ProfileRepository,
    private val fabricApi: FabricApi,
    private val forgeApi: ForgeApi
) {

    fun getAllMods(): Flow<List<Mod>> {
        return modDao.getAllMods().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    fun getModsByProfile(profileId: String): Flow<List<Mod>> {
        return modDao.getModsByProfile(profileId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    fun getEnabledModsByProfile(profileId: String): Flow<List<Mod>> {
        return modDao.getEnabledModsByProfile(profileId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun getModById(id: String): Mod? {
        return modDao.getModById(id)?.toDomainModel()
    }

    fun searchMods(profileId: String, query: String): Flow<List<Mod>> {
        return modDao.searchModsInProfile(profileId, "%$query%").map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    suspend fun addMod(mod: Mod) {
        modDao.insertMod(mod.toEntity())
        Timber.d("Added mod: ${mod.name}")
    }

    suspend fun updateMod(mod: Mod) {
        modDao.updateMod(mod.toEntity())
    }

    suspend fun setModEnabled(modId: String, enabled: Boolean) {
        modDao.setModEnabled(modId, enabled)
    }

    suspend fun toggleMod(modId: String) {
        modDao.toggleMod(modId)
    }

    suspend fun enableAllMods(profileId: String) {
        modDao.enableAllMods(profileId)
    }

    suspend fun disableAllMods(profileId: String) {
        modDao.disableAllMods(profileId)
    }

    suspend fun deleteMod(modId: String) {
        modDao.deleteModById(modId)
    }

    suspend fun deleteModsByProfile(profileId: String) {
        modDao.deleteModsByProfile(profileId)
    }

    suspend fun getModCountForProfile(profileId: String): Int {
        return modDao.getModCountForProfile(profileId)
    }

    suspend fun modExists(fileName: String, profileId: String): Boolean {
        return modDao.modExists(fileName, profileId)
    }

    suspend fun copyModsToProfile(sourceProfileId: String, targetProfileId: String) {
        modDao.copyModsToProfile(sourceProfileId, targetProfileId)
    }

    suspend fun scanModsDirectory(profileId: String): List<Mod> = withContext(Dispatchers.IO) {
        val profile = profileRepository.getProfileById(profileId)
            ?: return@withContext emptyList()

        val modsDir = profile.gameDirectory?.let { File(it, "mods") }
            ?: return@withContext emptyList()

        if (!modsDir.exists()) {
            return@withContext emptyList()
        }

        val scannedMods = mutableListOf<Mod>()

        modsDir.listFiles()
            ?.filter { it.isFile && (it.extension == "jar" || it.extension == "zip") }
            ?.forEach { file ->
                // Check if already in database
                if (!modExists(file.name, profileId)) {
                    val modInfo = parseModInfo(file, profile.modLoader)
                    val mod = Mod(
                        profileId = profileId,
                        name = modInfo.name ?: file.nameWithoutExtension,
                        fileName = file.name,
                        filePath = file.absolutePath,
                        version = modInfo.version,
                        description = modInfo.description,
                        authors = modInfo.authors,
                        modLoader = profile.modLoader,
                        gameVersion = modInfo.gameVersion,
                        isEnabled = !file.name.endsWith(".disabled"),
                        fileSize = file.length()
                    )
                    addMod(mod)
                    scannedMods.add(mod)
                }
            }

        scannedMods
    }

    private fun parseModInfo(file: File, modLoader: ModLoader): ModInfo {
        return try {
            java.util.zip.ZipFile(file).use { zip ->
                when (modLoader) {
                    ModLoader.FABRIC, ModLoader.QUILT -> parseFabricMod(zip)
                    ModLoader.FORGE, ModLoader.NEOFORGE -> parseForgeMod(zip)
                    else -> ModInfo()
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse mod info for ${file.name}")
            ModInfo()
        }
    }

    private fun parseFabricMod(zip: java.util.zip.ZipFile): ModInfo {
        val entry = zip.getEntry("fabric.mod.json") ?: return ModInfo()
        
        return try {
            val json = zip.getInputStream(entry).bufferedReader().readText()
            val jsonObject = org.json.JSONObject(json)
            
            ModInfo(
                name = jsonObject.optString("name"),
                version = jsonObject.optString("version"),
                description = jsonObject.optString("description"),
                authors = jsonObject.optJSONArray("authors")?.let { arr ->
                    (0 until arr.length()).mapNotNull { 
                        val item = arr.get(it)
                        when (item) {
                            is String -> item
                            is org.json.JSONObject -> item.optString("name")
                            else -> null
                        }
                    }
                } ?: emptyList(),
                gameVersion = jsonObject.optJSONObject("depends")?.optString("minecraft")
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse fabric.mod.json")
            ModInfo()
        }
    }

    private fun parseForgeMod(zip: java.util.zip.ZipFile): ModInfo {
        // Try mods.toml (modern Forge)
        val modsToml = zip.getEntry("META-INF/mods.toml")
        if (modsToml != null) {
            return parseModsToml(zip, modsToml)
        }

        // Try mcmod.info (legacy Forge)
        val mcmodInfo = zip.getEntry("mcmod.info")
        if (mcmodInfo != null) {
            return parseMcmodInfo(zip, mcmodInfo)
        }

        return ModInfo()
    }

    private fun parseModsToml(zip: java.util.zip.ZipFile, entry: java.util.zip.ZipEntry): ModInfo {
        return try {
            val content = zip.getInputStream(entry).bufferedReader().readText()
            
            // Simple TOML parsing for common fields
            val name = Regex("""displayName\s*=\s*"([^"]+)"""").find(content)?.groupValues?.get(1)
            val version = Regex("""version\s*=\s*"([^"]+)"""").find(content)?.groupValues?.get(1)
            val description = Regex("""description\s*=\s*'''([^']+)'''""").find(content)?.groupValues?.get(1)
                ?: Regex("""description\s*=\s*"([^"]+)"""").find(content)?.groupValues?.get(1)
            val authors = Regex("""authors\s*=\s*"([^"]+)"""").find(content)?.groupValues?.get(1)

            ModInfo(
                name = name,
                version = version,
                description = description?.trim(),
                authors = authors?.split(",")?.map { it.trim() } ?: emptyList()
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse mods.toml")
            ModInfo()
        }
    }

    private fun parseMcmodInfo(zip: java.util.zip.ZipFile, entry: java.util.zip.ZipEntry): ModInfo {
        return try {
            val json = zip.getInputStream(entry).bufferedReader().readText()
            val jsonArray = org.json.JSONArray(json)
            
            if (jsonArray.length() > 0) {
                val mod = jsonArray.getJSONObject(0)
                ModInfo(
                    name = mod.optString("name"),
                    version = mod.optString("version"),
                    description = mod.optString("description"),
                    authors = mod.optJSONArray("authorList")?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    } ?: emptyList(),
                    gameVersion = mod.optString("mcversion")
                )
            } else {
                ModInfo()
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse mcmod.info")
            ModInfo()
        }
    }

    private fun ModEntity.toDomainModel(): Mod {
        return Mod(
            id = id,
            profileId = profileId,
            name = name,
            fileName = fileName,
            filePath = filePath,
            version = version,
            description = description,
            authors = authors?.split(",")?.map { it.trim() } ?: emptyList(),
            modLoader = ModLoader.fromString(modLoader),
            gameVersion = gameVersion,
            isEnabled = isEnabled,
            iconPath = iconPath,
            downloadUrl = downloadUrl,
            sha1 = sha1,
            fileSize = fileSize,
            addedAt = addedAt
        )
    }

    private fun Mod.toEntity(): ModEntity {
        return ModEntity(
            id = id,
            profileId = profileId,
            name = name,
            fileName = fileName,
            filePath = filePath,
            version = version,
            description = description,
            authors = authors.joinToString(","),
            modLoader = modLoader.name.lowercase(),
            gameVersion = gameVersion,
            isEnabled = isEnabled,
            iconPath = iconPath,
            downloadUrl = downloadUrl,
            sha1 = sha1,
            fileSize = fileSize,
            addedAt = addedAt
        )
    }
}

private data class ModInfo(
    val name: String? = null,
    val version: String? = null,
    val description: String? = null,
    val authors: List<String> = emptyList(),
    val gameVersion: String? = null
)
