package com.dyclean.core

/**
 * 规则种类。key 即规则文件里每行开头的关键字（大小写不敏感）。
 */
enum class RuleKind(val key: String) {
    /** 顶部频道：保留（只保留关注/推荐） */
    KEEP_TOP_TAB("tab_keep"),

    /** 顶部频道：隐藏指定标题 */
    HIDE_TAB("tab_hide"),

    /** 底部导航栏：隐藏指定槽位（按文字识别） */
    BOTTOM_TAB_HIDE("bottom_tab_hide"),

    /** 按资源 id 名隐藏 */
    HIDE_ID("hide_id"),

    /** 按文本隐藏 */
    HIDE_TEXT("hide_text"),

    /** 按资源 id 名隐藏，并向上找 N 级祖先一起隐藏 */
    HIDE_UP("hide_up"),

    /** 按文本隐藏，并向上找 N 级祖先一起隐藏 */
    HIDE_TEXT_UP("hide_text_up"),

    /** 诊断：打印指定 id 的命中位置 */
    PROBE_ID("probe_id"),

    /** 诊断：打印指定文本的命中位置 */
    PROBE_TEXT("probe_text"),

    /** 只点击一次指定文本（用于一次性引导/弹窗） */
    CLICK_TEXT("click_text"),
    ;

    companion object {
        fun of(raw: String): RuleKind? = entries.firstOrNull { it.key == raw.lowercase() }
    }
}

/** 一条规则：kind + 参数（资源 id 名 / 文本）+ 上溯层数。 */
data class Rule(val kind: RuleKind, val arg: String, val levels: Int = 0)

/** 隐藏方式。 */
enum class ActionKind {
    GONE,
    INVISIBLE,
    ALPHA0,
    REMOVE_FROM_PARENT,
    COLLAPSE,
    ;

    companion object {
        fun of(raw: String): ActionKind? = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}

/** 重扫策略。 */
data class RescanPolicy(
    /** 是否挂 ViewTreeObserver 布局监听（布局驱动扫描） */
    val layoutDriven: Boolean = true,
    /** 是否开启兜底轮询 */
    val fallback: Boolean = true,
    /** 兜底轮询间隔 */
    val fallbackIntervalMs: Long = 800L,
    /** 页面重建后是否进入一轮密集重扫 */
    val burstOnRevive: Boolean = true,
    /** 密集重扫间隔 */
    val burstIntervalMs: Long = 120L,
    /** 密集重扫持续时间 */
    val burstDurationMs: Long = 2500L,
    /** 两次扫描最小间隔，防抖 */
    val minScanGapMs: Long = 60L,
)

/** 目标 App 声明段 [target]。 */
data class TargetHeader(
    val pkg: String = "com.ss.android.ugc.aweme.mobile",
    val activity: String = "com.ss.android.ugc.aweme.main.MainActivity",
    val rescan: RescanPolicy = RescanPolicy(),
    val action: ActionKind = ActionKind.GONE,
) {
    companion object {
        val EMPTY = TargetHeader()
    }
}

/** 一份规则集：目标声明 + 规则列表。 */
data class Ruleset(val header: TargetHeader, val rules: List<Rule>)
