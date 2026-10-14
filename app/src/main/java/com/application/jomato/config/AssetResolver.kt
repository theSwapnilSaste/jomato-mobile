package com.application.jomato.config

/**
 * Resolves relative asset paths to files packaged in the app's assets directory.
 */
object AssetResolver {

    fun urlPrimary(relativePath: String): String {
        return "file:///android_asset/${relativePath.removePrefix("/")}"
    }

    fun urlFallback(relativePath: String): String {
        return urlPrimary(relativePath)
    }
}
