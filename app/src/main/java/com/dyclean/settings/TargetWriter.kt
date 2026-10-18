package com.dyclean.settings

import android.content.Context
import com.dyclean.core.DefaultRules
import java.io.File

/**
 * root 直写回退通道：把规则文件写进抖音私有目录。
 *
 * 用途：LSPosed 未连接 / provider 通道被拦时，仍能在抖音下次启动时生效
 * （引擎的 RuleSource 会读 filesDir/dyclean_rules.txt）。
 * 同时负责「重启抖音」——force-stop 会导致未保存状态丢失，界面上有明确提示。
 */
object TargetWriter {

    const val TARGET_PKG = DefaultRules.TARGET_PKG

    data class Outcome(val ok: Boolean, val message: String)

    fun writeRules(ctx: Context, text: String): Outcome {
        val cache = File(ctx.cacheDir, DefaultRules.RULES_FILE_NAME)
        try {
            cache.writeText(text)
        } catch (t: Throwable) {
            return Outcome(false, "写入缓存文件失败: $t")
        }

        val target = "/data/data/$TARGET_PKG/files/${DefaultRules.RULES_FILE_NAME}"
        val uidRes = RootShell.exec("stat -c %u /data/data/$TARGET_PKG", 8_000L)
        if (!uidRes.ok) {
            return Outcome(false, "获取抖音 uid 失败（无 root 或被拒绝）: ${uidRes.out}")
        }
        val uid = uidRes.out.lines().last().trim()
        if (uid.isEmpty() || uid.toIntOrNull() == null) {
            return Outcome(false, "抖音 uid 解析失败: ${uidRes.out}")
        }

        val cmd = buildString {
            append("mkdir -p /data/data/$TARGET_PKG/files")
            append(" && cp '${cache.absolutePath}' '$target'")
            append(" && chown $uid:$uid '$target'")
            append(" && chmod 600 '$target'")
            append(" && (restorecon '$target' 2>/dev/null || true)")
        }
        val res = RootShell.exec(cmd, 20_000L)
        if (!res.ok) {
            return Outcome(false, "写入抖音目录失败（code=${res.code}）: ${res.out}")
        }
        val check = RootShell.exec("wc -c < '$target'", 8_000L)
        return Outcome(true, "已写入 $target（${check.out.trim()} 字节）")
    }

    fun readTargetRules(): Outcome {
        val target = "/data/data/$TARGET_PKG/files/${DefaultRules.RULES_FILE_NAME}"
        val res = RootShell.exec("cat '$target' 2>/dev/null | wc -c", 8_000L)
        return if (res.ok) Outcome(true, "抖音目录规则文件 ${res.out.trim()} 字节") else Outcome(false, res.out)
    }

    fun deleteTargetRules(): Outcome {
        val target = "/data/data/$TARGET_PKG/files/${DefaultRules.RULES_FILE_NAME}"
        val res = RootShell.exec("rm -f '$target'", 8_000L)
        return if (res.ok) Outcome(true, "已删除抖音目录下的规则文件") else Outcome(false, res.out)
    }

    /** force-stop 抖音，下次启动时重新加载模块与规则。 */
    fun restartTarget(): Outcome {
        val res = RootShell.exec("am force-stop $TARGET_PKG", 15_000L)
        return if (res.ok) Outcome(true, "已停止抖音，重新打开即可生效") else Outcome(false, "停止失败: ${res.out}")
    }
}
