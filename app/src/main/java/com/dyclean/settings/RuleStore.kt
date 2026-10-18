package com.dyclean.settings

import android.content.Context
import android.content.SharedPreferences
import com.dyclean.core.DefaultRules
import com.dyclean.core.PrefsContract
import com.dyclean.core.RuleParser

/**
 * 规则文本的读写与「开关 → 规则文件」重建。
 *
 * 重建策略：
 * - [target] 段沿用当前文件（缺失则用内置默认），保证 package/activity/rescan/action 不被界面覆盖；
 * - 规则区按 [Toggles.SECTIONS] 顺序输出，开关开 = 规则行生效，关 = 行被注释（保留可读性）；
 * - 当前文件里不属于任何开关的手工规则行原样保留在末尾，界面不会误删。
 */
object RuleStore {

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PrefsContract.PREFS_NAME, Context.MODE_PRIVATE)

    fun currentRules(ctx: Context): String =
        prefs(ctx).getString(PrefsContract.KEY_RULES, null)?.takeIf { it.isNotBlank() }
            ?: DefaultRules.TEXT

    fun saveRules(ctx: Context, text: String) {
        prefs(ctx).edit().putString(PrefsContract.KEY_RULES, text).apply()
    }

    fun toggleStates(ctx: Context): Map<String, Boolean> {
        val p = prefs(ctx)
        return Toggles.ALL.associate { d -> d.key to p.getBoolean(toggleKey(d.key), d.defaultOn) }
    }

    fun setToggle(ctx: Context, key: String, on: Boolean) {
        prefs(ctx).edit().putBoolean(toggleKey(key), on).apply()
    }

    fun resetToggles(ctx: Context) {
        val e = prefs(ctx).edit()
        Toggles.ALL.forEach { e.remove(toggleKey(it.key)) }
        e.apply()
    }

    private fun toggleKey(key: String) = "toggle_$key"

    /** 按开关状态重建规则文本。 */
    fun rebuild(base: String, states: Map<String, Boolean>): String {
        val baseLines = base.lines().map { it.trim() }
        val targetLines = extractTargetSection(baseLines)
            .ifEmpty { extractTargetSection(DefaultRules.TEXT.lines().map { it.trim() }) }

        val manual = baseLines
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("!") && !it.startsWith("[") }
            .filter { RuleParser.parseRule(it) != null && it !in Toggles.managedLines }
            .distinct()

        val sb = StringBuilder()
        sb.appendLine("[target]")
        targetLines.forEach { sb.appendLine(it) }
        sb.appendLine()
        sb.appendLine("[rules]")
        for ((section, defs) in Toggles.sectionsWithToggles()) {
            sb.appendLine("# ===== $section =====")
            for (d in defs) {
                val on = states[d.key] ?: d.defaultOn
                if (on) d.lines.forEach { sb.appendLine(it) } else d.lines.forEach { sb.appendLine("# $it") }
            }
            sb.appendLine()
        }
        if (manual.isNotEmpty()) {
            sb.appendLine("# ===== 手工规则（高级编辑新增，界面不管理）=====")
            manual.forEach { sb.appendLine(it) }
        }
        return sb.toString().trimEnd() + "\n"
    }

    private fun extractTargetSection(lines: List<String>): List<String> {
        val out = ArrayList<String>()
        var inTarget = false
        for (raw in lines) {
            val l = raw.trim()
            if (l.startsWith("[") && l.endsWith("]")) {
                inTarget = l.substring(1, l.length - 1).trim().equals("target", ignoreCase = true)
                continue
            }
            if (inTarget && l.isNotEmpty() && !l.startsWith("#") && !l.startsWith("!")) out.add(l)
        }
        return out
    }

    /** 统计规则条数与解析告警，供界面显示。 */
    fun summarize(text: String): Pair<Int, List<String>> {
        val r = RuleParser.parse(text)
        return r.ruleset.rules.size to r.errors
    }
}
