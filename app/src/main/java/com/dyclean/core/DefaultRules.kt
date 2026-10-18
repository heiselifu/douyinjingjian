package com.dyclean.core

/**
 * 内置默认规则集（作用于抖音 com.ss.android.ugc.aweme.mobile）。
 *
 * 规则来源：原 DyClean 模块内置默认规则，锚点均为实测 View 树资源 id。
 * 关闭某条规则 = 删除/注释该行，界面保存时会按开关状态重写。
 */
object DefaultRules {

    val TEXT: String = """
        [target]
        package=com.ss.android.ugc.aweme.mobile
        activity=com.ss.android.ugc.aweme.main.MainActivity
        rescan=layout+fallback
        action=GONE

        [rules]
        # ---- 底部主导航栏（打开 = 隐藏该底栏 Tab）----
        # 实测 5 个槽位：首页 / 朋友 / 加号 / 消息 / 我
        # 默认全部保留；需要时取消注释：
        # bottom_tab_hide 加号
        # bottom_tab_hide 朋友

        # ---- 首页：顶部频道（只保留 关注 / 推荐，其余隐藏）----
        # 名称取自 top_tab_container 下的 tv_toptab_title 实测值
        tab_hide 热点
        tab_hide 精选
        tab_hide 直播
        tab_hide 团购
        tab_hide 经验

        # ---- 首页：右下角音乐挂件 / 拍同款 ----
        hide_id videomusiccoverblock

        # ---- 侧边栏：冗余入口 ----
        hide_text 通知消息
        hide_text 我的转化记录
        hide_text 抖音创作者中心
        hide_text 直播广场
        hide_text 使用管理助手
        hide_text 未成年人保护

        # ---- 我页：创建 AI 形象（#uh5 上一级为可点条目）----
        hide_up uh5 1

        # ---- 消息页：顶部提醒 Banner（#big 上两级 = Banner 行容器）----
        hide_up big 2
        hide_text_up 打开提醒 2

        # ---- 消息页：顶部横向限时日常 / 故事头像列表（#rp9 上一级 = 行容器）----
        hide_up rp9 1

        # ==================================================================
        # 我页（个人主页）整页元素 —— 每行对着界面里「我」卡片的一个开关
        # 锚点全部来自实测 View 树 dump（MainActivity 停在「我」页）
        # 默认全部关闭（注释）：整页隐藏过于激进，按需在界面上逐个打开
        # ==================================================================
        # 标题栏：状态栏占位 + 整条标题栏
        # hide_id titlebar_container
        # 标题栏左侧「添加好友」
        # hide_id o2f
        # 标题栏右上角：求更新 / 主页访客 / 搜索 / 更多
        # hide_id qpk
        # 主页访客图标（在 #qpk 内，单独可关）
        # hide_id visitor_icon
        # 顶部个人主页背景大图
        # hide_id ueu
        # 头像容器
        # hide_id se1
        # 昵称
        # hide_id rk7
        # 昵称右侧下拉箭头
        # hide_id v_nick_right_area
        # 「抖音号：xxx」整行
        # hide_id sed
        # 获赞 / 互关 / 关注 / 粉丝
        # hide_id sej
        # 「编辑主页」按钮
        # hide_id sev
        # 简介 / 标签 / 直播预告整块
        # hide_id se9
        # 功能入口行
        # hide_id sdv
        # 运营推广横幅
        # hide_id cje
        # 作品栏（作品/日常/推荐/收藏/喜欢）——不能用 hide_id zkk，
        # 评论区复用了同名 id，故锚定作品栏独有的 #zlq 向上 3 层
        # hide_up zlq 3
        # 私密作品入口
        # hide_id qod
        # 作品列表内容区（封面网格整块）
        # hide_id wix

        # ---- 以下为诊断项，默认关闭，需要时取消注释 ----
        # probe_text 打开提醒
        # probe_text 限时日常
        # probe_id rp9
        # probe_id xm4
        # probe_id videomusiccoverblock
        # probe_text 编辑主页
        # click_text 消息
    """.trimIndent()

    /** 规则文件在目标 App 私有目录中的文件名（root 直写回退通道）。 */
    const val RULES_FILE_NAME = "dyclean_rules.txt"

    /** 模块自身 SharedPreferences 名（RemotePreferences 通道）。 */
    const val PREFS_NAME = "dyclean_rules"

    /** SharedPreferences 里存规则文本的 key。 */
    const val PREF_KEY_RULES = "rules_text"

    /** 目标 App 包名。 */
    const val TARGET_PKG = "com.ss.android.ugc.aweme.mobile"
}
