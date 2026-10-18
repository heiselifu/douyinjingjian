package com.dyclean.core

/**
 * 规则文件解析。
 *
 * 文件格式（与模块内置默认规则一致）：
 * ```
 * [target]
 * package=com.ss.android.ugc.aweme.mobile
 * activity=com.ss.android.ugc.aweme.main.MainActivity
 * rescan=layout+fallback
 * action=GONE
 *
 * [rules]
 * hide_id videomusiccoverblock
 * tab_hide 热点
 * hide_up uh5 1
 * hide_text_up 打开提醒 2
 * ```
 * - 空行、以 `#` 或 `!` 开头的行忽略；
 * - `[rules]` 段可省略（默认就在 rules 段）；
 * - 规则行 = `关键字 参数 [上溯层数]`，参数允许包含空格。
 */
object RuleParser {

    data class Result(val ruleset: Ruleset, val errors: List<String>)

    fun parse(text: String): Result {
        var pkg = TargetHeader.EMPTY.pkg
        var activity = TargetHeader.EMPTY.activity
        var action = TargetHeader.EMPTY.action
        var rescan = RescanPolicy()
        val rules = ArrayList<Rule>()
        val errors = ArrayList<String>()
        var section = "rules"

        for ((index, raw) in text.lines().withIndex()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue

            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length - 1).trim().lowercase()
                continue
            }

            if (section == "target") {
                val key: String
                val value: String
                val eq = line.indexOf('=')
                if (eq > 0) {
                    key = line.substring(0, eq).trim().lowercase()
                    value = line.substring(eq + 1).trim()
                } else {
                    val parts = line.split(WHITESPACE, limit = 2)
                    key = parts[0].lowercase()
                    value = parts.getOrElse(1) { "" }.trim()
                }
                when (key) {
                    "package", "pkg" -> if (value.isNotEmpty()) pkg = value
                    "activity" -> if (value.isNotEmpty()) activity = value
                    "action" -> ActionKind.of(value)?.let { action = it }
                        ?: errors.add("第 ${index + 1} 行：未知 action『$value』")
                    "rescan" -> rescan = applyRescan(rescan, value)
                    "burst" -> value.toLongOrNull()?.let { rescan = rescan.copy(burstIntervalMs = it) }
                    "gap" -> value.toLongOrNull()?.let { rescan = rescan.copy(minScanGapMs = it) }
                    else -> errors.add("第 ${index + 1} 行：未知 [target] 项『$key』")
                }
                continue
            }

            val rule = parseRule(line)
            if (rule == null) {
                errors.add("第 ${index + 1} 行：无法解析的规则『$line』")
            } else {
                rules.add(rule)
            }
        }

        return Result(
            Ruleset(
                header = TargetHeader(pkg = pkg, activity = activity, rescan = rescan, action = action),
                rules = rules,
            ),
            errors,
        )
    }

    fun parseRule(line: String): Rule? {
        val tokens = line.split(WHITESPACE).filter { it.isNotEmpty() }
        if (tokens.size < 2) return null
        val kind = RuleKind.of(tokens[0]) ?: return null
        val last = tokens.last().toIntOrNull()
        return if (last != null && tokens.size >= 3) {
            Rule(kind, tokens.subList(1, tokens.size - 1).joinToString(" "), last.coerceAtLeast(0))
        } else {
            Rule(kind, tokens.subList(1, tokens.size).joinToString(" "), 0)
        }
    }

    private fun applyRescan(base: RescanPolicy, value: String): RescanPolicy {
        val v = value.lowercase()
        return when {
            v.contains("off") -> base.copy(layoutDriven = false, fallback = false)
            v.contains("layout") && v.contains("fallback") -> base.copy(layoutDriven = true, fallback = true)
            v.contains("layout") -> base.copy(layoutDriven = true, fallback = false)
            v.contains("fallback") -> base.copy(layoutDriven = false, fallback = true)
            else -> base
        }
    }

    private val WHITESPACE = Regex("\\s+")
}
