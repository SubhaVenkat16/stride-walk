package com.stride.walk

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class WalkService : Service(), SensorEventListener {

    companion object {
        const val ACTION_START = "com.stride.walk.START"
        const val ACTION_PAUSE = "com.stride.walk.PAUSE"
        const val ACTION_RESUME = "com.stride.walk.RESUME"
        const val ACTION_STOP = "com.stride.walk.STOP"
        private const val NOTIF_ID = 1
        private const val DONE_ID = 3

        fun send(ctx: Context, action: String) {
            val i = Intent(ctx, WalkService::class.java).setAction(action)
            if (action == ACTION_START) ContextCompat.startForegroundService(ctx, i) else ctx.startService(i)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var sensorManager: SensorManager? = null

    private var phase = Phase.IDLE
    private var walkId = 0L
    private var startedAt = 0L
    private var accumulatedMs = 0L
    private var segmentStart = 0L
    private var steps = 0
    private var lastCounter = -1f

    private val ticker = object : Runnable {
        override fun run() {
            publish()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        WalkRepo.init(this)
        Notif.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stop()
        }
        if (phase == Phase.IDLE) stopSelf()
        return START_NOT_STICKY
    }

    private fun elapsedMs(): Long =
        accumulatedMs + (if (phase == Phase.RUNNING) SystemClock.elapsedRealtime() - segmentStart else 0L)

    private fun start() {
        if (phase != Phase.IDLE) {
            try {
                goForeground()
            } catch (e: Exception) {
            }
            return
        }
        walkId = System.currentTimeMillis()
        startedAt = walkId
        accumulatedMs = 0L
        segmentStart = SystemClock.elapsedRealtime()
        steps = 0
        lastCounter = -1f
        phase = Phase.RUNNING
        WalkState.pendingMedia.value = emptyList()
        try {
            goForeground()
        } catch (e: Exception) {
            phase = Phase.IDLE
            return
        }
        QuickStart.hide(this)
        registerSensors()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun pause() {
        if (phase != Phase.RUNNING) return
        accumulatedMs += SystemClock.elapsedRealtime() - segmentStart
        phase = Phase.PAUSED
        publish()
    }

    private fun resume() {
        if (phase != Phase.PAUSED) return
        segmentStart = SystemClock.elapsedRealtime()
        phase = Phase.RUNNING
        publish()
    }

    private fun stop() {
        if (phase == Phase.IDLE) return
        val durationSec = elapsedMs() / 1000
        handler.removeCallbacks(ticker)
        sensorManager?.unregisterListener(this)
        phase = Phase.IDLE

        if (durationSec >= 10 || steps > 0) {
            val walk = Walk(walkId, startedAt, durationSec, steps, WalkState.pendingMedia.value)
            val before = Badges.unlocked(WalkState.walks.value, WalkRepo.goal)
            WalkRepo.add(walk)
            val after = Badges.unlocked(WalkState.walks.value, WalkRepo.goal)
            WalkState.summary.value = Summary(walk, Badges.byIds(after - before))
            postDoneNotification(walk)
        }
        WalkState.pendingMedia.value = emptyList()
        WalkState.live.value = LiveWalk()
        stopForeground(STOP_FOREGROUND_REMOVE)
        QuickStart.show(this)
        stopSelf()
    }

    private fun postDoneNotification(walk: Walk) {
        if (!Notif.canNotify(this)) return
        val n = NotificationCompat.Builder(this, Notif.CH_DONE)
            .setSmallIcon(R.drawable.ic_walk)
            .setContentTitle("Walk saved 🎉")
            .setContentText("${fmtSteps(walk.steps)} steps in ${fmtMinutes(walk.durationSec)}. Tap for your share card.")
            .setContentIntent(Notif.openApp(this))
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(DONE_ID, n)
    }

    private fun goForeground() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun publish() {
        WalkState.live.value = LiveWalk(phase, elapsedMs(), steps)
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun action(label: String, act: String, code: Int): NotificationCompat.Action {
        val pi = PendingIntent.getService(
            this, code,
            Intent(this, WalkService::class.java).setAction(act),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action.Builder(0, label, pi).build()
    }

    private fun buildNotification(): Notification {
        val sec = elapsedMs() / 1000
        val km = steps * Walk.STRIDE_M / 1000.0
        val title = if (phase == Phase.PAUSED) "Paused · ${fmtDuration(sec)}" else "Walking · ${fmtDuration(sec)}"
        val toggle = if (phase == Phase.PAUSED) {
            action("▶ Resume", ACTION_RESUME, 21)
        } else {
            action("⏸ Pause", ACTION_PAUSE, 20)
        }
        return NotificationCompat.Builder(this, Notif.CH_LIVE)
            .setSmallIcon(R.drawable.ic_walk)
            .setContentTitle(title)
            .setContentText("${fmtSteps(steps)} steps · ${"%.2f".format(km)} km")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(Notif.openApp(this))
            .addAction(toggle)
            .addAction(action("⏹ Stop", ACTION_STOP, 22))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun registerSensors() {
        val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager = sm
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            ?: sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        WalkState.hasStepSensor.value = sensor != null
        if (sensor != null) sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val v = event.values[0]
                if (lastCounter >= 0f && phase == Phase.RUNNING) {
                    val d = (v - lastCounter).toInt()
                    if (d in 1..2000) steps += d
                }
                lastCounter = v
            }
            Sensor.TYPE_STEP_DETECTOR -> if (phase == Phase.RUNNING) steps += 1
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        sensorManager?.unregisterListener(this)
        super.onDestroy()
    }
}
