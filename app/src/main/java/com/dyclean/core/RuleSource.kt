package com.dyclean.core

import android.content.Context
import android.net.Uri
import io.github.libxposed.api.XposedModule
import java.io.File

/**
 * 规则文本加载链（按优先级，逐级回退）：
 *
 * 1. 模块 ContentProvider（`content://com.dyclean.prefs/rules`）—— 设置页保存后即时生效，
 *    本机实测 `getRemotePreferences` 读不到模块 App 的 prefs，故以 provider 为主通道；
 * 2. 目标 App 私有目录里的规则文件（root 直写回退通道，设置页「写入抖音目录」生成）；
 * 3. 模块内置默认规则。
 */
object RuleSource {

    data class Loaded(val text: String, val from: String)

    fun load(module: XposedModule, context: Context?, filesDir: File?, logger: Logger): Loaded {
        loadProvider(context, logger)?.let { return Loaded(it, "provider") }
        loadFile(filesDir, logger)?.let { return Loaded(it, "file") }
        loadRemote(module, logger)?.let { return Loaded(it, "remote-prefs") }
        return Loaded(DefaultRules.TEXT, "builtin")
    }

    private fun loadProvider(context: Context?, logger: Logger): String? {
        if (context == null) return null
        return try {
            context.contentResolver.query(PrefsContract.RULES_URI, null, null, null, null)?.use { c ->
                if (c.moveToFirst() && c.columnCount > 0) {
                    c.getString(0)?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
            }
        } catch (t: Throwable) {
            logger.w("provider 通道读取失败: $t")
            null
        }
    }

    private fun loadFile(filesDir: File?, logger: Logger): String? {
        if (filesDir == null) return null
        val f = File(filesDir, DefaultRules.RULES_FILE_NAME)
        if (!f.isFile) return null
        return try {
            f.readText().takeIf { it.isNotBlank() }
        } catch (t: Throwable) {
            logger.w("读取规则文件失败 ${f.absolutePath}: $t")
            null
        }
    }

    private fun loadRemote(module: XposedModule, logger: Logger): String? = try {
        module.getRemotePreferences(DefaultRules.PREFS_NAME)
            .getString(DefaultRules.PREF_KEY_RULES, null)
            ?.takeIf { it.isNotBlank() }
    } catch (t: Throwable) {
        logger.w("远程配置通道不可用: $t")
        null
    }
}

/** 模块与目标进程之间的契约常量（provider authority / Uri / prefs 键名）。 */
object PrefsContract {
    const val AUTHORITY = "com.dyclean.rewrite.prefs"
    const val PREFS_NAME = "dyclean"
    const val KEY_RULES = "rules_text"
    const val KEY_ENABLED = "enabled"

    val RULES_URI: Uri = Uri.parse("content://$AUTHORITY/rules")
    val ENABLED_URI: Uri = Uri.parse("content://$AUTHORITY/enabled")
}
