package me.floow.app

import android.util.Log
import me.floow.domain.utils.Logger
import java.util.Locale

class LoggerImpl : Logger {
	override fun d(tag: String?, message: String) {
		Log.d(tag, message)
		if (BuildConfig.DEBUG) {
			TelemetryDashboard.onLog(tag = tag ?: "Logger", message = message)
		}
	}
}

private object TelemetryDashboard {
	private const val DASHBOARD_TAG = "TelemetryDashboard"
	private const val SUMMARY_EVERY_EVENTS = 20

	private val timingStats = linkedMapOf<String, TimingAggregate>()
	private val cacheStats = linkedMapOf<String, MutableMap<String, Int>>()
	private var telemetryEvents = 0

	private val kvRegex = Regex("""([A-Za-z0-9_]+)=([^\s]+)""")

	@Synchronized
	fun onLog(tag: String, message: String) {
		if (!message.startsWith("[telemetry]")) return
		telemetryEvents++

		val values = parseValues(message)
		if (message.startsWith("[telemetry][timing]")) {
			val op = values["op"] ?: "unknown"
			val duration = values["durationMs"]?.toLongOrNull()
			if (duration != null) {
				timingStats.getOrPut(op) { TimingAggregate() }.record(duration)
			}
		} else if (message.startsWith("[telemetry][cache]")) {
			val scope = values["scope"] ?: "unknown"
			val decision = values["decision"] ?: "unknown"
			val byDecision = cacheStats.getOrPut(scope) { linkedMapOf() }
			byDecision[decision] = (byDecision[decision] ?: 0) + 1
		}

		if (telemetryEvents % SUMMARY_EVERY_EVENTS == 0) {
			Log.i(DASHBOARD_TAG, buildSummaryLine(sourceTag = tag))
		}
	}

	private fun parseValues(message: String): Map<String, String> {
		return kvRegex
			.findAll(message)
			.map { it.groupValues[1] to it.groupValues[2] }
			.toMap()
	}

	private fun buildSummaryLine(sourceTag: String): String {
		val timingPart = if (timingStats.isEmpty()) {
			"timing=none"
		} else {
			"timing=" + timingStats.entries.joinToString(separator = "|") { (op, stat) ->
				"$op{count=${stat.count},avg=${stat.avg()},min=${stat.minMs},max=${stat.maxMs}}"
			}
		}

		val cachePart = if (cacheStats.isEmpty()) {
			"cache=none"
		} else {
			"cache=" + cacheStats.entries.joinToString(separator = "|") { (scope, decisions) ->
				val total = decisions.values.sum().coerceAtLeast(1)
				val hits = (decisions["fresh_hit"] ?: 0) + (decisions["stale_hit_revalidate"] ?: 0)
				val hitRate = (hits.toDouble() / total.toDouble()) * 100.0
				"$scope{hitRate=${"%.1f".format(Locale.US, hitRate)}%,events=$total}"
			}
		}

		return "[dashboard] events=$telemetryEvents source=$sourceTag $timingPart $cachePart"
	}

	private data class TimingAggregate(
		var count: Int = 0,
		var sumMs: Long = 0,
		var minMs: Long = Long.MAX_VALUE,
		var maxMs: Long = Long.MIN_VALUE
	) {
		fun record(valueMs: Long) {
			count++
			sumMs += valueMs
			minMs = minOf(minMs, valueMs)
			maxMs = maxOf(maxMs, valueMs)
		}

		fun avg(): Long = if (count == 0) 0 else sumMs / count
	}
}
