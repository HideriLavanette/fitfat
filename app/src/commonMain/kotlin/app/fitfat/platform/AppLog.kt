package app.fitfat.platform

import app.fitfat.time.Fmt
import app.fitfat.time.now
import kotlinx.datetime.LocalDateTime

/**
 * Журнал последних событий приложения (в памяти, 150 строк): переходы между экранами и ошибки.
 * Уходит только вместе с отчётом о сбое или сообщением о проблеме. Без данных дневника, имени и почты.
 */
object AppLog {
    private const val MAX = 150
    private val lines = ArrayDeque<String>()

    fun i(msg: String) {
        val t = LocalDateTime.now()
        val line = "${Fmt.two(t.hour)}:${Fmt.two(t.minute)}:${Fmt.two(t.second)} $msg"
        synchronizedAdd(line)
    }

    private fun synchronizedAdd(line: String) {
        lines.addLast(line)
        while (lines.size > MAX) lines.removeFirst()
    }

    fun dump(): String = lines.toList().joinToString("\n")
}
