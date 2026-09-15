package com.kazumaproject.core.data.snippet

/**
 * キーボード上に表示するスニペット（メールアドレスなど、よく使うテキスト）。
 *
 * @param id データベース上のユニークID
 * @param label 一覧に表示する見出し
 * @param text タップ時に入力される本文
 */
data class SnippetItem(
    val id: Long,
    val label: String,
    val text: String,
)
