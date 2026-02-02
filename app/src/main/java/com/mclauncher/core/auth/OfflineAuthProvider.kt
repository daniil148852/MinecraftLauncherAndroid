package com.mclauncher.core.auth

import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineAuthProvider @Inject constructor() {

    companion object {
        private const val MIN_USERNAME_LENGTH = 3
        private const val MAX_USERNAME_LENGTH = 16
        private val USERNAME_REGEX = Regex("^[a-zA-Z0-9_]+$")
        
        // Reserved/banned usernames
        private val RESERVED_USERNAMES = setOf(
            "admin", "administrator", "mod", "moderator", "owner",
            "server", "console", "system", "root", "null", "undefined",
            "minecraft", "mojang", "notch", "jeb", "dinnerbone"
        )
    }

    fun validateUsername(username: String): Result<String> {
        val trimmed = username.trim()

        return when {
            trimmed.isBlank() -> {
                Result.failure(UsernameValidationException("Username cannot be empty"))
            }
            trimmed.length < MIN_USERNAME_LENGTH -> {
                Result.failure(UsernameValidationException(
                    "Username must be at least $MIN_USERNAME_LENGTH characters"
                ))
            }
            trimmed.length > MAX_USERNAME_LENGTH -> {
                Result.failure(UsernameValidationException(
                    "Username cannot exceed $MAX_USERNAME_LENGTH characters"
                ))
            }
            !trimmed.matches(USERNAME_REGEX) -> {
                Result.failure(UsernameValidationException(
                    "Username can only contain letters, numbers, and underscores"
                ))
            }
            trimmed.lowercase() in RESERVED_USERNAMES -> {
                Result.failure(UsernameValidationException(
                    "This username is reserved and cannot be used"
                ))
            }
            trimmed.startsWith("_") || trimmed.endsWith("_") -> {
                Result.failure(UsernameValidationException(
                    "Username cannot start or end with an underscore"
                ))
            }
            trimmed.contains("__") -> {
                Result.failure(UsernameValidationException(
                    "Username cannot contain consecutive underscores"
                ))
            }
            else -> Result.success(trimmed)
        }
    }

    /**
     * Generates a consistent offline UUID for a given username.
     * This matches Minecraft's offline UUID generation algorithm.
     */
    fun generateOfflineUUID(username: String): String {
        val data = "OfflinePlayer:$username".toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("MD5")
        val hash = md.digest(data)

        // Set version to 3 (name-based MD5 hash)
        hash[6] = (hash[6].toInt() and 0x0f or 0x30).toByte()
        // Set variant to IETF
        hash[8] = (hash[8].toInt() and 0x3f or 0x80).toByte()

        return buildString {
            for (i in hash.indices) {
                append(String.format("%02x", hash[i]))
                if (i == 3 || i == 5 || i == 7 || i == 9) {
                    append('-')
                }
            }
        }
    }

    /**
     * Generates a random UUID for cases where we don't need consistency.
     */
    fun generateRandomUUID(): String {
        return UUID.randomUUID().toString()
    }

    /**
     * Creates an offline access token.
     * For offline mode, this is just a placeholder that won't be validated.
     */
    fun generateOfflineAccessToken(): String {
        return "offline_${UUID.randomUUID().toString().replace("-", "")}"
    }

    /**
     * Validates if a UUID is in the correct format.
     */
    fun isValidUUID(uuid: String): Boolean {
        return try {
            UUID.fromString(uuid)
            true
        } catch (e: IllegalArgumentException) {
            // Try without dashes
            if (uuid.length == 32) {
                try {
                    val formatted = "${uuid.substring(0, 8)}-${uuid.substring(8, 12)}-" +
                            "${uuid.substring(12, 16)}-${uuid.substring(16, 20)}-${uuid.substring(20)}"
                    UUID.fromString(formatted)
                    true
                } catch (e: IllegalArgumentException) {
                    false
                }
            } else {
                false
            }
        }
    }

    /**
     * Formats a UUID with dashes if it doesn't have them.
     */
    fun formatUUID(uuid: String): String {
        val clean = uuid.replace("-", "")
        return if (clean.length == 32) {
            "${clean.substring(0, 8)}-${clean.substring(8, 12)}-" +
                    "${clean.substring(12, 16)}-${clean.substring(16, 20)}-${clean.substring(20)}"
        } else {
            uuid
        }
    }

    /**
     * Returns UUID without dashes (trimmed format used by Mojang in some APIs).
     */
    fun trimUUID(uuid: String): String {
        return uuid.replace("-", "")
    }
}

class UsernameValidationException(message: String) : Exception(message)
