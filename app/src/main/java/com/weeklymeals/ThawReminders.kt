package com.weeklymeals

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** When a thaw reminder goes off: [before] 0 = morning of, 1 = night before, 2 = 2 nights before; [minute] of the day. */
@Serializable
data class ThawTime(val before: Int = 1, val minute: Int = 20 * 60)

/** Something to move from the freezer to the fridge for a meal. */
@Serializable
data class ThawItem(val name: String, val amount: String, val meal: String)

/** A day with meals that use freezer meat. [date] is "yyyy-MM-dd". */
@Serializable
data class ThawDay(val date: String, val items: List<ThawItem>)

/** What the alarms need, saved where the receivers can read it without the app open. */
@Serializable
data class ThawPlan(
    val on: Boolean = false,
    val times: List<ThawTime> = emptyList(),
    val beat: String = "popcorn",
    val snooze: Int = 10,
    val days: List<ThawDay> = emptyList(),
)

/**
 * Thaw reminders: a notification (lock screen and a banner at the top) before meals that use meat
 * from the freezer, buzzing a food beat. It stays until you tap Moved it or Snooze.
 */
object Thaw {
    class Beat(val key: String, val emoji: String, val name: String, val about: String, val pattern: LongArray)

    private fun taps(n: Int, on: Long, off: Long): LongArray = LongArray(n * 2) { if (it % 2 == 0) on else off }

    /** Vibration patterns: wait, buzz, wait, buzz, ... in milliseconds. */
    val BEATS = listOf(
        Beat("popcorn", "🍿", "Popcorn", "Slow pops that speed up", longArrayOf(0, 60, 500, 60, 350, 60, 220, 60, 130, 60, 80, 60, 60, 60, 60, 60, 250, 250)),
        Beat("bell", "🔔", "Dinner bell", "Three even rings", longArrayOf(0, 350, 250, 350, 250, 350)),
        Beat("chop", "🔪", "Chop chop", "Quick steady knife chops", longArrayOf(0) + taps(6, 50, 90) + longArrayOf(300) + taps(6, 50, 90)),
        Beat("sizzle", "🍳", "Sizzle", "A fast flutter ending in a long hiss", longArrayOf(0) + taps(10, 30, 40) + longArrayOf(900)),
        Beat("microwave", "⏲️", "Microwave", "Three spaced beeps", longArrayOf(0, 450, 450, 450, 450, 450)),
        Beat("drip", "💧", "Thaw drip", "Slow drips getting slower", longArrayOf(0, 80, 300, 80, 500, 80, 800, 80, 1100, 80)),
        Beat("toaster", "🍞", "Toaster", "A long hum, then a pop", longArrayOf(0, 1000, 600, 150)),
        Beat("egg", "🥚", "Egg crack", "Tap, tap, crack", longArrayOf(0, 60, 250, 60, 250, 350)),
        Beat("kettle", "🫖", "Kettle", "Builds up to a whistle", longArrayOf(0, 100, 400, 150, 300, 200, 200, 300, 100, 800)),
        Beat("boil", "🫧", "Boiling pot", "Bubbles popping at random", longArrayOf(0, 40, 150, 70, 90, 40, 200, 90, 80, 50, 130, 40, 100, 80)),
        Beat("soda", "🥤", "Soda fizz", "A psst, then fizz", longArrayOf(0, 400, 150) + taps(10, 25, 35)),
        Beat("taco", "🌮", "Taco night", "Da-da-DUM, da-da-DUM", longArrayOf(0, 100, 100, 100, 100, 500, 400, 100, 100, 100, 100, 500)),
    )

    fun beat(key: String): Beat = BEATS.firstOrNull { it.key == key } ?: BEATS.first()

    val WHEN = listOf("Morning of", "Night before", "2 nights before")

    fun timeText(minute: Int): String = LocalTime.of(minute / 60, minute % 60).format(DateTimeFormatter.ofPattern("h:mm a"))

    fun label(t: ThawTime): String = WHEN.getOrElse(t.before) { WHEN[1] } + " " + timeText(t.minute)

    private const val PREFS = "thaw"
    private const val CHANNEL_PREFIX = "thaw_"
    private const val NOTIFICATION_ID = 7100
    const val ACTION_FIRE = "com.weeklymeals.thaw.FIRE"
    const val ACTION_DONE = "com.weeklymeals.thaw.DONE"
    const val ACTION_SNOOZE = "com.weeklymeals.thaw.SNOOZE"
    private const val EXTRA_AT = "at"
    private const val EXTRA_DATES = "dates"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): ThawPlan =
        prefs(ctx).getString("plan", null)?.let { runCatching { json.decodeFromString(ThawPlan.serializer(), it) }.getOrNull() } ?: ThawPlan()

    /** Saves the plan (when the week or settings change) and sets the next alarm. */
    fun update(ctx: Context, plan: ThawPlan) {
        prefs(ctx).edit().putString("plan", json.encodeToString(ThawPlan.serializer(), plan)).apply()
        reschedule(ctx)
    }

    private fun done(ctx: Context): Set<String> = prefs(ctx).getStringSet("done", emptySet()).orEmpty()

    private fun markDone(ctx: Context, dates: Collection<String>) {
        val cutoff = LocalDate.now().minusDays(14).toString()
        val keep = (done(ctx) + dates).filter { it >= cutoff }.toSet()
        prefs(ctx).edit().putStringSet("done", keep).apply()
    }

    /** When a reminder for [day] goes off, in milliseconds. */
    private fun fireAt(day: ThawDay, t: ThawTime): Long? = runCatching {
        LocalDate.parse(day.date).minusDays(t.before.toLong()).atTime(t.minute / 60, t.minute % 60)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()

    /** The next reminder: when it goes off and the days it's for (null if none). */
    fun next(ctx: Context, plan: ThawPlan = load(ctx)): Pair<Long, List<ThawDay>>? {
        if (!plan.on) return null
        val now = System.currentTimeMillis()
        val done = done(ctx)
        val fires = plan.days.filter { it.date !in done }.flatMap { d -> plan.times.mapNotNull { t -> fireAt(d, t)?.let { it to d } } }
            .filter { it.first > now }
        val at = fires.minOfOrNull { it.first } ?: return null
        return at to fires.filter { it.first == at }.map { it.second }.distinctBy { it.date }
    }

    private fun alarm(ctx: Context, code: Int, i: Intent) =
        PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun setAlarm(ctx: Context, at: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun reschedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = alarm(ctx, NOTIFICATION_ID, Intent(ctx, ThawReceiver::class.java).setAction(ACTION_FIRE))
        am.cancel(pi)
        val (at, _) = next(ctx) ?: return
        setAlarm(ctx, at, alarm(ctx, NOTIFICATION_ID, Intent(ctx, ThawReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_AT, at)))
    }

    internal fun onFire(ctx: Context, at: Long) {
        val plan = load(ctx)
        if (plan.on) {
            val done = done(ctx)
            val days = plan.days.filter { d -> d.date !in done && plan.times.any { fireAt(d, it) == at } }
            if (days.isNotEmpty()) show(ctx, plan, days)
        }
        reschedule(ctx)
    }

    internal fun onSnoozeEnd(ctx: Context, dates: List<String>) {
        val plan = load(ctx)
        val done = done(ctx)
        val days = plan.days.filter { it.date in dates && it.date !in done }
        if (plan.on && days.isNotEmpty()) show(ctx, plan, days)
    }

    internal fun onDone(ctx: Context, dates: List<String>) {
        markDone(ctx, dates)
        NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_ID)
        ctx.getSystemService(AlarmManager::class.java).cancel(snoozeAlarm(ctx, emptyList()))
        reschedule(ctx)
    }

    internal fun onSnooze(ctx: Context, dates: List<String>) {
        NotificationManagerCompat.from(ctx).cancel(NOTIFICATION_ID)
        val minutes = load(ctx).snooze.coerceAtLeast(1)
        setAlarm(ctx, System.currentTimeMillis() + minutes * 60_000L, snoozeAlarm(ctx, dates))
    }

    private fun snoozeAlarm(ctx: Context, dates: List<String>) = alarm(
        ctx, NOTIFICATION_ID + 1,
        Intent(ctx, ThawReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_DATES, dates.toTypedArray()),
    )

    /** One channel per beat: Android fixes a channel's vibration when it's made. Others are removed. */
    private fun channel(ctx: Context, b: Beat): String {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val id = CHANNEL_PREFIX + b.key
        nm.notificationChannels.filter { it.id.startsWith(CHANNEL_PREFIX) && it.id != id }.forEach { nm.deleteNotificationChannel(it.id) }
        if (nm.getNotificationChannel(id) == null) {
            nm.createNotificationChannel(
                NotificationChannel(id, "Thaw reminders (${b.name})", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Reminds you to move freezer meat to the fridge before a meal"
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
                    )
                    enableVibration(true)
                    vibrationPattern = b.pattern
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                },
            )
        }
        return id
    }

    /** "tonight" / "this morning" style title for when the meal is. */
    private fun title(days: List<ThawDay>): String {
        val first = days.minOf { it.date }.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return "❄️ Move to the fridge"
        val today = LocalDate.now()
        val evening = LocalTime.now().hour >= 12
        return when {
            first <= today -> "❄️ Move to the fridge now"
            first == today.plusDays(1) && evening -> "❄️ Move to the fridge tonight"
            else -> "❄️ Move to the fridge"
        }
    }

    private fun dayName(date: String): String =
        runCatching { Logic.DAY_SHORT[LocalDate.parse(date).dayOfWeek.value - 1] }.getOrDefault("")

    /** Posts the reminder. A [test] one's buttons only close it (they don't mark meals as moved). */
    fun show(ctx: Context, plan: ThawPlan, days: List<ThawDay>, test: Boolean = false) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val dates = if (test) emptyList() else days.map { it.date }
        val lines = days.flatMap { d -> d.items.map { "${it.name} (${it.amount}) for ${dayName(d.date)} · ${it.meal}" } }
        fun action(code: Int, action: String) = PendingIntent.getBroadcast(
            ctx, code, Intent(ctx, ThawReceiver::class.java).setAction(action).putExtra(EXTRA_DATES, dates.toTypedArray()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            ctx, NOTIFICATION_ID, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, channel(ctx, beat(plan.beat)))
            .setSmallIcon(R.drawable.ic_fg_mono)
            .setContentTitle(title(days))
            .setContentText(lines.joinToString(" · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(open)
            .addAction(0, "Moved it", action(NOTIFICATION_ID + 2, ACTION_DONE))
            .addAction(0, "Snooze ${plan.snooze} min", action(NOTIFICATION_ID + 3, ACTION_SNOOZE))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, n)
        } catch (_: SecurityException) {
        }
    }

    /** Plays a beat on the phone right now (the Try it button). */
    fun tryBeat(ctx: Context, b: Beat) {
        val v = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
        }
        runCatching { v?.vibrate(VibrationEffect.createWaveform(b.pattern, -1)) }
    }

    fun datesFrom(i: Intent): List<String> = i.getStringArrayExtra(EXTRA_DATES)?.toList().orEmpty()
    fun atFrom(i: Intent): Long = i.getLongExtra(EXTRA_AT, 0L)
}

class ThawReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> Thaw.reschedule(context)
            Thaw.ACTION_DONE -> Thaw.onDone(context, Thaw.datesFrom(intent))
            Thaw.ACTION_SNOOZE -> Thaw.onSnooze(context, Thaw.datesFrom(intent))
            Thaw.ACTION_FIRE -> {
                val dates = Thaw.datesFrom(intent)
                if (dates.isNotEmpty()) Thaw.onSnoozeEnd(context, dates) else Thaw.onFire(context, Thaw.atFrom(intent))
            }
        }
    }
}

/** The inside of Settings → Thaw reminders. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.ThawSettings(vm: MealsViewModel, st: UserState) {
    val ctx = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val lime = MaterialTheme.colorScheme.primary
    var picking by remember { mutableStateOf<Int?>(null) }
    var allowed by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }

    @Composable
    fun Label(text: String) = Text(
        text.uppercase(), style = MaterialTheme.typography.labelSmall, color = muted,
        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp),
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Remind me to thaw", style = MaterialTheme.typography.bodyLarge)
            Text("For meals using meat from your freezer", style = MaterialTheme.typography.bodySmall, color = muted)
        }
        Switch(checked = st.thawOn, onCheckedChange = {
            vm.setThawOn(it)
            if (it && !allowed) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        })
    }
    if (st.thawOn && !allowed) {
        OutlinedButton(onClick = { ask.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth()) {
            Text("Allow notifications so reminders can show")
        }
    }
    if (!st.thawOn) return

    Label("When")
    st.thawTimes.forEachIndexed { i, t ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            var menu by remember { mutableStateOf(false) }
            Box {
                Text(
                    Thaw.WHEN.getOrElse(t.before) { Thaw.WHEN[1] } + " ▾",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { menu = true }.padding(horizontal = 10.dp, vertical = 7.dp),
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    Thaw.WHEN.forEachIndexed { b, name ->
                        DropdownMenuItem(text = { Text(name) }, onClick = {
                            menu = false
                            vm.setThawTimes(st.thawTimes.mapIndexed { j, x -> if (j == i) x.copy(before = b) else x })
                        })
                    }
                }
            }
            Text(
                Thaw.timeText(t.minute), color = lime, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clickable { picking = i }.padding(4.dp),
            )
            Spacer(Modifier.weight(1f))
            if (st.thawTimes.size > 1) {
                Text("✕", color = muted, modifier = Modifier.clickable { vm.setThawTimes(st.thawTimes.filterIndexed { j, _ -> j != i }) }.padding(8.dp))
            }
        }
    }
    TextButton(onClick = { vm.setThawTimes(st.thawTimes + ThawTime(before = 0, minute = 7 * 60)) }) { Text("+ Add a reminder") }
    Text(
        "Tap a time to change it. A later reminder for the same meal is skipped once you tap Moved it.",
        style = MaterialTheme.typography.bodySmall, color = muted,
    )

    Label("Buzz beat")
    Thaw.BEATS.forEach { b ->
        Row(
            Modifier.fillMaxWidth().clickable { vm.setThawBeat(b.key); Thaw.tryBeat(ctx, b) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = st.thawBeat == b.key, onClick = { vm.setThawBeat(b.key); Thaw.tryBeat(ctx, b) })
            Text(b.emoji, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(b.name, style = MaterialTheme.typography.bodyMedium)
                Text(b.about, style = MaterialTheme.typography.bodySmall, color = muted)
            }
            TextButton(onClick = { Thaw.tryBeat(ctx, b) }) { Text("Try it") }
        }
    }
    Text(
        "Follows your phone's sound settings: on vibrate it only buzzes, on silent it stays quiet.",
        style = MaterialTheme.typography.bodySmall, color = muted,
    )

    Label("Snooze for")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(5, 10, 30).forEach { m ->
            PillChip("$m min", st.thawSnooze == m) { vm.setThawSnooze(m) }
        }
    }

    Label("Next reminder")
    val next = remember(st) { vm.thawPlan(st).let { Thaw.next(ctx, it) } }
    Text(
        next?.let { (at, days) ->
            val time = java.time.Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
            Logic.DAY_SHORT[time.dayOfWeek.value - 1] + " " + Thaw.timeText(time.hour * 60 + time.minute) + " · " +
                days.flatMap { d -> d.items.map { it.name } }.distinct().joinToString(", ")
        } ?: "None planned. Plan a meal that uses meat from your freezer.",
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedButton(
        onClick = {
            if (!allowed) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
            else {
                val plan = vm.thawPlan(st)
                val days = next?.second ?: listOf(ThawDay(LocalDate.now().plusDays(1).toString(), listOf(ThawItem("Chicken breast", "3 pieces", "Example meal"))))
                Thaw.show(ctx, plan, days, test = true)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Send a test reminder now") }

    picking?.let { i ->
        val t = st.thawTimes.getOrNull(i) ?: return@let
        val state = rememberTimePickerState(initialHour = t.minute / 60, initialMinute = t.minute % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    vm.setThawTimes(st.thawTimes.mapIndexed { j, x -> if (j == i) x.copy(minute = state.hour * 60 + state.minute) else x })
                    picking = null
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}
