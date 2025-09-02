/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.jetstream.presentation.screens.movies

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.jetstream.R
import com.google.jetstream.data.entities.MovieDetails
import com.google.jetstream.data.util.StringConstants
import com.google.jetstream.presentation.screens.dashboard.rememberChildPadding
import com.google.jetstream.presentation.theme.JetStreamButtonShape
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MovieDetails(
    movieDetails: MovieDetails,
    // 可选：传入文件大小用于展示（由上层ViewModel通过WebDAV查询）
    fileSizeBytes: Long? = null,

    goToMoviePlayer: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    val childPadding = rememberChildPadding()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(432.dp)
            .bringIntoViewRequester(bringIntoViewRequester)
    ) {
        MovieImageWithGradients(
            movieDetails = movieDetails,
            modifier = Modifier.fillMaxSize()
        )

        Column(modifier = Modifier.fillMaxWidth(0.55f)) {
            Spacer(modifier = Modifier.height(108.dp))
            Column(
                modifier = Modifier.padding(start = childPadding.start)
            ) {
                MovieLargeTitle(movieTitle = movieDetails.name)

                Column(
                    modifier = Modifier.alpha(0.75f)
                ) {
                    MovieDescription(description = movieDetails.description)
                    DotSeparatedRow(
                        modifier = Modifier.padding(top = 20.dp),
                        texts = listOf(
                            movieDetails.pgRating,
                            movieDetails.releaseDate,
                            movieDetails.categories.joinToString(", "),
                            movieDetails.duration
                        ).filter { it.isNotBlank() }
                    )
                    DirectorScreenplayMusicRow(
                        director = movieDetails.director,
                        screenplay = movieDetails.screenplay,
                        music = movieDetails.music
                    )
                }
                WatchTrailerButton(
                    modifier = Modifier.onFocusChanged {
                        if (it.isFocused) {
                            coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                        }
                    },
                    goToMoviePlayer = goToMoviePlayer,
                    focusRequester = focusRequester
                )
            }
        }
    }
}

@Composable
private fun WatchTrailerButton(
    modifier: Modifier = Modifier,
    goToMoviePlayer: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    Button(
        onClick = goToMoviePlayer,
        modifier = modifier
            .padding(top = 24.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        shape = ButtonDefaults.shape(shape = JetStreamButtonShape)
    ) {
        Icon(
            imageVector = Icons.Outlined.PlayArrow,
            contentDescription = null
        )
        Spacer(Modifier.size(8.dp))
        // 文案改为“播放”，但保持按钮原有宽度：用一段不可见的原文案占位
        Box {
            Text(
                text = "播放",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.watch_trailer),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.alpha(0f)
            )
        }
    }
}

@Composable
private fun DirectorScreenplayMusicRow(
    director: String,
    screenplay: String,
    music: String
) {
    // 只显示有值的字段
    val fields = listOfNotNull(
        if (director.isNotBlank()) Pair(stringResource(R.string.director), director) else null,
        if (screenplay.isNotBlank()) Pair(stringResource(R.string.screenplay), screenplay) else null,
        if (music.isNotBlank()) Pair(stringResource(R.string.music), music) else null
    )
    
    if (fields.isNotEmpty()) {
        Row(modifier = Modifier.padding(top = 32.dp)) {
            fields.forEachIndexed { index, (title, value) ->
                TitleValueText(
                    modifier = Modifier
                        .then(if (index < fields.lastIndex) Modifier.padding(end = 32.dp) else Modifier)
                        .weight(1f),
                    title = title,
                    value = value
                )
            }
            // 填充剩余空间，保持布局平衡
            repeat(3 - fields.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MovieDescription(description: String) {
    Text(
        text = description,
        style = MaterialTheme.typography.titleSmall.copy(
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal
        ),
        modifier = Modifier.padding(top = 8.dp),
        maxLines = 2
    )
}

@Composable
private fun MovieLargeTitle(movieTitle: String) {
    Text(
        text = movieTitle,
        style = MaterialTheme.typography.displayMedium.copy(
            fontWeight = FontWeight.Bold
        ),
        maxLines = 1
    )
}

@Composable
private fun MovieImageWithGradients(
    movieDetails: MovieDetails,
    modifier: Modifier = Modifier,
    gradientColor: Color = MaterialTheme.colorScheme.surface,
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(movieDetails.backdropUri)
            .crossfade(true).build(),
        contentDescription = StringConstants
            .Composable
            .ContentDescription
            .moviePoster(movieDetails.name),
        contentScale = ContentScale.Crop,
        modifier = modifier.drawWithContent {
            drawContent()
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, gradientColor),
                    startY = 600f
                )
            )
            drawRect(
                Brush.horizontalGradient(
                    colors = listOf(gradientColor, Color.Transparent),
                    endX = 1000f,
                    startX = 300f
                )
            )
            drawRect(
                Brush.linearGradient(
                    colors = listOf(gradientColor, Color.Transparent),
                    start = Offset(x = 500f, y = 500f),
                    end = Offset(x = 1000f, y = 0f)
                )
            )
        }
    )

}

@Composable
fun SourceInfoAndSpecs(movieDetails: MovieDetails, fileSizeBytes: Long? = null) {
    val childPadding = rememberChildPadding()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = childPadding.start)
            .alpha(0.85f)
            .focusable(false)
    ) {
        val path = movieDetails.videoUri
        // 1) 文件名（带后缀）
        val (fileName, dirText, specs, durationZh) = remember(path, movieDetails.duration) {
            val uri = try { android.net.Uri.parse(path) } catch (_: Throwable) { null }
            val rawFile = (uri?.lastPathSegment ?: path.substringAfterLast('/')).ifBlank { movieDetails.name }
            val decodedFile = try { java.net.URLDecoder.decode(rawFile, "UTF-8") } catch (_: Throwable) { rawFile }
            val parentPath = try {
                val segs = uri?.pathSegments ?: emptyList()
                if (segs.isNotEmpty()) segs.dropLast(1).joinToString("/") else path.substringBeforeLast('/', "")
            } catch (_: Throwable) { path.substringBeforeLast('/', "") }
            val hostOrPrefix = when (uri?.scheme?.lowercase()) {
                "http", "https" -> "WebDAV: ${uri.host ?: ""}"
                "file" -> "本地:"
                else -> ""
            }
            val dir = listOf(hostOrPrefix, parentPath.trim('/')).filter { it.isNotBlank() }.joinToString(" / ")
            val specsText = guessSpecs(path)
            val zhDuration = toZhDuration(movieDetails.duration)
            Quad(decodedFile, dir, specsText, zhDuration)
        }

        Text(text = fileName, style = MaterialTheme.typography.titleSmall)
        if (dirText.isNotBlank()) {
            Text(
                text = dirText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        // 第三行：时长 + 规格（若有）
        val sizeText = fileSizeBytes?.takeIf { it > 0 }?.let { humanReadableBytes(it) } ?: ""
        // 顺序调整为：时长  文件大小  分辨率规格
        val thirdLine = listOf(durationZh, sizeText, specs).filter { it.isNotBlank() }.joinToString("  ")
        if (thirdLine.isNotBlank()) {
            Text(
                text = thirdLine,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private fun guessSpecs(input: String): String {
    val s = input.lowercase()
    return when {
        // 扩充更多关键词
        "2160" in s || "4k" in s || "uhd" in s || "3840x2160" in s || "2160p" in s -> "4K"
        "1440" in s || "2k" in s || "2560x1440" in s || "1440p" in s -> "2K"
        "1080" in s || "fhd" in s || "fullhd" in s || "1920x1080" in s || "1080p" in s -> "1080P"
        "720" in s || "1280x720" in s || "720p" in s -> "720P"
        "480" in s || "720x480" in s || "480p" in s -> "480P"
        "360" in s || "640x360" in s || "360p" in s -> "360P"
        else -> ""
    }
}

private fun toZhDuration(text: String): String {
    // 将 "1h 58m" / "137 mins" 等尽量转为 "1 小时 58 分钟"
    val t = text.trim()
    val h = Regex("(\\d+)h").find(t)?.groupValues?.getOrNull(1)?.toIntOrNull()
    val m = Regex("(\\d+)m").find(t)?.groupValues?.getOrNull(1)?.toIntOrNull()
    return when {
        h != null && m != null -> "${h} 小时 ${m} 分钟"
        h != null -> "${h} 小时"
        m != null -> "${m} 分钟"
        else -> t
    }
}

private fun humanReadableBytes(bytes: Long): String {
    if (bytes <= 0) return ""
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return String.format(java.util.Locale.US, "%.2f %s", v, units[i])
}

private data class Quad<A,B,C,D>(val first: A, val second: B, val third: C, val fourth: D)
