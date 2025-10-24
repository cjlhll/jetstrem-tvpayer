/*
 * Copyright 2025 Google LLC
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

package com.google.jetstream.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ASSRT API 响应基础结构
 */
@Serializable
data class AssrtResponse<T>(
    val status: Int,
    val sub: T? = null,
    val error: String? = null
)

/**
 * 搜索响应
 */
@Serializable
data class AssrtSearchResult(
    val action: String,
    val keyword: String,
    val result: String,
    val subs: List<AssrtSubtitle>
)

/**
 * 详情响应
 */
@Serializable
data class AssrtDetailResult(
    val action: String,
    val result: String,
    val subs: List<AssrtSubtitleDetail>
)

/**
 * 字幕基本信息
 */
@Serializable
data class AssrtSubtitle(
    val id: Int,
    @SerialName("native_name")
    val nativeName: String,
    val videoname: String? = null,
    val revision: Int? = null,  // 修订版本可能为 null
    val subtype: String,
    @SerialName("upload_time")
    val uploadTime: String,
    @SerialName("vote_score")
    val voteScore: Int? = null,  // 评分可能为 null
    @SerialName("release_site")
    val releaseSite: String? = null,
    val lang: AssrtLanguage? = null
)

/**
 * 字幕详细信息
 */
@Serializable
data class AssrtSubtitleDetail(
    val id: Int,
    val filename: String,
    @SerialName("native_name")
    val nativeName: String,
    val url: String,
    val size: Int,
    val subtype: String,
    @SerialName("upload_time")
    val uploadTime: String,
    @SerialName("vote_score")
    val voteScore: Int? = null,  // 评分可能为 null
    @SerialName("release_site")
    val releaseSite: String? = null,
    val filelist: List<AssrtFile>? = null,
    val lang: AssrtLanguage? = null,
    @SerialName("down_count")
    val downCount: Int? = null,
    @SerialName("view_count")
    val viewCount: Int? = null
)

/**
 * 字幕语言信息
 */
@Serializable
data class AssrtLanguage(
    val desc: String,
    val langlist: AssrtLangList? = null
)

/**
 * 语言列表
 */
@Serializable
data class AssrtLangList(
    val langchs: Boolean? = null,      // 简体中文
    val langcht: Boolean? = null,      // 繁体中文
    val langeng: Boolean? = null,      // 英语
    val langdou: Boolean? = null,      // 双语
    val langkor: Boolean? = null,      // 韩语
    val langjpn: Boolean? = null,      // 日语
)

/**
 * 压缩包内的文件
 */
@Serializable
data class AssrtFile(
    val f: String,  // 文件名
    val s: String,  // 文件大小
    val url: String // 下载地址
)

/**
 * 字幕语言枚举（按优先级排序）
 */
enum class SubtitleLanguage(val priority: Int, val keywords: List<String>) {
    SIMPLIFIED_BILINGUAL(1, listOf("简英", "简体&英文", "双语", "langdou")),
    SIMPLIFIED_CHINESE(2, listOf("简体", "简", "chs", "chi", "zh-cn", "中文", "langchs")),
    TRADITIONAL_BILINGUAL(3, listOf("繁英", "繁体&英文", "繁體雙語")),
    TRADITIONAL_CHINESE(4, listOf("繁体", "繁體", "cht", "zh-tw", "zh-hk", "langcht")),
    ENGLISH(5, listOf("英语", "英文", "eng", "en", "english", "langeng"));

    companion object {
        /**
         * 检测字幕语言
         */
        fun detect(desc: String?, langlist: AssrtLangList?): SubtitleLanguage? {
            // 首先检查是否为双语
            if (langlist?.langdou == true || 
                desc?.contains("双语", ignoreCase = true) == true ||
                desc?.contains("雙語", ignoreCase = true) == true) {
                // 判断是简体双语还是繁体双语
                return if (langlist?.langchs == true || desc?.contains("简", ignoreCase = true) == true) {
                    SIMPLIFIED_BILINGUAL
                } else if (langlist?.langcht == true || desc?.contains("繁", ignoreCase = true) == true) {
                    TRADITIONAL_BILINGUAL
                } else {
                    SIMPLIFIED_BILINGUAL // 默认简体双语
                }
            }

            // 检查简体中文
            if (langlist?.langchs == true) {
                return SIMPLIFIED_CHINESE
            }

            // 检查繁体中文
            if (langlist?.langcht == true) {
                return TRADITIONAL_CHINESE
            }

            // 检查英语
            if (langlist?.langeng == true) {
                return ENGLISH
            }

            // 根据描述文本判断
            desc?.let { d ->
                for (lang in values().sortedBy { it.priority }) {
                    if (lang.keywords.any { keyword -> 
                        d.contains(keyword, ignoreCase = true) 
                    }) {
                        return lang
                    }
                }
            }

            return null
        }
    }
}

