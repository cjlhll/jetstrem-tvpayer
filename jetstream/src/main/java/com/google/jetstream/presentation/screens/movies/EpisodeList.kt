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

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.google.jetstream.data.entities.Episode
import com.google.jetstream.presentation.screens.dashboard.rememberChildPadding

/**
 * 剧集列表组件
 * 
 * @param modifier Modifier
 * @param episodeList 剧集列表
 * @param startPadding 开始边距
 * @param endPadding 结束边距
 * @param title 标题
 * @param titleStyle 标题样式
 * @param onEpisodeClick 剧集点击回调
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EpisodeList(
    modifier: Modifier = Modifier,
    episodeList: List<Episode>,
    startPadding: Dp = rememberChildPadding().start,
    endPadding: Dp = rememberChildPadding().end,
    title: String? = null,
    titleStyle: TextStyle = MaterialTheme.typography.headlineLarge.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 30.sp
    ),
    onEpisodeClick: (Episode) -> Unit
) {
    AnimatedContent(
        targetState = episodeList,
        modifier = modifier,
        label = ""
    ) { episodes ->
        Column {
            // 移除标题显示，只显示剧集列表
            if (episodes.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRestorer(),
                    contentPadding = PaddingValues(
                        start = startPadding,
                        end = endPadding
                    ),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(episodes) { episode ->
                        EpisodeCard(
                            episode = episode,
                            itemWidth = 180.dp, // 与最近观看卡片相同的宽度
                            onEpisodeClick = onEpisodeClick
                        )
                    }
                }
            } else {
                // 空状态
                Text(
                    text = "暂无剧集信息",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(
                        start = startPadding,
                        bottom = 16.dp
                    )
                )
            }
        }
    }
}