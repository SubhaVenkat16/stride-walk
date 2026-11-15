package com.stride.walk

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

object Notif {
    const val CH_LIVE = "walk_live"
    const val CH_QUICK = "walk_quick"
    const val CH_DONE = "walk_done"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_LIVE, "Walk in progress", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_QUICK, "Quick start", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "Walk summary", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}

fun hasActivityPermission(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) ==
        PackageManager.PERMISSION_GRANTED

/** A small notification with a Start button, so a walk can begin without opening the app. */
object QuickStart {
    private const val ID = 2

    fun show(ctx: Context) {
        if (!WalkRepo.quickStart) return
        if (!Notif.canNotify(ctx) || !hasActivityPermission(ctx)) return
        Notif.ensureChannels(ctx)
        val startIntent = PendingIntent.getForegroundService(
            ctx, 10,
            Intent(ctx, WalkService::class.java).setAction(WalkService.ACTION_START),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, Notif.CH_QUICK)
            .setSmallIcon(R.drawable.ic_walk)
            .setContentTitle("Ready for a walk?")
            .setContentText("Tap Start to begin tracking")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(Notif.openApp(ctx))
            .addAction(0, "▶ Start walk", startIntent)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(ID, n)
    }

    fun hide(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(ID)
    }
}
