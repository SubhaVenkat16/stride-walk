package com.stride.walk

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Walk(
    val id: Long,
    val startedAt: Long,
    val durationSec: Long,
    val steps: Int,
    val media: List<String> = emptyList()
) {
    val date: LocalDate
        get() = Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    val hourOfDay: Int
        get() = Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).hour
    val distanceKm: Double
        get() = steps * STRIDE_M / 1000.0
    val calories: Int
        get() = (steps * KCAL_PER_STEP).toInt()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("startedAt", startedAt)
        put("durationSec", durationSec)
        put("steps", steps)
        put("media", JSONArray(media))
    }

    companion object {
        const val STRIDE_M = 0.762
        const val KCAL_PER_STEP = 0.04

        fun fromJson(o: JSONObject): Walk {
            val m = o.optJSONArray("media") ?: JSONArray()
            return Walk(
                o.getLong("id"),
                o.getLong("startedAt"),
                o.getLong("durationSec"),
                o.getInt("steps"),
                (0 until m.length()).map { m.getString(it) }
            )
        }
    }
}

fun fmtDuration(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun fmtMinutes(sec: Long): String {
    val m = sec / 60
    return when {
        sec < 60 -> "${sec}s"
        m >= 60 -> "${m / 60} h ${m % 60} min"
        else -> "$m min"
    }
}

fun fmtSteps(n: Int): String = "%,d".format(n)

data class Badge(val id: String, val emoji: String, val title: String, val desc: String)

object Badges {
    val all = listOf(
        Badge("first", "👟", "First Steps", "Finish your first walk"),
        Badge("warm", "🔥", "Warm-up", "1,000 steps in one walk"),
        Badge("trail", "🥾", "Trail Blazer", "5,000 steps in one walk"),
        Badge("half", "⏱️", "Half-hour Hero", "Walk for 30 minutes"),
        Badge("hour", "🏅", "Hour Power", "Walk for 60 minutes"),
        Badge("tenk", "🏆", "10K Master", "10,000 steps in one day"),
        Badge("goal", "🎯", "Goal Getter", "Reach your daily step goal"),
        Badge("s3", "📆", "3-Day Streak", "Walk 3 days in a row"),
        Badge("s7", "🌟", "7-Day Streak", "Walk 7 days in a row"),
        Badge("early", "🌅", "Early Bird", "Start a walk before 7 AM"),
        Badge("memory", "📸", "Memory Maker", "Add a photo or video to a walk")
    )

    fun unlocked(walks: List<Walk>, goal: Int): Set<String> {
        val s = mutableSetOf<String>()
        if (walks.isEmpty()) return s
        s.add("first")
        if (walks.any { it.steps >= 1000 }) s.add("warm")
        if (walks.any { it.steps >= 5000 }) s.add("trail")
        if (walks.any { it.durationSec >= 1800 }) s.add("half")
        if (walks.any { it.durationSec >= 3600 }) s.add("hour")
        val dayTotals = walks.groupBy { it.date }.mapValues { e -> e.value.sumOf { w -> w.steps } }
        if (dayTotals.values.any { it >= 10000 }) s.add("tenk")
        if (dayTotals.values.any { it >= goal }) s.add("goal")
        val longest = longestStreak(dayTotals.keys)
        if (longest >= 3) s.add("s3")
        if (longest >= 7) s.add("s7")
        if (walks.any { it.hourOfDay < 7 }) s.add("early")
        if (walks.any { it.media.isNotEmpty() }) s.add("memory")
        return s
    }

    fun byIds(ids: Collection<String>): List<Badge> = all.filter { it.id in ids }

    private fun longestStreak(dates: Set<LocalDate>): Int {
        val sorted = dates.sorted()
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in sorted) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            if (run > best) best = run
            prev = d
        }
        return best
    }

    fun currentStreak(walks: List<Walk>, today: LocalDate): Int {
        val dates = walks.map { it.date }.toSet()
        var d = when {
            today in dates -> today
            today.minusDays(1) in dates -> today.minusDays(1)
            else -> return 0
        }
        var n = 0
        while (d in dates) {
            n++
            d = d.minusDays(1)
        }
        return n
    }
}

object Quotes {
    private val list = listOf(
        "Every step is a small promise you keep to yourself.",
        "Walk it out. Your mind will thank you.",
        "Slow progress is still progress. Keep going.",
        "The best view comes after the walk you didn't feel like taking.",
        "One more step. Then one more.",
        "Fresh air, steady steps, clear mind.",
        "Small steps every day build a big life.",
        "You don't need to run. You just need to move.",
        "Today's walk is tomorrow's energy.",
        "Where your feet go, your mood follows.",
        "Walk like you have somewhere wonderful to be.",
        "Strong habits are built one step at a time.",
        "Be proud of every step, not just the finish line.",
        "A walk a day keeps the worries away."
    )

    fun pick(seed: Long): String = list[Math.floorMod(seed, list.size.toLong()).toInt()]

    fun daily(): String = pick(LocalDate.now().toEpochDay())
}
