package com.livora.corbett.util

import com.livora.corbett.BuildConfig

/**
 * Absolute URLs are used as-is; site-relative paths ("/photos/...") are prefixed with the website
 * origin (assetBaseUrl from /settings/public, falling back to BuildConfig.SITE_URL).
 */
fun resolveImageUrl(url: String?, assetBase: String?): String? {
    if (url.isNullOrBlank()) return null
    if (url.startsWith("http://") || url.startsWith("https://")) return url
    val base = (assetBase?.takeIf { it.isNotBlank() } ?: BuildConfig.SITE_URL).trimEnd('/')
    return if (url.startsWith("/")) base + url else "$base/$url"
}
