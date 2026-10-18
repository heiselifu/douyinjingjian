package com.dyclean

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.dyclean.core.DefaultRules
import com.dyclean.core.PrefsContract

/**
 * 供目标进程（抖音）跨进程读取规则文本与开关。
 *
 * 为什么不用 `getRemotePreferences`：本机实测该 API 返回默认值，
 * 读不到模块 App 自己的 prefs（douyin-dl-lsp 中已记录同一现象），
 * 因此 ContentProvider 作为主通道。
 *
 * 暴露内容只有两项，无写能力：
 * - `content://com.dyclean.prefs/rules`   → 单列表格，一行一列，规则全文
 * - `content://com.dyclean.prefs/enabled` → 单列表格，1/0
 */
class DyPrefsProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val prefs = context?.getSharedPreferences(PrefsContract.PREFS_NAME, Context.MODE_PRIVATE)
        return when (uri.lastPathSegment) {
            "rules" -> {
                val text = prefs?.getString(PrefsContract.KEY_RULES, null)?.takeIf { it.isNotBlank() }
                    ?: DefaultRules.TEXT
                MatrixCursor(arrayOf(PrefsContract.KEY_RULES)).apply {
                    addRow(arrayOf<Any>(text))
                }
            }
            "enabled" -> {
                val enabled = prefs?.getBoolean(PrefsContract.KEY_ENABLED, true) ?: true
                MatrixCursor(arrayOf(PrefsContract.KEY_ENABLED)).apply {
                    addRow(arrayOf<Any>(if (enabled) 1 else 0))
                }
            }
            else -> null
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.${PrefsContract.AUTHORITY}"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
