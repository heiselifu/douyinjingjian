package com.dyclean.core

import android.util.Log
import io.github.libxposed.api.XposedModule

/** 统一日志前缀，方便在 LSPosed 日志里 grep。 */
class Logger(private val module: XposedModule) {

    companion object {
        const val TAG = "DyClean"
    }

    fun i(msg: String) = module.log(Log.INFO, TAG, msg)

    fun w(msg: String, t: Throwable? = null) {
        if (t == null) module.log(Log.WARN, TAG, msg) else module.log(Log.WARN, TAG, msg, t)
    }

    fun e(msg: String, t: Throwable? = null) {
        if (t == null) module.log(Log.ERROR, TAG, msg) else module.log(Log.ERROR, TAG, msg, t)
    }
}
