package com.dyclean.core

import android.view.View
import android.view.ViewGroup
import java.util.WeakHashMap

/**
 * 隐藏动作。除 REMOVE_FROM_PARENT 外都可逆，便于「关掉开关后恢复」。
 */
object Actions {

    private val originalLp = WeakHashMap<View, ViewGroup.LayoutParams>()
    private val removedFrom = WeakHashMap<View, Pair<ViewGroup, Int>>()

    fun apply(v: View, kind: ActionKind) {
        if (isApplied(v, kind)) return
        when (kind) {
            ActionKind.GONE -> v.visibility = View.GONE
            ActionKind.INVISIBLE -> v.visibility = View.INVISIBLE
            ActionKind.ALPHA0 -> {
                v.alpha = 0f
                v.isClickable = false
            }
            ActionKind.COLLAPSE -> {
                val lp = v.layoutParams
                if (lp != null && !originalLp.containsKey(v)) {
                    originalLp[v] = ViewGroup.LayoutParams(lp)
                }
                v.visibility = View.GONE
                if (lp != null) {
                    lp.width = 0
                    lp.height = 0
                    v.layoutParams = lp
                }
                v.minimumWidth = 0
                v.minimumHeight = 0
            }
            ActionKind.REMOVE_FROM_PARENT -> {
                val parent = v.parent
                if (parent is ViewGroup) {
                    removedFrom[v] = parent to parent.indexOfChild(v)
                    parent.removeView(v)
                }
            }
        }
    }

    fun restore(v: View, kind: ActionKind) {
        when (kind) {
            ActionKind.GONE, ActionKind.INVISIBLE -> v.visibility = View.VISIBLE
            ActionKind.ALPHA0 -> {
                v.alpha = 1f
                v.isClickable = true
            }
            ActionKind.COLLAPSE -> {
                originalLp.remove(v)?.let { v.layoutParams = it }
                v.visibility = View.VISIBLE
            }
            ActionKind.REMOVE_FROM_PARENT -> {
                val record = removedFrom.remove(v) ?: return
                val (parent, index) = record
                if (v.parent != null) return
                try {
                    parent.addView(v, index.coerceIn(0, parent.childCount))
                } catch (t: Throwable) {
                    // 恢复失败不影响主流程
                }
            }
        }
    }

    fun isApplied(v: View, kind: ActionKind): Boolean = when (kind) {
        ActionKind.GONE -> v.visibility == View.GONE
        ActionKind.INVISIBLE -> v.visibility == View.INVISIBLE
        ActionKind.ALPHA0 -> v.alpha == 0f
        ActionKind.COLLAPSE -> v.visibility == View.GONE && v.layoutParams?.height == 0
        ActionKind.REMOVE_FROM_PARENT -> v.parent == null
    }

    fun isHiddenByAction(v: View): Boolean = when {
        v.visibility != View.VISIBLE -> true
        v.alpha == 0f -> true
        else -> false
    }
}
