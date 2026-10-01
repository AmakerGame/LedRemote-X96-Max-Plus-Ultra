package ua.tvremote.ledcontrol

import java.io.DataOutputStream

/**
 * Мінімальна обгортка над "su" для виконання команд з root-правами.
 * Кожен виклик відкриває окрему su-сесію (простіше й надійніше на кастомних прошивках,
 * ніж тримати один довгоживучий процес).
 */
object Shell {

    data class Result(val ok: Boolean, val output: String)

    fun exec(command: String): Result {
        return try {
            val process = ProcessBuilder("su").redirectErrorStream(true).start()
            val out = DataOutputStream(process.outputStream)
            out.writeBytes(command + "\n")
            out.writeBytes("exit\n")
            out.flush()
            val output = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            Result(code == 0, output.trim())
        } catch (e: Exception) {
            Result(false, e.message ?: "exec error")
        }
    }

    fun execAll(commands: List<String>): Result {
        return exec(commands.joinToString("\n"))
    }

    /** Записує значення у sysfs-атрибут: echo "value" > path */
    fun writeAttr(path: String, value: String): Result =
        exec("echo $value > $path")

    fun readAttr(path: String): Result =
        exec("cat $path 2>/dev/null")

    fun hasRoot(): Boolean {
        val r = exec("id")
        return r.ok && r.output.contains("uid=0")
    }
}
