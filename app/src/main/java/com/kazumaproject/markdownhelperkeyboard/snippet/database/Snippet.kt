package com.kazumaproject.markdownhelperkeyboard.snippet.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ユーザーが登録したスニペット（メールアドレスなど、ワンタップで入力するテキスト）。
 *
 * 定型文 (user_template) と違い「読み」を持たず変換候補には出ない。
 * キーボード上のスニペットパネルから直接呼び出す用途に限定する。
 */
@Entity(
    tableName = "snippet",
    indices = [Index(value = ["sortOrder"])],
)
data class Snippet(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 一覧に表示する見出し。空でもよい。 */
    val label: String,
    /** タップ時に入力される本文。 */
    val text: String,
    /** 表示順。小さいほど先頭。 */
    val sortOrder: Int,
)
