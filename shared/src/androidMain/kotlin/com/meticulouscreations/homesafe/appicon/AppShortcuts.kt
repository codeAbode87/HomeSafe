package com.meticulouscreations.homesafe.appicon

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Icon
import android.util.Log
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.net.toUri
import com.meticulouscreations.homesafe.PlatformContext
import com.meticulouscreations.homesafe.data.AlertNotificationPoster
import com.meticulouscreations.homesafe.finance.ui.FinanceTab
import com.meticulouscreations.homesafe.navigation.AppDeepLink
import com.meticulouscreations.homesafe.navigation.DoorDeepLink
import com.meticulouscreations.homesafe.navigation.FinanceDeepLink
import com.meticulouscreations.homesafe.navigation.FitnessDeepLink
import com.meticulouscreations.homesafe.navigation.WeatherDeepLink
import com.meticulouscreations.homesafe.shared.R
import com.meticulouscreations.homesafe.ui.screens.InnerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The shortcuts a long press on the launcher icon offers (Workout, Radar, Budget and Doors), plus
 * pinning any of PercySafe's apps straight onto the home screen as a one-tap icon. Each opens the
 * same deep link a notification of that kind opens (MainActivity hands it to the shell, or to the
 * sign-in screen's app layer before sign-in so server-free apps open straight away).
 *
 * Dynamic rather than declared in the manifest because a shortcut belongs to one launcher entry,
 * and which one is enabled depends on the icon picked (see [AppIconSwitcher]): published again,
 * on the alias now enabled, every time the app starts and whenever the icon changes. The intents
 * name MainActivity itself, like [appLaunchIntent], so no alias switch can strand one.
 */
object AppShortcuts {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private class Shortcut(
        val id: String,
        @param:StringRes val label: Int,
        @param:DrawableRes val glyph: Int,
        val uri: String,
        @param:ColorInt val background: Int = AlertNotificationPoster.ACCENT_COLOR,
    )

    private val shortcuts = listOf(
        Shortcut("workout", R.string.shortcut_workout, R.drawable.ic_notification_workout, FitnessDeepLink.Workout.toUri()),
        Shortcut("radar", R.string.shortcut_radar, R.drawable.ic_notification_weather_rain, WeatherDeepLink(view = WeatherDeepLink.View.RADAR).toUri()),
        Shortcut("budget", R.string.shortcut_budget, R.drawable.ic_notification_budget, FinanceDeepLink(FinanceTab.BUDGET).toUri()),
        Shortcut("doors", R.string.shortcut_doors, R.drawable.ic_notification_door, DoorDeepLink().toUri()),
    )

    private fun pinnable(app: InnerApp): Shortcut = when (app) {
        InnerApp.WEATHER -> Shortcut(
            id = "pin_weather",
            label = R.string.shortcut_weather,
            glyph = R.drawable.ic_notification_weather_sun,
            uri = AppDeepLink.uriFor(app),
            background = Color.parseColor("#1F6BD6"),
        )

        InnerApp.FITNESS -> Shortcut(
            id = "pin_fitness",
            label = R.string.shortcut_fitness,
            glyph = R.drawable.ic_notification_workout,
            uri = AppDeepLink.uriFor(app),
            background = Color.parseColor("#D9441A"),
        )

        InnerApp.FINANCE -> Shortcut(
            id = "pin_finance",
            label = R.string.shortcut_finance,
            glyph = R.drawable.ic_notification_budget,
            uri = AppDeepLink.uriFor(app),
            background = Color.parseColor("#1B8A5A"),
        )

        InnerApp.PACKAGES -> Shortcut(
            id = "pin_packages",
            label = R.string.shortcut_packages,
            glyph = R.drawable.ic_notification_parcel,
            uri = AppDeepLink.uriFor(app),
            background = Color.parseColor("#C27815"),
        )

        InnerApp.ATLAS -> Shortcut(
            id = "pin_atlas",
            label = R.string.shortcut_atlas,
            glyph = R.drawable.ic_shortcut_atlas,
            uri = AppDeepLink.uriFor(app),
            background = Color.parseColor("#1A7F8A"),
        )

        InnerApp.CHARGING -> Shortcut(
            id = "pin_charging",
            label = R.string.shortcut_charging,
            glyph = R.drawable.ic_shortcut_charging,
            uri = AppDeepLink.uriFor(app),
            background = Color.parseColor("#2E7D6F"),
        )
    }

    /** Whether the current launcher allows pinning standalone app shortcuts onto the home screen. */
    fun canPin(context: Context): Boolean =
        context.getSystemService(ShortcutManager::class.java)?.isRequestPinShortcutSupported == true

    /**
     * Asks the launcher to pin [app] onto the home screen as a standalone one-tap icon. The intent
     * aims at MainActivity itself rather than an icon alias, so switching HomeSafe's icon never
     * strands a pinned app shortcut.
     */
    fun pin(context: Context, app: InnerApp): Boolean = runCatching {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!manager.isRequestPinShortcutSupported) return false
        val target = context.appLaunchIntent()?.component ?: return false
        val shortcut = pinnable(app)
        val label = context.getString(shortcut.label)
        val info = ShortcutInfo.Builder(context, shortcut.id)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(icon(context, shortcut.glyph, shortcut.background))
            .setIntent(Intent(Intent.ACTION_VIEW, shortcut.uri.toUri()).setComponent(target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            .build()
        manager.requestPinShortcut(info, null)
    }.onFailure { Log.w(TAG, "couldn't pin shortcut for $app", it) }.getOrDefault(false)

    /**
     * Publishes the shortcuts on [launcherAlias] (the launcher entry enabled now; by default the
     * one the launcher's own intent names), off the main thread, so a cold start never waits on
     * it. Skipped when the launcher already has them on that entry.
     */
    fun publish(context: Context, launcherAlias: ComponentName? = null) {
        val app = context.applicationContext
        scope.launch {
            runCatching { publishNow(app, launcherAlias) }.onFailure { Log.w(TAG, "couldn't publish the launcher shortcuts", it) }
        }
    }

    private fun publishNow(context: Context, launcherAlias: ComponentName?) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val alias = launcherAlias ?: context.packageManager.getLaunchIntentForPackage(context.packageName)?.component ?: return
        val target = context.appLaunchIntent()?.component ?: return
        val current = manager.dynamicShortcuts
        val upToDate = current.map { it.id } == shortcuts.map { it.id } && current.all { it.activity == alias && it.intent?.component == target }
        if (upToDate) return
        manager.setDynamicShortcuts(
            shortcuts.map { shortcut ->
                val label = context.getString(shortcut.label)
                ShortcutInfo.Builder(context, shortcut.id)
                    .setShortLabel(label)
                    .setLongLabel(label)
                    .setIcon(icon(context, shortcut.glyph, shortcut.background))
                    .setActivity(alias)
                    // NEW_TASK alone: the running app (singleTask) comes forward with the link rather than starting over.
                    .setIntent(Intent(Intent.ACTION_VIEW, shortcut.uri.toUri()).setComponent(target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    .build()
            },
        )
    }

    /**
     * The glyph, white, on [background], as an adaptive icon: the glyphs are drawn white for
     * notifications and would vanish on a light launcher's menu or wallpaper.
     */
    private fun icon(context: Context, @DrawableRes glyph: Int, @ColorInt background: Int): Icon {
        val density = context.resources.displayMetrics.density
        val size = (ADAPTIVE_SIZE_DP * density).roundToInt()
        val glyphSize = (GLYPH_SIZE_DP * density).roundToInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        val inset = (size - glyphSize) / 2
        checkNotNull(context.getDrawable(glyph)).mutate().apply {
            setBounds(inset, inset, inset + glyphSize, inset + glyphSize)
            draw(canvas)
        }
        return Icon.createWithAdaptiveBitmap(bitmap)
    }

    private const val TAG = "HomeSafeShortcuts"

    /** An adaptive icon's full square; the launcher's mask keeps the middle 72dp or so. */
    private const val ADAPTIVE_SIZE_DP = 108

    private const val GLYPH_SIZE_DP = 44
}

private class AndroidAppShortcutPinner(private val context: Context) : AppShortcutPinner {
    override val supported: Boolean get() = AppShortcuts.canPin(context)

    override suspend fun pin(app: InnerApp): Boolean = withContext(Dispatchers.Default) {
        AppShortcuts.pin(context, app)
    }
}

actual fun createAppShortcutPinner(context: PlatformContext): AppShortcutPinner =
    AndroidAppShortcutPinner(context.context.applicationContext)
