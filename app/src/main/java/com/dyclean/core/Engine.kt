package com.dyclean.core

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import io.github.libxposed.api.XposedModule
import java.io.File
import java.lang.ref.WeakReference

/**
 * 精简引擎。
 *
 * 生命周期：Activity.onResume 时 attach() → 挂布局监听 + 兜底轮询 → 每次扫描按规则集
 * 在 decorView 里找目标并执行隐藏动作；规则被移除时自动 restore。
 *
 * 线程：所有操作都在主线程（Handler(Looper.getMainLooper())），避免跨线程改 View 崩溃。
 */
class Engine(
    private val module: XposedModule,
    private val logger: Logger,
) {

    companion object {
        private const val RULES_TTL_MS = 8_000L
        private const val STATS_INTERVAL_MS = 60_000L
        private const val TOP_TAB_CONTAINER = "top_tab_container"
        private const val TOP_TAB_TITLE = "tv_toptab_title"
        private const val NAV_SLOT = "bottom_nav_slot"
        private const val NAV_FALLBACK_CONTAINER = "zl7"

        /** 顶栏内置保留项。 */
        private val BUILTIN_KEEP = setOf("关注", "推荐")

        /** 顶栏内置隐藏项（同城频道标题会变成城市名，这里按名称兜底）。 */
        private val BUILTIN_HIDE = setOf("同城")

        /** 底栏槽位可识别标签。 */
        private val NAV_LABELS = listOf("首页", "朋友", "消息", "我", "发布", "加号", "拍摄", "商城", "同城")
    }

    private class HiddenRef(val ref: WeakReference<View>, val action: ActionKind, val key: String)

    private val handler = Handler(Looper.getMainLooper())

    private var activityRef: WeakReference<Activity>? = null
    private var decorRef: WeakReference<View>? = null
    private var observer: ViewTreeObserver? = null
    private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    private var running = false
    private var ruleset: Ruleset = RuleParser.parse(DefaultRules.TEXT).ruleset
    private var rulesText: String = DefaultRules.TEXT
    private var rulesSource: String = "builtin"
    private var rulesLoadedAt = 0L

    private var lastScanAt = 0L
    private var burstUntil = 0L
    private var scanPending = false
    private var lastStatsAt = 0L
    private var warnedNoTabContainer = false
    private var warnedNoNav = false

    private val clickedOnce = HashSet<String>()
    private val hidden = ArrayList<HiddenRef>()
    private var hideTotal = 0
    private var restoreTotal = 0
    private var scanCount = 0

    private val scanRunnable = Runnable { doScan("scheduled") }
    private val fallbackRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            requestScan("fallback")
            handler.postDelayed(this, ruleset.header.rescan.fallbackIntervalMs.coerceAtLeast(200L))
        }
    }

    // ---------------------------------------------------------------- 生命周期

    fun attach(activity: Activity) {
        val decor = activity.window?.decorView ?: return
        val sameDecor = decorRef?.get() === decor
        activityRef = WeakReference(activity)
        if (!sameDecor) {
            decorRef = WeakReference(decor)
            releaseObserver()
            observe(decor)
            burstUntil = SystemClock.uptimeMillis() + ruleset.header.rescan.burstDurationMs
            logger.i("attach: ${activity.javaClass.name} decor=${decor.javaClass.name}")
        }
        ensureRunning()
        requestScan("attach")
    }

    private fun ensureRunning() {
        if (running) return
        running = true
        if (ruleset.header.rescan.fallback) handler.postDelayed(fallbackRunnable, 300L)
        logger.i("引擎启动 pkg=${ruleset.header.pkg} action=${ruleset.header.action}")
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        releaseObserver()
        for (rec in hidden) rec.ref.get()?.let { Actions.restore(it, rec.action) }
        hidden.clear()
        logger.i("引擎停止")
    }

    private fun observe(decor: View) {
        val vto = decor.viewTreeObserver ?: return
        observer = vto
        val l = ViewTreeObserver.OnGlobalLayoutListener { requestScan("layout") }
        layoutListener = l
        vto.addOnGlobalLayoutListener(l)
        logger.i("已挂布局监听")
    }

    private fun releaseObserver() {
        val vto = observer
        val l = layoutListener
        if (vto != null && l != null && vto.isAlive) {
            try {
                vto.removeOnGlobalLayoutListener(l)
            } catch (t: Throwable) {
                logger.w("移除布局监听失败: $t")
            }
        }
        observer = null
        layoutListener = null
    }

    // ---------------------------------------------------------------- 调度

    fun requestScan(reason: String) {
        if (!running) return
        val now = SystemClock.uptimeMillis()
        val policy = ruleset.header.rescan
        val interval = if (now < burstUntil) policy.burstIntervalMs else policy.minScanGapMs
        val delta = now - lastScanAt
        if (delta < interval) {
            if (!scanPending) {
                scanPending = true
                handler.postDelayed(scanRunnable, interval - delta)
            }
            return
        }
        doScan(reason)
    }

    private fun doScan(reason: String) {
        scanPending = false
        if (!running) return
        val decor = decorRef?.get() ?: return
        if (decor.width <= 0 || decor.height <= 0) return
        lastScanAt = SystemClock.uptimeMillis()
        scanCount++
        maybeReloadRules()

        val keys = HashSet<String>()
        val hits = HashMap<String, Int>()
        try {
            applyRules(decor, keys, hits)
        } catch (t: Throwable) {
            logger.e("扫描异常: $t", t)
        }
        restoreMissing(keys)

        if (hits.isNotEmpty()) {
            logger.i("扫描[$reason] 命中 ${hits.size} 类: $hits")
        }
        logStatsIfNeeded()
    }

    private fun maybeReloadRules() {
        val now = SystemClock.uptimeMillis()
        if (now - rulesLoadedAt < RULES_TTL_MS) return
        rulesLoadedAt = now
        val activity = activityRef?.get()
        val filesDir: File? = activity?.filesDir
        val loaded = RuleSource.load(module, activity, filesDir, logger)
        if (loaded.text == rulesText && loaded.from == rulesSource) return
        rulesText = loaded.text
        rulesSource = loaded.from
        val parsed = RuleParser.parse(loaded.text)
        ruleset = parsed.ruleset
        if (parsed.errors.isNotEmpty()) {
            logger.w("规则解析告警 ${parsed.errors.size} 条: ${parsed.errors.take(5)}")
        }
        logger.i("规则已加载 from=${loaded.from} 条数=${parsed.ruleset.rules.size} action=${parsed.ruleset.header.action}")
    }

    // ---------------------------------------------------------------- 规则应用

    private fun applyRules(root: View, keys: MutableSet<String>, hits: MutableMap<String, Int>) {
        val action = ruleset.header.action
        val topTabs = ArrayList<Rule>()
        val bottomTabs = ArrayList<Rule>()
        val keepTabs = ArrayList<Rule>()
        val probes = ArrayList<Rule>()
        val clicks = ArrayList<Rule>()

        for (rule in ruleset.rules) {
            when (rule.kind) {
                RuleKind.HIDE_ID -> {
                    keys.add("hide_id:${rule.arg}")
                    val found = ArrayList<View>()
                    ViewUtils.collectById(root, rule.arg, found)
                    for (v in found) hide(v, action, "hide_id:${rule.arg}@${ViewUtils.idName(v)}", "hide_id ${rule.arg}")
                    if (found.isNotEmpty()) hits["hide_id ${rule.arg}"] = found.size
                }

                RuleKind.HIDE_TEXT -> {
                    keys.add("hide_text:${rule.arg}")
                    val found = ArrayList<View>()
                    ViewUtils.collectByText(root, rule.arg, found)
                    for (v in found) hide(v, action, "hide_text:${rule.arg}", "hide_text ${rule.arg}")
                    if (found.isNotEmpty()) hits["hide_text ${rule.arg}"] = found.size
                }

                RuleKind.HIDE_UP -> {
                    keys.add("hide_up:${rule.arg}:${rule.levels}")
                    val found = ArrayList<View>()
                    ViewUtils.collectById(root, rule.arg, found)
                    var count = 0
                    for (v in found) {
                        val target = ViewUtils.ancestorUp(v, rule.levels) ?: v
                        hide(target, action, "hide_up:${rule.arg}:${rule.levels}", "hide_up ${rule.arg} ${rule.levels}")
                        count++
                    }
                    if (count > 0) hits["hide_up ${rule.arg} ${rule.levels}"] = count
                }

                RuleKind.HIDE_TEXT_UP -> {
                    keys.add("hide_text_up:${rule.arg}:${rule.levels}")
                    val found = ArrayList<View>()
                    ViewUtils.collectByText(root, rule.arg, found)
                    var count = 0
                    for (v in found) {
                        val target = ViewUtils.ancestorUp(v, rule.levels) ?: v
                        hide(target, action, "hide_text_up:${rule.arg}:${rule.levels}", "hide_text_up ${rule.arg} ${rule.levels}")
                        count++
                    }
                    if (count > 0) hits["hide_text_up ${rule.arg} ${rule.levels}"] = count
                }

                RuleKind.HIDE_TAB -> topTabs.add(rule)
                RuleKind.KEEP_TOP_TAB -> keepTabs.add(rule)
                RuleKind.BOTTOM_TAB_HIDE -> bottomTabs.add(rule)
                RuleKind.PROBE_ID, RuleKind.PROBE_TEXT -> probes.add(rule)
                RuleKind.CLICK_TEXT -> clicks.add(rule)
            }
        }

        if (topTabs.isNotEmpty() || keepTabs.isNotEmpty()) {
            applyTopTabs(root, topTabs, keepTabs, action, keys, hits)
        }
        if (bottomTabs.isNotEmpty()) {
            applyBottomNav(root, bottomTabs, action, keys, hits)
        }
        for (rule in probes) runProbe(root, rule)
        for (rule in clicks) runClick(root, rule)
    }

    private fun hide(v: View, action: ActionKind, key: String, label: String) {
        if (Actions.isApplied(v, action)) {
            if (hidden.none { it.ref.get() === v && it.key == key }) {
                hidden.add(HiddenRef(WeakReference(v), action, key))
            }
            return
        }
        try {
            Actions.apply(v, action)
            hideTotal++
            hidden.add(HiddenRef(WeakReference(v), action, key))
            logger.i("隐藏 $label -> ${ViewUtils.idOf(v)}")
        } catch (t: Throwable) {
            logger.w("隐藏失败 $label: $t")
        }
    }

    private fun restoreMissing(keys: Set<String>) {
        if (hidden.isEmpty()) return
        val it = hidden.iterator()
        while (it.hasNext()) {
            val rec = it.next()
            val v = rec.ref.get()
            if (v == null) {
                it.remove()
                continue
            }
            // 规则仍在 → 保留；规则已删除 → 恢复
            val ruleAlive = recKeyPrefix(rec.key) in keys
            if (!ruleAlive) {
                try {
                    Actions.restore(v, rec.action)
                    restoreTotal++
                } catch (t: Throwable) {
                    logger.w("恢复失败: $t")
                }
                it.remove()
            }
        }
    }

    /** 记录 key 去掉 @ 后缀（同一规则命中多个同 id 元素时用 @ 区分）。 */
    private fun recKeyPrefix(key: String): String = key.substringBefore('@')

    // ---------------------------------------------------------------- 顶栏 / 底栏

    private fun applyTopTabs(
        root: View,
        hideRules: List<Rule>,
        keepRules: List<Rule>,
        action: ActionKind,
        keys: MutableSet<String>,
        hits: MutableMap<String, Int>,
    ) {
        val container = ViewUtils.firstById(root, TOP_TAB_CONTAINER) as? ViewGroup
        if (container == null) {
            if (!warnedNoTabContainer) {
                warnedNoTabContainer = true
                logger.w("未找到顶栏容器 $TOP_TAB_CONTAINER")
            }
            return
        }
        val hideSet = HashSet<String>()
        for (r in hideRules) {
            hideSet.add(r.arg)
            keys.add("tab_hide:${r.arg}")
        }
        hideSet.addAll(BUILTIN_HIDE)
        val keepSet = HashSet<String>(BUILTIN_KEEP)
        for (r in keepRules) {
            keepSet.add(r.arg)
            keys.add("tab_keep:${r.arg}")
        }

        val titles = ArrayList<View>()
        ViewUtils.collectById(container, TOP_TAB_TITLE, titles)
        var count = 0
        for (title in titles) {
            val item = stripItemOf(container, title) ?: continue
            val label = ViewUtils.textOf(title)
            if (label.isEmpty()) continue
            val shouldHide = label in hideSet || (keepRules.isNotEmpty() && label !in keepSet)
            if (shouldHide) {
                hide(item, action, "tab:${label}", "顶栏 ${label}")
                keys.add("tab:${label}")
                count++
            }
        }
        if (count > 0) {
            hits["tab_hide"] = count
            logger.i("顶栏: 容器子项=${container.childCount} 标题=${titles.size} hide=$hideSet keep=$keepSet 隐藏=$count")
        }
    }

    /** 找到 child 相对 container 的直接子项。 */
    private fun stripItemOf(container: ViewGroup, child: View): View? {
        var cur: View = child
        var guard = 0
        while (guard < 16) {
            val parent = cur.parent
            if (parent === container) return cur
            if (parent !is View) return null
            cur = parent
            guard++
        }
        return null
    }

    private fun applyBottomNav(
        root: View,
        hideRules: List<Rule>,
        action: ActionKind,
        keys: MutableSet<String>,
        hits: MutableMap<String, Int>,
    ) {
        val hideSet = HashSet<String>()
        for (r in hideRules) {
            hideSet.add(r.arg)
            keys.add("bottom_tab_hide:${r.arg}")
        }

        val slots = ArrayList<View>()
        ViewUtils.collectById(root, NAV_SLOT, slots)
        val candidates: List<View> = if (slots.isNotEmpty()) {
            slots
        } else {
            val container = ViewUtils.firstById(root, NAV_FALLBACK_CONTAINER) as? ViewGroup
            if (container == null) {
                if (!warnedNoNav) {
                    warnedNoNav = true
                    logger.w("未找到底栏容器 $NAV_SLOT / $NAV_FALLBACK_CONTAINER")
                }
                return
            }
            val list = ArrayList<View>(container.childCount)
            for (i in 0 until container.childCount) container.getChildAt(i)?.let { list.add(it) }
            list
        }

        var count = 0
        for (slot in candidates) {
            val label = identifyNavItem(slot) ?: continue
            if (label in hideSet) {
                hide(slot, action, "nav:${label}", "底栏 $label")
                keys.add("nav:${label}")
                count++
            }
        }
        if (count > 0) {
            hits["bottom_tab_hide"] = count
            logger.i("底栏: 槽位=${candidates.size} hide=$hideSet 隐藏=$count")
        }
    }

    private fun identifyNavItem(slot: View): String? {
        var best: String? = null
        ViewUtils.walk(slot) { v ->
            if (best == null) {
                val t = ViewUtils.textOf(v)
                if (t.isNotEmpty() && NAV_LABELS.any { ViewUtils.matches(t, it) }) best = t
            }
        }
        if (best != null) return best
        val cd = (slot.contentDescription ?: "").toString().trim()
        if (cd.isNotEmpty()) {
            return NAV_LABELS.firstOrNull { cd.contains(it) } ?: cd
        }
        return null
    }

    // ---------------------------------------------------------------- 诊断

    private fun runProbe(root: View, rule: Rule) {
        val found = ArrayList<View>()
        if (rule.kind == RuleKind.PROBE_ID) {
            ViewUtils.collectById(root, rule.arg, found)
        } else {
            ViewUtils.collectByText(root, rule.arg, found)
        }
        if (found.isEmpty()) {
            logger.i("probe ${rule.kind.key} ${rule.arg}: 无命中")
            return
        }
        val sb = StringBuilder("probe ${rule.kind.key} ${rule.arg}: ${found.size} 处")
        for (v in found.take(6)) {
            sb.append("\n  - id=${ViewUtils.idOf(v)} text='${ViewUtils.textOf(v).take(24)}' " +
                "size=${v.width}x${v.height} vis=${v.visibility} alpha=${v.alpha}")
        }
        logger.i(sb.toString())
    }

    private fun runClick(root: View, rule: Rule) {
        val key = "click:${rule.arg}"
        if (key in clickedOnce) return
        val v = ViewUtils.firstByText(root, rule.arg) ?: return
        clickedOnce.add(key)
        try {
            v.performClick()
            logger.i("已点击『${rule.arg}』id=${ViewUtils.idOf(v)}")
        } catch (t: Throwable) {
            logger.w("点击失败『${rule.arg}』: $t")
        }
    }

    private fun logStatsIfNeeded() {
        val now = SystemClock.uptimeMillis()
        if (now - lastStatsAt < STATS_INTERVAL_MS) return
        lastStatsAt = now
        logger.i("统计: 扫描=$scanCount 隐藏=$hideTotal 恢复=$restoreTotal 当前隐藏=${hidden.size} 规则=${ruleset.rules.size} from=$rulesSource")
    }
}
