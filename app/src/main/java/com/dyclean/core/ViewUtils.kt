package com.dyclean.core

import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * View 树工具：按资源 id 名 / 文本查找、上溯祖先、可见性判断。
 * 所有遍历都有深度与节点数上限，避免异常层级导致卡死。
 */
object ViewUtils {

    private const val MAX_DEPTH = 64
    private const val MAX_NODES = 5000

    private val idNameCache = HashMap<Int, String>()

    /** 资源 id 名字（如 titlebar_container），无 id 或解析失败返回空串。 */
    fun idName(v: View): String {
        val id = v.id
        if (id == View.NO_ID) return ""
        idNameCache[id]?.let { return it }
        val name = try {
            v.resources.getResourceEntryName(id) ?: ""
        } catch (t: Throwable) {
            ""
        }
        idNameCache[id] = name
        return name
    }

    fun idOf(v: View): String {
        val name = idName(v)
        return if (name.isEmpty()) "0x${Integer.toHexString(v.id)}" else name
    }

    /** 深度优先遍历整棵子树。 */
    fun walk(root: View, visitor: (View) -> Unit) {
        var count = 0
        fun visit(v: View, depth: Int) {
            if (depth > MAX_DEPTH || count >= MAX_NODES) return
            count++
            visitor(v)
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) {
                    val child = v.getChildAt(i) ?: continue
                    visit(child, depth + 1)
                }
            }
        }
        visit(root, 0)
    }

    fun collectById(root: View, name: String, out: MutableList<View>) {
        walk(root) { v -> if (idName(v) == name) out.add(v) }
    }

    fun firstById(root: View, name: String): View? {
        var found: View? = null
        walk(root) { v ->
            if (found == null && idName(v) == name) found = v
        }
        return found
    }

    /** 该 View 的文本（TextView 取 text，其它取 contentDescription）。 */
    fun textOf(v: View): String {
        val t = when (v) {
            is TextView -> v.text?.toString() ?: ""
            else -> ""
        }
        if (t.isNotBlank()) return t.trim()
        return (v.contentDescription ?: "").toString().trim()
    }

    fun matches(text: String, target: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || target.isEmpty()) return false
        if (t == target) return true
        return t.length > target.length && t.startsWith(target)
    }

    /** 文本命中：View 自身文本匹配。 */
    fun collectByText(root: View, target: String, out: MutableList<View>) {
        walk(root) { v ->
            if (matches(textOf(v), target)) out.add(v)
        }
    }

    fun firstByText(root: View, target: String): View? {
        var found: View? = null
        walk(root) { v ->
            if (found == null && matches(textOf(v), target)) found = v
        }
        return found
    }

    /** 向上找 [levels] 级祖先；越界返回 null（避免隐藏到根容器）。 */
    fun ancestorUp(v: View, levels: Int): View? {
        var cur: View? = v
        var n = 0
        while (n < levels) {
            val parent = cur?.parent
            if (parent !is View) return null
            cur = parent
            n++
        }
        return cur
    }

    fun isEffectivelyVisible(v: View): Boolean {
        if (v.visibility != View.VISIBLE) return false
        if (v.alpha <= 0f) return false
        if (v.width <= 0 || v.height <= 0) return false
        if (!v.isAttachedToWindow) return false
        var cur: View? = v
        var guard = 0
        while (cur != null && guard < MAX_DEPTH) {
            if (cur.visibility != View.VISIBLE) return false
            cur = cur.parent as? View
            guard++
        }
        return true
    }

    /** 找容器（按 id 名），返回其直接子项。 */
    fun stripChildren(root: View, containerName: String): List<View> {
        val container = firstById(root, containerName) as? ViewGroup ?: return emptyList()
        val out = ArrayList<View>(container.childCount)
        for (i in 0 until container.childCount) {
            container.getChildAt(i)?.let { out.add(it) }
        }
        return out
    }

    /**
     * 找「条带」容器：容器内存在指定标题 id 时为顶栏样式；
     * 否则回退为容器 id 本身。
     */
    fun findStrip(root: View, containerName: String, titleName: String): ViewGroup? {
        val container = firstById(root, containerName) as? ViewGroup ?: return null
        val hasTitle = ArrayList<View>()
        collectById(container, titleName, hasTitle)
        return if (hasTitle.isEmpty()) null else container
    }
}
