package com.dyclean

import android.app.Activity
import android.app.Instrumentation
import com.dyclean.core.Engine
import com.dyclean.core.Logger
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/**
 * 抖音精简 —— libxposed Modern API 102 入口。
 *
 * 只在作用域进程 com.ss.android.ugc.aweme.mobile 内安装 Hook：
 * - Activity.onResume：常规入口；
 * - Instrumentation.callActivityOnResume：抖音多数 Activity 覆写 onResume 且不回调 super
 *   时仍能收到（与 douyin-dl-lsp 的处理一致）。
 *
 * 模块不做任何「注入/加控件」动作，只按规则集隐藏目标 View，因此对目标进程侵入性很低。
 */
class ModuleMain : XposedModule() {

    companion object {
        private const val TARGET_PACKAGE = "com.ss.android.ugc.aweme.mobile"
    }

    @Volatile
    private var installed = false

    private var engine: Engine? = null
    private var logger: Logger? = null

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        val log = Logger(this)
        logger = log
        log.i(
            "onModuleLoaded process=${param.getProcessName()} " +
                "systemServer=${param.isSystemServer} " +
                "framework=$frameworkName $frameworkVersion api=$apiVersion",
        )
    }

    override fun onPackageReady(param: PackageReadyParam) {
        val pkg = param.getPackageName()
        if (pkg != TARGET_PACKAGE) return
        if (installed) return
        installed = true

        val log = logger ?: Logger(this).also { logger = it }
        try {
            log.i("onPackageReady pkg=$pkg first=${param.isFirstPackage}")
            val eng = Engine(this, log)
            engine = eng
            hookLifecycle(eng, log)
            log.i("hook 安装完成")
        } catch (t: Throwable) {
            log.e("hook 安装失败: $t", t)
        }
    }

    private fun hookLifecycle(eng: Engine, log: Logger) {
        try {
            val onResume = Activity::class.java.getDeclaredMethod("onResume")
            hook(onResume).setId("dyclean/activity-onResume").intercept { chain ->
                val result = chain.proceed()
                (chain.getThisObject() as? Activity)?.let { act ->
                    try {
                        eng.attach(act)
                    } catch (t: Throwable) {
                        log.e("attach 异常: $t", t)
                    }
                }
                result
            }
            log.i("hooked Activity.onResume")
        } catch (t: Throwable) {
            log.w("hook Activity.onResume 失败: $t")
        }

        try {
            val m = Instrumentation::class.java
                .getDeclaredMethod("callActivityOnResume", Activity::class.java)
            hook(m).setId("dyclean/instrumentation-onResume").intercept { chain ->
                val act = chain.args.getOrNull(0) as? Activity
                val result = chain.proceed()
                if (act != null) {
                    try {
                        eng.attach(act)
                    } catch (t: Throwable) {
                        log.e("attach 异常(resume): $t", t)
                    }
                }
                result
            }
            log.i("hooked Instrumentation.callActivityOnResume")
        } catch (t: Throwable) {
            log.w("hook callActivityOnResume 失败: $t")
        }
    }
}
