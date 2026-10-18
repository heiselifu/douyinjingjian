package com.dyclean.settings

import com.dyclean.core.DefaultRules

/**
 * 一个界面开关对应一组规则行（通常 1 行，Banner 那种需要 2 行）。
 * 开关「开」= 这些规则行原样出现在规则文件里；「关」= 它们被移除/注释。
 */
data class ToggleDef(
    val key: String,
    val section: String,
    val title: String,
    val desc: String,
    val lines: List<String>,
) {
    val defaultOn: Boolean get() = lines.all { it in activeLines }
}

/** 内置默认规则里处于「生效」状态的规则行（未注释）。 */
private val activeLines: Set<String> by lazy {
    DefaultRules.TEXT.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("!") && !it.startsWith("[") }
        .toSet()
}

/**
 * 开关清单。锚点（资源 id / 文本）全部来自实测 View 树 dump，
 * 与 [DefaultRules.TEXT] 中注释的规则行一一对应，避免两处定义漂移。
 */
object Toggles {

    val SECTIONS = listOf("底部导航", "顶部频道", "首页", "侧边栏", "消息页", "我页")

    val ALL: List<ToggleDef> = listOf(
        // ---------------------------------------------------------- 底部导航
        ToggleDef(
            "nav_plus", "底部导航", "隐藏「加号」", "底栏中间发布入口（bottom_nav_slot）",
            listOf("bottom_tab_hide 加号"),
        ),
        ToggleDef(
            "nav_friends", "底部导航", "隐藏「朋友」", "底栏「朋友」Tab",
            listOf("bottom_tab_hide 朋友"),
        ),
        ToggleDef(
            "nav_message", "底部导航", "隐藏「消息」", "底栏「消息」Tab",
            listOf("bottom_tab_hide 消息"),
        ),
        ToggleDef(
            "nav_me", "底部导航", "隐藏「我」", "底栏「我」Tab（隐藏后无法进入个人页）",
            listOf("bottom_tab_hide 我"),
        ),

        // ---------------------------------------------------------- 顶部频道
        ToggleDef(
            "tab_hot", "顶部频道", "隐藏「热点」", "#tv_toptab_title 文本匹配",
            listOf("tab_hide 热点"),
        ),
        ToggleDef(
            "tab_featured", "顶部频道", "隐藏「精选」", "#tv_toptab_title 文本匹配",
            listOf("tab_hide 精选"),
        ),
        ToggleDef(
            "tab_live", "顶部频道", "隐藏「直播」", "#tv_toptab_title 文本匹配",
            listOf("tab_hide 直播"),
        ),
        ToggleDef(
            "tab_group", "顶部频道", "隐藏「团购」", "#tv_toptab_title 文本匹配",
            listOf("tab_hide 团购"),
        ),
        ToggleDef(
            "tab_exp", "顶部频道", "隐藏「经验」", "#tv_toptab_title 文本匹配",
            listOf("tab_hide 经验"),
        ),

        // ---------------------------------------------------------- 首页
        ToggleDef(
            "home_music", "首页", "隐藏音乐挂件 / 拍同款", "#videomusiccoverblock",
            listOf("hide_id videomusiccoverblock"),
        ),
        ToggleDef(
            "home_ai_avatar", "首页", "隐藏「创建 AI 形象」", "#uh5 向上 1 级",
            listOf("hide_up uh5 1"),
        ),

        // ---------------------------------------------------------- 侧边栏
        ToggleDef(
            "sb_notice", "侧边栏", "隐藏「通知消息」", "按文本匹配",
            listOf("hide_text 通知消息"),
        ),
        ToggleDef(
            "sb_conversion", "侧边栏", "隐藏「我的转化记录」", "按文本匹配",
            listOf("hide_text 我的转化记录"),
        ),
        ToggleDef(
            "sb_creator", "侧边栏", "隐藏「抖音创作者中心」", "按文本匹配",
            listOf("hide_text 抖音创作者中心"),
        ),
        ToggleDef(
            "sb_live_square", "侧边栏", "隐藏「直播广场」", "按文本匹配",
            listOf("hide_text 直播广场"),
        ),
        ToggleDef(
            "sb_assistant", "侧边栏", "隐藏「使用管理助手」", "按文本匹配",
            listOf("hide_text 使用管理助手"),
        ),
        ToggleDef(
            "sb_minor", "侧边栏", "隐藏「未成年人保护」", "按文本匹配",
            listOf("hide_text 未成年人保护"),
        ),

        // ---------------------------------------------------------- 消息页
        ToggleDef(
            "msg_banner", "消息页", "隐藏顶部提醒 Banner",
            "#big 向上 2 级 + 文本「打开提醒」向上 2 级（两条规则）",
            listOf("hide_up big 2", "hide_text_up 打开提醒 2"),
        ),
        ToggleDef(
            "msg_daily", "消息页", "隐藏限时日常 / 故事头像行", "#rp9 向上 1 级",
            listOf("hide_up rp9 1"),
        ),

        // ---------------------------------------------------------- 我页
        ToggleDef(
            "me_titlebar", "我页", "隐藏标题栏整条", "#titlebar_container（含状态栏占位）",
            listOf("hide_id titlebar_container"),
        ),
        ToggleDef(
            "me_add_friend", "我页", "隐藏「添加好友」", "#o2f",
            listOf("hide_id o2f"),
        ),
        ToggleDef(
            "me_top_right", "我页", "隐藏右上角按钮组", "#qpk（求更新/访客/搜索/更多）",
            listOf("hide_id qpk"),
        ),
        ToggleDef(
            "me_visitor", "我页", "隐藏主页访客图标", "#visitor_icon",
            listOf("hide_id visitor_icon"),
        ),
        ToggleDef(
            "me_cover", "我页", "隐藏背景大图", "#ueu",
            listOf("hide_id ueu"),
        ),
        ToggleDef(
            "me_avatar", "我页", "隐藏头像", "#se1",
            listOf("hide_id se1"),
        ),
        ToggleDef(
            "me_nickname", "我页", "隐藏昵称", "#rk7",
            listOf("hide_id rk7"),
        ),
        ToggleDef(
            "me_nick_arrow", "我页", "隐藏昵称右侧箭头", "#v_nick_right_area",
            listOf("hide_id v_nick_right_area"),
        ),
        ToggleDef(
            "me_douyin_id", "我页", "隐藏「抖音号」行", "#sed",
            listOf("hide_id sed"),
        ),
        ToggleDef(
            "me_stats", "我页", "隐藏获赞/关注/粉丝行", "#sej",
            listOf("hide_id sej"),
        ),
        ToggleDef(
            "me_edit", "我页", "隐藏「编辑主页」", "#sev",
            listOf("hide_id sev"),
        ),
        ToggleDef(
            "me_bio", "我页", "隐藏简介区块", "#se9（简介/标签/直播预告）",
            listOf("hide_id se9"),
        ),
        ToggleDef(
            "me_entries", "我页", "隐藏功能入口行", "#sdv",
            listOf("hide_id sdv"),
        ),
        ToggleDef(
            "me_promo", "我页", "隐藏运营推广横幅", "#cje",
            listOf("hide_id cje"),
        ),
        ToggleDef(
            "me_works_tabs", "我页", "隐藏作品栏 Tab", "#zlq 向上 3 级（作品/日常/推荐/收藏/喜欢）",
            listOf("hide_up zlq 3"),
        ),
        ToggleDef(
            "me_private", "我页", "隐藏私密作品入口", "#qod",
            listOf("hide_id qod"),
        ),
        ToggleDef(
            "me_works_grid", "我页", "隐藏作品列表内容", "#wix（封面网格整块）",
            listOf("hide_id wix"),
        ),
    )

    fun byKey(key: String): ToggleDef? = ALL.firstOrNull { it.key == key }

    fun sectionsWithToggles(): List<Pair<String, List<ToggleDef>>> =
        SECTIONS.map { s -> s to ALL.filter { it.section == s } }.filter { it.second.isNotEmpty() }

    /** 被界面管理的规则行集合（重建规则文件时用于区分手工规则）。 */
    val managedLines: Set<String> by lazy { ALL.flatMap { it.lines }.toSet() }
}
