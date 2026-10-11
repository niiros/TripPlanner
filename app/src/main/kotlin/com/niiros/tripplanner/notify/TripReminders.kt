package com.niiros.tripplanner.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.niiros.tripplanner.MainActivity
import com.niiros.tripplanner.R
import com.niiros.tripplanner.core.plan.DayPlan
import java.time.Duration
import java.time.Instant

/** A reminder to post at [at]. Pure data so the selection logic stays simple to reason about. */
data class Reminder(val at: Instant, val title: String, val text: String)

/**
 * Local reminders on the trip day (off by default): when to leave to reach a "needs daylight" stop
 * before sunset minus the buffer, and the last stop where mincha fits. Uses inexact alarms, so no
 * exact-alarm permission is needed; a few minutes of drift is fine for these reminders.
 */
object TripReminders {
    private const val CHANNEL_ID = "trip_reminders"
    private const val MAX_REMINDERS = 8
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_TEXT = "text"
    private const val EXTRA_ID = "id"
    private val LEAVE_LEAD: Duration = Duration.ofMinutes(10)
    private val MINCHA_LEAD: Duration = Duration.ofMinutes(10)

    fun reminders(context: Context, plan: DayPlan, buffer: Duration, now: Instant): List<Reminder> {
        val list = ArrayList<Reminder>()
        plan.stops.forEachIndexed { i, sp ->
            if (!sp.scheduled.stop.needsDaylight) return@forEachIndexed
            val sunset = sp.point.zmanim?.sunset ?: return@forEachIndexed
            // Latest departure from the previous point that still arrives by sunset minus buffer.
            val latestLeave = sunset.minus(buffer).minus(sp.scheduled.legIn.duration)
            val previousDeparture = if (i == 0) plan.schedule.departure else plan.stops[i - 1].scheduled.departure
            val at = minOf(latestLeave, previousDeparture).minus(LEAVE_LEAD)
            list += Reminder(at, context.getString(R.string.notif_leave_title), context.getString(R.string.notif_leave_text, sp.point.place.name))
        }
        plan.prayer.minchaStopIds.lastOrNull()?.let { id ->
            plan.stops.firstOrNull { it.scheduled.stop.id == id }?.let { sp ->
                list += Reminder(
                    sp.scheduled.arrival.minus(MINCHA_LEAD),
                    context.getString(R.string.notif_mincha_title),
                    context.getString(R.string.notif_mincha_text, sp.point.place.name),
                )
            }
        }
        return list.filter { it.at.isAfter(now) }.sortedBy { it.at }.take(MAX_REMINDERS)
    }

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = context.getString(R.string.notif_channel_description)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun pending(context: Context, index: Int, reminder: Reminder?): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java)
        if (reminder != null) {
            intent.putExtra(EXTRA_TITLE, reminder.title).putExtra(EXTRA_TEXT, reminder.text).putExtra(EXTRA_ID, index)
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or if (reminder == null) PendingIntent.FLAG_NO_CREATE else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getBroadcast(context, REQUEST_BASE + index, intent, flags)
    }

    fun cancelAll(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        repeat(MAX_REMINDERS) { i -> pending(context, i, null)?.let { alarms.cancel(it); it.cancel() } }
    }

    /** Replaces all scheduled reminders with those for [plan]. Only the open trip gets reminders. */
    fun schedule(context: Context, plan: DayPlan, buffer: Duration, now: Instant) {
        cancelAll(context)
        if (!canNotify(context)) return
        ensureChannel(context)
        val alarms = context.getSystemService(AlarmManager::class.java)
        reminders(context, plan, buffer, now).forEachIndexed { i, r ->
            pending(context, i, r)?.let { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at.toEpochMilli(), it) }
        }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    // canNotify() checks POST_NOTIFICATIONS first; SecurityException is caught below as well.
    @SuppressLint("MissingPermission")
    internal fun post(context: Context, intent: Intent) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_weather_clear_day)
            .setContentTitle(intent.getStringExtra(EXTRA_TITLE))
            .setContentText(intent.getStringExtra(EXTRA_TEXT))
            .setStyle(NotificationCompat.BigTextStyle().bigText(intent.getStringExtra(EXTRA_TEXT)))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFY_BASE + intent.getIntExtra(EXTRA_ID, 0), n)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    private const val REQUEST_BASE = 4100
    private const val NOTIFY_BASE = 4200
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = TripReminders.post(context, intent)
}
