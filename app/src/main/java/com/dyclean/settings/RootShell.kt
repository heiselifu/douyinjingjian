package com.dyclean.settings

import java.util.concurrent.TimeUnit

/** 极简 root 命令执行器（su -c）。失败一律返回错误信息，不抛异常。 */
object RootShell {

    data class Result(val code: Int, val out: String) {
        val ok: Boolean get() = code == 0
    }

    fun exec(cmd: String, timeoutMs: Long = 20_000L): Result {
        var process: Process? = null
        return try {
            process = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return Result(-1, "命令超时（${timeoutMs}ms）")
            }
            val out = process.inputStream.bufferedReader().use { it.readText() }.trim()
            Result(process.exitValue(), out)
        } catch (t: Throwable) {
            Result(-1, t.toString())
        } finally {
            try {
                process?.inputStream?.close()
            } catch (_: Throwable) {
            }
        }
    }

    fun hasRoot(): Boolean = exec("id -u", 5_000L).out == "0"
}
