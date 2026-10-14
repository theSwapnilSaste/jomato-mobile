package com.application.jomato.config

import android.content.Context
import com.application.jomato.utils.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * Singleton that loads bundled UI config and runs optional integrity checks.
 * Use [fetch] then [checkIntegrity] when the bundled config provides integrity metadata.
 */
object UiConfigManager {

    private const val TAG = "UiConfigManager"
    private val json = Json { ignoreUnknownKeys = true }

    var config: UiConfig? = null
        private set

    suspend fun fetch(context: Context) {
        try {
            val body = withContext(Dispatchers.IO) {
                context.assets.open("ui.json").bufferedReader().use { it.readText() }
            }
            val loadedConfig = json.decodeFromString<UiConfig>(body)
            config = loadedConfig
            EntityRegistry.updateFrom(loadedConfig)
            FileLogger.log(context, TAG, "Loaded bundled ui.json")
        } catch (e: Exception) {
            config = null
            EntityRegistry.updateFrom(null)
            FileLogger.log(context, TAG, "Failed to load bundled ui.json | ${e.message}", e)
        }
    }

    /**
     * Runs integrity check using the installed APK's SHA-256 digest (computed here).
     * Call after [fetch]; uses [config] from the last successful fetch.
     */
    fun checkIntegrity(context: Context): IntegrityResult {
        val integrity = config?.integrity ?: run {
            FileLogger.log(context, TAG, "No integrity metadata, skipping integrity check")
            return IntegrityResult.Unknown
        }

        val installedDigest = installedApkDigest(context) ?: run {
            FileLogger.log(context, TAG, "Could not compute APK digest, skipping integrity check")
            return IntegrityResult.Unknown
        }

        val match = integrity.versions.any { it.digest.equals(installedDigest, ignoreCase = true) }
        return if (match) {
            FileLogger.log(context, TAG, "Integrity check passed for digest: $installedDigest")
            IntegrityResult.Pass
        } else {
            FileLogger.log(context, TAG, "Integrity check FAILED for digest: $installedDigest")
            IntegrityResult.Fail(
                message = integrity.onMismatch.message,
                strict = integrity.onMismatch.strict
            )
        }
    }

    fun getInstalledApkDigest(context: Context): String? = installedApkDigest(context)

    private fun installedApkDigest(context: Context): String? = try {
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        val apkFile = File(appInfo.sourceDir)
        val digest = MessageDigest.getInstance("SHA-256")
        apkFile.inputStream().use { inputStream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        "sha256:" + digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        FileLogger.log(context, TAG, "Failed to compute APK digest: ${e.message}", e)
        null
    }
}

sealed class IntegrityResult {
    object Pass : IntegrityResult()
    object Unknown : IntegrityResult()
    data class Fail(val message: String, val strict: Boolean) : IntegrityResult()
}