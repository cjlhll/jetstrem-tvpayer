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

package com.google.jetstream.presentation.screens.dashboard

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerDefaults
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.jetstream.data.entities.Movie
import com.google.jetstream.presentation.screens.Screens
import com.google.jetstream.presentation.screens.home.HomeScreen
import com.google.jetstream.presentation.screens.profile.ProfileScreen
import com.google.jetstream.presentation.utils.Padding
import kotlinx.coroutines.launch

val ParentPadding = PaddingValues(vertical = 16.dp, horizontal = 58.dp)

@Composable
fun rememberChildPadding(direction: LayoutDirection = LocalLayoutDirection.current): Padding {
    return remember {
        Padding(
            start = ParentPadding.calculateStartPadding(direction) + 8.dp,
            top = ParentPadding.calculateTopPadding(),
            end = ParentPadding.calculateEndPadding(direction) + 8.dp,
            bottom = ParentPadding.calculateBottomPadding()
        )
    }
}

@Composable
fun DashboardScreen(
    openCategoryMovieList: (categoryId: String) -> Unit,
    openMovieDetailsScreen: (movieId: String) -> Unit,
    openVideoPlayer: (Movie) -> Unit,
    openMovieTypeList: (movieType: String) -> Unit,
    isComingBackFromDifferentScreen: Boolean,
    resetIsComingBackFromDifferentScreen: () -> Unit,
    onBackPressed: () -> Unit
) {
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val navController = rememberNavController()
    val dashboardViewModel: DashboardViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val isRefreshing by dashboardViewModel.isRefreshing.collectAsState(initial = false)
    
    // Drawer state
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val drawerFocusRequester = remember { FocusRequester() }
    
    // TopBar "我的"图标的 FocusRequester
    val profileFocusRequester = TopBarFocusRequesters[0]

    var isTopBarVisible by remember { mutableStateOf(true) }
    var isTopBarFocused by remember { mutableStateOf(false) }
    
    // 当抽屉状态改变时，管理焦点
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            // 抽屉打开时，请求焦点到抽屉
            drawerFocusRequester.requestFocus()
        } else {
            // 抽屉关闭时，将焦点恢复到"我的"图标
            kotlinx.coroutines.delay(100)
            profileFocusRequester.requestFocus()
        }
    }

    var showExitDialog by remember { mutableStateOf(false) }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("退出应用") },
            text = { Text("您确定要退出应用吗？") },
            confirmButton = {
                Button(onClick = {
                    showExitDialog = false
                    onBackPressed() // Exit the app
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                Button(onClick = { showExitDialog = false }) {
                    Text("取消")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    }

    var currentDestination: String? by remember { mutableStateOf(null) }
    val hasTabs = TopBarTabs.isNotEmpty()
    val currentTopBarSelectedTabIndex by remember(currentDestination, hasTabs) {
        derivedStateOf {
            if (hasTabs) {
                currentDestination?.let { TopBarTabs.indexOf(Screens.valueOf(it)) } ?: 0
            } else {
                -1
            }
        }
    }

    DisposableEffect(Unit) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            currentDestination = destination.route
        }

        navController.addOnDestinationChangedListener(listener)

        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }

    BackPressHandledArea(
        // 1. If drawer is open, close it
        // 2. On user's first back press, bring focus to the current selected tab, if TopBar is not
        //    visible, first make it visible, then focus the selected tab
        // 3. On second back press, bring focus back to the first displayed tab
        // 4. On third back press, exit the app
        onBackPressed = {
            if (drawerState.isOpen) {
                scope.launch { drawerState.close() }
            } else if (!isTopBarVisible) {
                isTopBarVisible = true
                val targetIndex = if (hasTabs) currentTopBarSelectedTabIndex + 1 else 0
                TopBarFocusRequesters[targetIndex].requestFocus()
            } else if (!hasTabs || currentTopBarSelectedTabIndex == 0) {
                showExitDialog = true
            } else if (!isTopBarFocused) {
                TopBarFocusRequesters[currentTopBarSelectedTabIndex + 1].requestFocus()
            } else {
                // Focus first tab when tabs exist, otherwise focus avatar (index 0)
                val firstFocusable = if (hasTabs) 1 else 0
                TopBarFocusRequesters[firstFocusable].requestFocus()
            }
        }
    ) {
        // We do not want to focus the TopBar everytime we come back from another screen e.g.
        // MovieDetails, CategoryMovieList or VideoPlayer screen
        var wasTopBarFocusRequestedBefore by rememberSaveable { mutableStateOf(false) }

        var topBarHeightPx: Int by rememberSaveable { mutableIntStateOf(0) }

        // Used to show/hide DashboardTopBar
        val topBarYOffsetPx by animateIntAsState(
            targetValue = if (isTopBarVisible) 0 else -topBarHeightPx,
            animationSpec = tween(),
            label = "",
            finishedListener = {
                if (it == -topBarHeightPx && isComingBackFromDifferentScreen) {
                    focusManager.moveFocus(FocusDirection.Down)
                    resetIsComingBackFromDifferentScreen()
                }
            }
        )

        // Used to push down/pull up NavHost when DashboardTopBar is shown/hidden
        val navHostTopPaddingDp by animateDpAsState(
            targetValue = if (isTopBarVisible) with(density) { topBarHeightPx.toDp() } else 0.dp,
            animationSpec = tween(),
            label = "",
        )

        // 不再自动聚焦到 TopBar，让焦点保持在内容区域
        // 首页会自动将焦点设置到第一个内容项
        LaunchedEffect(Unit) {
            wasTopBarFocusRequestedBefore = true
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(400.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .focusRequester(drawerFocusRequester)
                ) {
                    DrawerContent()
                }
            },
            scrimColor = androidx.compose.material3.MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)
        ) {
            Box {
                DashboardTopBar(
                    modifier = Modifier
                        .offset { IntOffset(x = 0, y = topBarYOffsetPx) }
                        .onSizeChanged { topBarHeightPx = it.height }
                        .onFocusChanged { isTopBarFocused = it.hasFocus }
                        .padding(
                            horizontal = ParentPadding.calculateStartPadding(
                                LocalLayoutDirection.current
                            ) + 8.dp
                        )
                        .padding(
                            top = ParentPadding.calculateTopPadding(),
                            bottom = ParentPadding.calculateBottomPadding()
                        ),
                    selectedTabIndex = currentTopBarSelectedTabIndex,
                    onScreenSelection = { screen ->
                        if (screen == Screens.Profile) {
                            // Open drawer instead of navigating
                            scope.launch { drawerState.open() }
                        } else {
                            val targetRoute = screen()
                            if (currentDestination != targetRoute) {
                                navController.navigate(targetRoute) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    },
                    onRefreshClick = { dashboardViewModel.refreshAndScrape() },
                    showRefresh = (currentDestination == Screens.Home()),
                    isRefreshing = isRefreshing
                )

                Body(
                    openCategoryMovieList = openCategoryMovieList,
                    openMovieDetailsScreen = openMovieDetailsScreen,
                    openVideoPlayer = openVideoPlayer,
                    openMovieTypeList = openMovieTypeList,
                    updateTopBarVisibility = { isTopBarVisible = it },
                    isTopBarVisible = isTopBarVisible,
                    navController = navController,
                    modifier = Modifier.offset(y = navHostTopPaddingDp),
                )
            }
        }
    }
}

@Composable
private fun BackPressHandledArea(
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) =
    Box(
        modifier = Modifier
            .onPreviewKeyEvent {
                if (it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                    onBackPressed()
                    true
                } else {
                    false
                }
            }
            .then(modifier),
        content = content
    )

@Composable
private fun Body(
    openCategoryMovieList: (categoryId: String) -> Unit,
    openMovieDetailsScreen: (movieId: String) -> Unit,
    openVideoPlayer: (Movie) -> Unit,
    openMovieTypeList: (movieType: String) -> Unit,
    updateTopBarVisibility: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    isTopBarVisible: Boolean = true,
) =
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = Screens.Home(),
    ) {
        composable(Screens.Home()) {
            // 使用 DisposableEffect 监听 HomeScreen 的显示，每次返回时触发焦点恢复
            var triggerFocusRestore by remember { mutableStateOf(0) }
            DisposableEffect(Unit) {
                triggerFocusRestore++
                onDispose { }
            }
            
            HomeScreen(
                onMovieClick = { selectedMovie ->
                    openMovieDetailsScreen(selectedMovie.id)
                },
                goToVideoPlayer = openVideoPlayer,
                onScroll = updateTopBarVisibility,
                isTopBarVisible = isTopBarVisible,
                onShowAllClick = openMovieTypeList,
                focusRestoreTrigger = triggerFocusRestore
            )
        }
    }

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun DrawerContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .focusRestorer()
    ) {
        // WebDAV 浏览器内容 - 使用更小的 padding 适配抽屉
        com.google.jetstream.presentation.screens.profile.WebDavBrowserSection(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp),
            horizontalPadding = 16.dp
        )
    }
}
