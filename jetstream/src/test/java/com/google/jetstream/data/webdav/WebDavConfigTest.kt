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

package com.google.jetstream.data.webdav

import org.junit.Test
import org.junit.Assert.*

/**
 * WebDAV配置测试
 */
class WebDavConfigTest {

    @Test
    fun testWebDavConfigValidation() {
        // 测试有效配置
        val validConfig = WebDavConfig(
            serverUrl = "https://example.com/webdav",
            username = "testuser",
            password = "testpass",
            displayName = "Test Server"
        )
        assertTrue("有效配置应该通过验证", validConfig.isValid())

        // 测试无效配置 - 空URL
        val invalidConfigEmptyUrl = WebDavConfig(
            serverUrl = "",
            username = "testuser",
            password = "testpass"
        )
        assertFalse("空URL配置应该验证失败", invalidConfigEmptyUrl.isValid())

        // 测试无效配置 - 空用户名
        val invalidConfigEmptyUsername = WebDavConfig(
            serverUrl = "https://example.com/webdav",
            username = "",
            password = "testpass"
        )
        assertFalse("空用户名配置应该验证失败", invalidConfigEmptyUsername.isValid())

        // 测试无效配置 - 空密码
        val invalidConfigEmptyPassword = WebDavConfig(
            serverUrl = "https://example.com/webdav",
            username = "testuser",
            password = ""
        )
        assertFalse("空密码配置应该验证失败", invalidConfigEmptyPassword.isValid())

        // 测试无效配置 - 无效URL格式
        val invalidConfigBadUrl = WebDavConfig(
            serverUrl = "not-a-url",
            username = "testuser",
            password = "testpass"
        )
        assertFalse("无效URL格式应该验证失败", invalidConfigBadUrl.isValid())
    }

    @Test
    fun testGetFormattedServerUrl() {
        // 测试URL已经以/结尾
        val configWithSlash = WebDavConfig(
            serverUrl = "https://example.com/webdav/",
            username = "test",
            password = "test"
        )
        assertEquals(
            "已有斜杠的URL应该保持不变",
            "https://example.com/webdav/",
            configWithSlash.getFormattedServerUrl()
        )

        // 测试URL没有以/结尾
        val configWithoutSlash = WebDavConfig(
            serverUrl = "https://example.com/webdav",
            username = "test",
            password = "test"
        )
        assertEquals(
            "没有斜杠的URL应该添加斜杠",
            "https://example.com/webdav/",
            configWithoutSlash.getFormattedServerUrl()
        )
    }

    @Test
    fun testWebDavResult() {
        // 测试成功结果
        val successResult = WebDavResult.Success("test data")
        assertTrue("成功结果应该是Success类型", successResult is WebDavResult.Success)
        assertEquals("成功结果应该包含正确数据", "test data", successResult.data)

        // 测试错误结果
        val errorResult = WebDavResult.Error("test error")
        assertTrue("错误结果应该是Error类型", errorResult is WebDavResult.Error)
        assertEquals("错误结果应该包含正确消息", "test error", errorResult.message)

        // 测试加载结果
        val loadingResult = WebDavResult.Loading
        assertTrue("加载结果应该是Loading类型", loadingResult is WebDavResult.Loading)
    }

    @Test
    fun testWebDavConnectionStatus() {
        // 测试所有连接状态
        val statuses = WebDavConnectionStatus.values()
        assertEquals("应该有5个连接状态", 5, statuses.size)
        
        assertTrue("应该包含IDLE状态", statuses.contains(WebDavConnectionStatus.IDLE))
        assertTrue("应该包含CONNECTING状态", statuses.contains(WebDavConnectionStatus.CONNECTING))
        assertTrue("应该包含CONNECTED状态", statuses.contains(WebDavConnectionStatus.CONNECTED))
        assertTrue("应该包含FAILED状态", statuses.contains(WebDavConnectionStatus.FAILED))
        assertTrue("应该包含TESTING状态", statuses.contains(WebDavConnectionStatus.TESTING))
    }
}
