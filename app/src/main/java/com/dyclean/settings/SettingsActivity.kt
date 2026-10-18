package com.dyclean.settings

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.dyclean.core.DefaultRules

/**
 * 设置界面（纯 View，无 Compose 依赖）。
 *
 * 开关与规则行一一对应；任何开关变化都会立即重建规则文本并保存到模块 prefs，
 * 目标进程通过 ContentProvider 读取后 8 秒内自动重载（也可强制重启抖音）。
 */
class SettingsActivity : Activity() {

    private lateinit var statusView: TextView
    private lateinit var contentRoot: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "抖音精简"
        buildUi()
        refreshStatus()
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }

        statusView = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF444444.toInt())
        }
        root.addView(statusView, wrap())

        root.addView(buttonRow(
            "① 应用规则(LSPosed)" to { applyToXposed() },
            "② 写入抖音目录" to { writeToTarget() },
        ))
        root.addView(buttonRow(
            "重启抖音" to { restartTarget() },
            "编辑规则文本" to { editRules() },
        ))
        root.addView(buttonRow(
            "恢复默认" to { resetAll() },
            "刷新状态" to { refreshStatus() },
        ))

        contentRoot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rebuildToggleList()
        root.addView(contentRoot)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun rebuildToggleList() {
        contentRoot.removeAllViews()
        val states = RuleStore.toggleStates(this)
        for ((section, defs) in Toggles.sectionsWithToggles()) {
            contentRoot.addView(sectionHeader(section))
            for (d in defs) {
                contentRoot.addView(toggleRow(d, states[d.key] ?: d.defaultOn))
            }
        }
    }

    private fun sectionHeader(name: String): View = TextView(this).apply {
        text = name
        textSize = 17f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(18), 0, dp(4))
    }

    private fun toggleRow(d: ToggleDef, checked: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(8))
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val info = TextView(this).apply {
            text = d.title
            textSize = 15f
        }
        val desc = TextView(this).apply {
            text = d.desc
            textSize = 11f
            setTextColor(0xFF888888.toInt())
        }
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(info)
            addView(desc)
        }
        row.addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val sw = Switch(this).apply { isChecked = checked }
        sw.setOnCheckedChangeListener { _, isChecked ->
            RuleStore.setToggle(this, d.key, isChecked)
            rebuildAndSave()
            refreshStatus()
        }
        row.addView(sw)
        return row
    }

    private fun buttonRow(vararg buttons: Pair<String, () -> Unit>): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
        }
        for ((label, action) in buttons) {
            val b = Button(this).apply {
                text = label
                textSize = 12f
                setOnClickListener { action() }
            }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.marginEnd = dp(6)
            row.addView(b, lp)
        }
        return row
    }

    // ---------------------------------------------------------------- 动作

    private fun rebuildAndSave() {
        val base = RuleStore.currentRules(this)
        val text = RuleStore.rebuild(base, RuleStore.toggleStates(this))
        RuleStore.saveRules(this, text)
    }

    private fun applyToXposed() {
        rebuildAndSave()
        val (count, errors) = RuleStore.summarize(RuleStore.currentRules(this))
        val msg = if (errors.isEmpty()) {
            "已保存 $count 条规则。抖音已在前台时 8 秒内自动生效；否则请重启抖音。"
        } else {
            "已保存 $count 条规则，但解析有 ${errors.size} 处告警：${errors.first()}"
        }
        toast(msg)
        refreshStatus()
    }

    private fun writeToTarget() {
        rebuildAndSave()
        val text = RuleStore.currentRules(this)
        toast("正在以 root 写入抖音目录…")
        runAsync({
            TargetWriter.writeRules(this, text)
        }) { outcome ->
            toast(outcome.message)
            refreshStatus()
        }
    }

    private fun restartTarget() {
        AlertDialog.Builder(this)
            .setTitle("重启抖音")
            .setMessage("将通过 am force-stop 停止抖音，未保存的内容会丢失。是否继续？")
            .setPositiveButton("重启") { _, _ ->
                runAsync({ TargetWriter.restartTarget() }) { outcome -> toast(outcome.message) }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun resetAll() {
        AlertDialog.Builder(this)
            .setTitle("恢复默认")
            .setMessage("将丢弃本地保存的规则与全部开关状态，回到模块内置默认规则。")
            .setPositiveButton("恢复") { _, _ ->
                RuleStore.prefs(this).edit().remove(com.dyclean.core.PrefsContract.KEY_RULES).apply()
                RuleStore.resetToggles(this)
                targetRuleFileCleanup()
                rebuildToggleList()
                refreshStatus()
                toast("已恢复默认")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun targetRuleFileCleanup() {
        runAsync({ TargetWriter.deleteTargetRules() }) { }
    }

    private fun editRules() {
        val edit = EditText(this).apply {
            setText(RuleStore.currentRules(this@SettingsActivity))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 12
            textSize = 12f
        }
        AlertDialog.Builder(this)
            .setTitle("规则文本")
            .setView(edit)
            .setPositiveButton("解析并保存") { _, _ ->
                val text = edit.text.toString()
                val (count, errors) = RuleStore.summarize(text)
                if (errors.isNotEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("有 ${errors.size} 处无法解析")
                        .setMessage(errors.take(8).joinToString("\n"))
                        .setPositiveButton("仍然保存") { _, _ ->
                            RuleStore.saveRules(this, text)
                            toast("已保存 $count 条规则")
                            refreshStatus()
                        }
                        .setNegativeButton("返回修改", null)
                        .show()
                } else {
                    RuleStore.saveRules(this, text)
                    toast("已保存 $count 条规则")
                    refreshStatus()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshStatus() {
        val text = RuleStore.currentRules(this)
        val (count, errors) = RuleStore.summarize(text)
        val local = RuleStore.prefs(this).getString(com.dyclean.core.PrefsContract.KEY_RULES, null) != null
        val source = if (local) "本地已保存" else "内置默认"
        val errText = if (errors.isEmpty()) "无告警" else "${errors.size} 处告警"
        statusView.text = "规则：$count 条（$source，$errText）\n" +
            "作用域：${DefaultRules.TARGET_PKG}\n" +
            "通道：① LSPosed/Provider（抖音内自动读取） ② root 直写抖音目录\n" +
            "检测 root…"
        runAsync({
            val root = if (RootShell.hasRoot()) "可用" else "不可用（su 被拒绝）"
            val file = TargetWriter.readTargetRules()
            "root：$root\n抖音目录：${file.message}"
        }) { extra ->
            statusView.text = "规则：$count 条（$source，$errText）\n" +
                "作用域：${DefaultRules.TARGET_PKG}\n" +
                "通道：① LSPosed/Provider（抖音内自动读取） ② root 直写抖音目录\n" +
                extra
        }
    }

    // ---------------------------------------------------------------- 工具

    private fun <T> runAsync(work: () -> T, done: (T) -> Unit) {
        Thread {
            val result = try {
                work()
            } catch (t: Throwable) {
                null
            }
            runOnUiThread { if (!isFinishing && result != null) done(result) }
        }.start()
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun wrap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}
