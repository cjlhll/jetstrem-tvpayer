package com.google.jetstream.data.subs

import androidx.media3.common.MimeTypes

object SubtitleMime {
    fun fromUrl(url: String): String? {
        val lower = url.substringBefore('?').lowercase()
        return when {
            lower.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            lower.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
            lower.endsWith(".ass") || lower.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            lower.endsWith(".ttml") || lower.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
            else -> null
        }
    }
}
