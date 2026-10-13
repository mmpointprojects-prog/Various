package com.mmpoint.spotter.notify

import android.Manifest
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
import com.mmpoint.spotter.MainActivity
import com.mmpoint.spotter.R
import com.mmpoint.spotter.data.Actions
import com.mmpoint.spotter.data.AppData
import com.mmpoint.spotter.data.Store
import com.mmpoint.spotter.data.SuppKind
import com.mmpoint.spotter.logic.Planner
import com.mmpoint.spotter.logic.TimeUtil
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Every reminder is an exact alarm keyed by a string:
 *   morning, evening, weigh, snack, gym, rest, dose|<suppId>|<HH:mm>, snooze|<suppId>|<HH:mm>|<date>
 * After any alarm fires (or data changes) [rescheduleAll] recomputes the next occurrence of each.
 */
object Reminders {
    const val CH_CHECKIN = "checkin"
    const val CH_DOSE = "dose"
    const val CH_REST = "rest"
    const val CH_WORKOUT = "workout"

    const val EXTRA_KEY = "key"
    const val EXTRA_ROUTE = "route"
    private const val PREFS = "alarms"
    private const val TRACKED = "tracked"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_CHECKIN, "Daily check-ins & weigh-ins", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CH_DOSE, "Peptide & supplement reminders", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CH_REST, "Rest timer", NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true); vibrationPattern = longArrayOf(0, 400, 200, 400)
                },
                NotificationChannel(CH_WORKOUT, "Snack & gym reminders", NotificationManager.IMPORTANCE_HIGH),
            )
        )
    }

    private fun code(key: String) = key.hashCode()

    private fun alarmIntent(ctx: Context, key: String): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction("spotter.ALARM.$key").putExtra(EXTRA_KEY, key)
        return PendingIntent.getBroadcast(ctx, code(key), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun schedule(ctx: Context, key: String, at: LocalDateTime) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = alarmIntent(ctx, key)
        val exactOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exactOk) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
    }

    fun cancel(ctx: Context, key: String) {
        ctx.getSystemService(AlarmManager::class.java).cancel(alarmIntent(ctx, key))
    }

    fun scheduleRest(ctx: Context, seconds: Int, label: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("restLabel", label).apply()
        schedule(ctx, "rest", LocalDateTime.now().plusSeconds(seconds.toLong()))
    }

    fun cancelRest(ctx: Context) {
        cancel(ctx, "rest")
        NotificationManagerCompat.from(ctx).cancel(code("rest"))
    }

    private fun nextAt(time: LocalTime, now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate().atTime(time)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    /** Compute the next fire time for every recurring reminder. */
    fun plan(data: AppData, now: LocalDateTime = LocalDateTime.now()): Map<String, LocalDateTime> {
        val s = data.settings
        val out = mutableMapOf<String, LocalDateTime>()
        if (!s.onboarded) return out
        if (s.morningEnabled) TimeUtil.parse(s.morningTime)?.let { out["morning"] = nextAt(it, now) }
        if (s.eveningEnabled) TimeUtil.parse(s.eveningTime)?.let { out["evening"] = nextAt(it, now) }
        if (s.weighEnabled) TimeUtil.parse(s.weighTime)?.let { t ->
            var d = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.of(s.weighDay))).atTime(t)
            if (!d.isAfter(now)) d = d.plusWeeks(1)
            out["weigh"] = d
        }
        val today = now.toLocalDate()
        val morning = Planner.morningCheckIn(data, today)
        val gym = TimeUtil.parse(morning?.gymTime)
        if (morning?.wantsWorkout == true && gym != null) {
            Planner.snackTime(gym, s.snackLeadMin)?.let { snack ->
                val at = today.atTime(snack)
                if (at.isAfter(now)) out["snack"] = at
            }
            val leave = today.atTime(gym.minusMinutes(s.gymLeadMin.toLong()))
            if (s.gymLeadMin > 0 && leave.isAfter(now)) out["gym"] = leave
        }
        for (supp in data.supplements.filter { it.active }) {
            for (t in supp.times) {
                val lt = TimeUtil.parse(t) ?: continue
                for (offset in 0L..370L) {
                    val date: LocalDate = today.plusDays(offset)
                    val at = date.atTime(lt)
                    val logged = data.doseLogs.any { it.supplementId == supp.id && it.date == date.toString() && it.time == t }
                    if (at.isAfter(now) && !logged && Planner.isSuppDue(supp, date)) {
                        out["dose|${supp.id}|$t"] = at
                        break
                    }
                }
            }
        }
        return out
    }

    fun rescheduleAll(ctx: Context) {
        Store.init(ctx)
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getStringSet(TRACKED, emptySet()) ?: emptySet()
        val planned = plan(Store.current)
        (previous - planned.keys).forEach { cancel(ctx, it) }
        planned.forEach { (k, at) -> schedule(ctx, k, at) }
        prefs.edit().putStringSet(TRACKED, planned.keys).apply()
    }

    fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(ctx: Context, route: String, req: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .putExtra(EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun notify(ctx: Context, id: Int, channel: String, title: String, text: String, route: String,
               build: (NotificationCompat.Builder) -> Unit = {}) {
        if (!canNotify(ctx)) return
        val b = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, route, id))
        build(b)
        try {
            NotificationManagerCompat.from(ctx).notify(id, b.build())
        } catch (_: SecurityException) {
        }
    }

    /** Called by [AlarmReceiver]. */
    fun fire(ctx: Context, key: String) {
        Store.init(ctx)
        val data = Store.current
        val today = LocalDate.now()
        val todayDay = Planner.dayFor(data, today)
        when {
            key == "morning" -> if (Planner.morningCheckIn(data, today) == null) notify(
                ctx, code(key), CH_CHECKIN, "Good morning — how do you feel?",
                "Quick check-in: sleep, soreness, are you training today, and what time will you be at the gym?" +
                    (todayDay?.let { " Today's plan: ${it.name}." } ?: " Today is a rest day."),
                "checkin/MORNING",
            )
            key == "evening" -> if (Planner.eveningCheckIn(data, today) == null) notify(
                ctx, code(key), CH_CHECKIN, "Evening check-in",
                "How did today go? I'll suggest what to do for recovery tonight.", "checkin/EVENING",
            )
            key == "weigh" -> notify(
                ctx, code(key), CH_CHECKIN, "Weekly weigh-in",
                "Step on the scale (same conditions as last week: after the bathroom, before eating) and log it.", "weight",
            )
            key == "snack" -> {
                val gym = TimeUtil.pretty(Planner.morningCheckIn(data, today)?.gymTime)
                notify(
                    ctx, code(key), CH_WORKOUT, "Pre-gym snack time",
                    "Gym at $gym. Have something small with carbs + a little protein: banana + Greek yogurt, " +
                        "rice cakes + peanut butter, or toast + honey. Keep it light on fat and fiber.", "today",
                )
            }
            key == "gym" -> notify(
                ctx, code(key), CH_WORKOUT, "Gym in ${data.settings.gymLeadMin} min",
                todayDay?.let { "Today: ${it.name}. Grab water and your gear." } ?: "Grab water and your gear.", "today",
            )
            key == "test" -> notify(ctx, code(key), CH_CHECKIN, "Alarm test", "Scheduled reminders are working.", "today")
            key == "rest" -> {
                val label = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("restLabel", "") ?: ""
                notify(ctx, code(key), CH_REST, "Rest over — next set", label, "workout") {
                    it.setCategory(NotificationCompat.CATEGORY_ALARM).setTimeoutAfter(120_000)
                }
            }
            key.startsWith("dose|") || key.startsWith("snooze|") -> {
                val parts = key.split("|")
                val supp = data.supplements.firstOrNull { it.id == parts[1] } ?: return
                val time = parts[2]
                val date = parts.getOrNull(3)?.let { LocalDate.parse(it) } ?: today
                val doseText = listOf(supp.dose, supp.unit).filter { it.isNotBlank() }.joinToString(" ")
                val units = if (supp.vialMg != null && supp.waterMl != null && supp.doseMcg != null)
                    Planner.syringeUnits(supp.vialMg, supp.waterMl, supp.doseMcg)?.let { " · draw %.1f units".format(it) } ?: "" else ""
                val site = if (supp.rotateSites) " · site: ${Planner.nextSite(data, supp.id)}" else ""
                val extra = supp.instructions.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: ""
                val kind = if (supp.kind == SuppKind.PEPTIDE) "Peptide" else supp.kind.label
                val id = ("dose|${supp.id}|$time").hashCode()
                notify(ctx, id, CH_DOSE, "$kind: ${supp.name}", "$doseText$units$site$extra".trim(), "supplements") { b ->
                    b.addAction(0, "Taken", DoseActionReceiver.intent(ctx, DoseActionReceiver.TAKEN, supp.id, time, date, id))
                    b.addAction(0, "Snooze 15m", DoseActionReceiver.intent(ctx, DoseActionReceiver.SNOOZE, supp.id, time, date, id))
                    b.addAction(0, "Skip", DoseActionReceiver.intent(ctx, DoseActionReceiver.SKIP, supp.id, time, date, id))
                }
            }
        }
        if (key != "rest" && key != "test") rescheduleAll(ctx)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(Reminders.EXTRA_KEY) ?: return
        Reminders.fire(context, key)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.rescheduleAll(context)
    }
}

class DoseActionReceiver : BroadcastReceiver() {
    companion object {
        const val TAKEN = "spotter.DOSE_TAKEN"
        const val SNOOZE = "spotter.DOSE_SNOOZE"
        const val SKIP = "spotter.DOSE_SKIP"

        fun intent(ctx: Context, action: String, suppId: String, time: String, date: LocalDate, notifId: Int): PendingIntent {
            val i = Intent(ctx, DoseActionReceiver::class.java).setAction(action)
                .putExtra("supp", suppId).putExtra("time", time).putExtra("date", date.toString()).putExtra("nid", notifId)
            return PendingIntent.getBroadcast(ctx, (action + suppId + time).hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val supp = intent.getStringExtra("supp") ?: return
        val time = intent.getStringExtra("time") ?: return
        val date = LocalDate.parse(intent.getStringExtra("date") ?: LocalDate.now().toString())
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra("nid", 0))
        when (intent.action) {
            TAKEN -> {
                Actions.logDose(supp, date, time)
                val s = Store.current.supplements.firstOrNull { it.id == supp }
                if (s?.remainingDoses != null && s.remainingDoses <= 3) Reminders.notify(
                    context, ("low$supp").hashCode(), Reminders.CH_DOSE, "Running low: ${s.name}",
                    "${s.remainingDoses} doses left. Time to reorder or mix a new vial.", "supplements",
                )
            }
            SKIP -> Actions.logDose(supp, date, time, skipped = true)
            SNOOZE -> Reminders.schedule(context, "snooze|$supp|$time|$date", LocalDateTime.now().plusMinutes(15))
        }
        Reminders.rescheduleAll(context)
    }
}
