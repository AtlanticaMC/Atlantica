package io.atlantica.profiler


import cz.lukynka.prettylog.LogType
import cz.lukynka.prettylog.log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object Profiler {

    @Volatile
    var enabled = true

    private val sections = ConcurrentHashMap<String, SectionStats>()

    fun <T> section(
        name: String,
        warnAboveMs: Double = -1.0,
        block: () -> T
    ): T {

        if (!enabled) {
            return block()
        }

        val start = System.nanoTime()

        try {
            return block()
        } finally {
            val elapsed = System.nanoTime() - start

            val stats = sections.computeIfAbsent(name) {
                SectionStats(name)
            }

            stats.record(elapsed)

            val ms = elapsed / 1_000_000.0

            if (warnAboveMs >= 0 && ms >= warnAboveMs) {
                log(
                    "[Profiler] \"$name\" took ${"%.3f".format(ms)}ms",
                    LogType.PERFORMANCE
                )
            }
        }
    }

    fun printSummary() {

        log("========== PROFILER SUMMARY ==========", LogType.PERFORMANCE)

        sections.values
            .sortedByDescending { it.totalTime.get() }
            .forEach {

                val totalMs = it.totalTime.get() / 1_000_000.0
                val avgMs = it.averageMs()
                val maxMs = it.maxTime.get() / 1_000_000.0

                log(
                    buildString {
                        append(it.name)
                        append(" | calls=")
                        append(it.calls.get())
                        append(" | avg=")
                        append("%.3f".format(avgMs))
                        append("ms")
                        append(" | max=")
                        append("%.3f".format(maxMs))
                        append("ms")
                        append(" | total=")
                        append("%.3f".format(totalMs))
                        append("ms")
                    },
                     LogType.PERFORMANCE
                )
            }
    }

    fun reset() {
        sections.clear()
    }

}

class SectionStats(
    val name: String
) {

    val calls = AtomicLong()
    val totalTime = AtomicLong()
    val maxTime = AtomicLong()

    fun record(nanos: Long) {

        calls.incrementAndGet()
        totalTime.addAndGet(nanos)

        maxTime.updateAndGet {
            maxOf(it, nanos)
        }
    }

    fun averageMs(): Double {

        val count = calls.get()

        if (count == 0L) {
            return 0.0
        }

        return (totalTime.get().toDouble() / count) / 1_000_000.0
    }
}
