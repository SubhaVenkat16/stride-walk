package com.stride.walk

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import java.io.File

enum class Phase { IDLE, RUNNING, PAUSED }

data class LiveWalk(
    val phase: Phase = Phase.IDLE,
    val elapsedMs: Long = 0L,
    val steps: Int = 0
)

data class Summary(val walk: Walk, val newBadges: List<Badge>)

/** In-memory state shared between the walking service and the UI (same process). */
object WalkState {
    val live = MutableStateFlow(LiveWalk())
    val pendingMedia = MutableStateFlow<List<String>>(emptyList())
    val summary = MutableStateFlow<Summary?>(null)
    val walks = MutableStateFlow<List<Walk>>(emptyList())
    val hasStepSensor = MutableStateFlow(true)
}

object WalkRepo {
    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs != null) return
        prefs = ctx.applicationContext.getSharedPreferences("stride", Context.MODE_PRIVATE)
        WalkState.walks.value = load()
    }

    private fun p(): SharedPreferences = prefs!!

    private fun load(): List<Walk> = try {
        val a = JSONArray(p().getString("walks", "[]") ?: "[]")
        (0 until a.length()).map { Walk.fromJson(a.getJSONObject(it)) }
            .sortedByDescending { it.startedAt }
    } catch (e: Exception) {
        emptyList()
    }

    private fun persist(list: List<Walk>) {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        p().edit().putString("walks", a.toString()).apply()
        WalkState.walks.value = list
    }

    fun add(w: Walk) = persist((WalkState.walks.value + w).sortedByDescending { it.startedAt })

    fun update(w: Walk) = persist(WalkState.walks.value.map { if (it.id == w.id) w else it })

    fun delete(id: Long) {
        WalkState.walks.value.firstOrNull { it.id == id }?.media?.forEach { File(it).delete() }
        persist(WalkState.walks.value.filter { it.id != id })
    }

    var goal: Int
        get() = p().getInt("goal", 6000)
        set(value) {
            p().edit().putInt("goal", value).apply()
        }

    var quickStart: Boolean
        get() = p().getBoolean("quick", false)
        set(value) {
            p().edit().putBoolean("quick", value).apply()
        }
}
