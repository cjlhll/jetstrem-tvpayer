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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.google.jetstream.presentation.screens.dashboard.rememberChildPadding
import com.google.jetstream.presentation.theme.JetStreamButtonShape
import com.google.jetstream.tvmaterial.StandardDialog
import kotlinx.coroutines.launch

/**
 * 季选择器数据模型
 */
data class Season(
    val number: Int,
    val displayName: String,
    val episodeCount: Int = 0
)

/**
 * 电视剧季选择器组件
 * 
 * @param seasons 可用的季列表（从WebDAV检测得出）
 * @param selectedSeason 当前选中的季
 * @param onSeasonSelected 季选择回调
 * @param modifier Modifier
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun SeasonSelector(
    seasons: List<Season>,
    selectedSeason: Season?,
    onSeasonSelected: (Season) -> Unit,
    modifier: Modifier = Modifier
) {
    // 移除空列表检查，始终显示季选择器
    
    var showDialog by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val childPadding = rememberChildPadding()
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = childPadding.start)
            .padding(bottom = 24.dp)
    ) {
        // 季选择按钮
        Button(
            onClick = { showDialog = true },
            modifier = Modifier
                .focusRequester(focusRequester)
                .wrapContentWidth(),
            shape = ButtonDefaults.shape(shape = JetStreamButtonShape),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            colors = ButtonDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.primary,
                focusedContentColor = MaterialTheme.colorScheme.onPrimary
            ),
            border = ButtonDefaults.border(
                border = Border(
                    border = BorderStroke(1.dp, Color.Transparent),
                    shape = JetStreamButtonShape
                ),
                focusedBorder = Border(
                    border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                    shape = JetStreamButtonShape
                )
            ),
            scale = ButtonDefaults.scale(focusedScale = 1.0f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = selectedSeason?.displayName ?: "选择季",
                    style = MaterialTheme.typography.bodyMedium
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "展开选项",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
    
    // 季选择对话框
    if (showDialog) {
        SeasonSelectionDialog(
            seasons = seasons,
            selectedSeason = selectedSeason,
            onSeasonSelected = { season ->
                onSeasonSelected(season)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * 季选择对话框
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun SeasonSelectionDialog(
    seasons: List<Season>,
    selectedSeason: Season?,
    onSeasonSelected: (Season) -> Unit,
    onDismiss: () -> Unit
) {
    StandardDialog(
        showDialog = true,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "选择季",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .heightIn(min = 120.dp, max = 400.dp)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(seasons) { index, season ->
                    val isSelected = season == selectedSeason
                    val focusRequester = remember { FocusRequester() }
                    
                    LaunchedEffect(Unit) {
                        if (isSelected && index == 0) {
                            focusRequester.requestFocus()
                        }
                    }
                    
                    ListItem(
                        selected = isSelected,
                        onClick = { onSeasonSelected(season) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        headlineContent = {
                            Text(
                                text = season.displayName,
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        supportingContent = if (season.episodeCount > 0) {
                            {
                                Text(
                                    text = "${season.episodeCount}集",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else null,
                        trailingContent = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "已选中"
                                )
                            }
                        } else null,
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                        ),
                        shape = ListItemDefaults.shape(shape = RoundedCornerShape(12.dp))
                    )
                }
            }
        },
        confirmButton = {
            // 去掉关闭按钮，使用遥控器返回键关闭
        }
    )
}