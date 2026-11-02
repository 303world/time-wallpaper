package io.github.world303.timewallpaper.core.compat

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Battery-optimisation and "auto start" (自启动) helpers.
 *
 * Android has a public API to be exempted from Doze (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS),
 * but *no* public API for OEM auto-start whitelists. Every Chinese ROM ships its own activity, so
 * we probe a curated list of known component names for the current manufacturer and fall back to
 * the system app-details page when none of them resolves.
 */
object OemCompat {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Intent that shows the system "ignore battery optimisations" dialog. */
    fun batteryOptimizationIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun openBatterySettings(context: Context): Boolean {
        val candidates = listOf(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        return startFirst(context, candidates)
    }

    data class AutoStartOption(val label: String, val intent: Intent)

    /**
     * Known OEM auto-start managers, most specific first. Returned list only contains entries that
     * actually resolve on this device, so the UI never shows a dead button.
     */
    fun autoStartOptions(context: Context): List<AutoStartOption> {
        return candidates(context).filter { it.second.resolveActivity(context.packageManager) != null }
            .map { AutoStartOption(it.first, it.second) }
    }

    /** Tries the vendor auto-start screen, then falls back to the app details page. */
    fun openAutoStart(context: Context): Boolean {
        val options = autoStartOptions(context)
        for (option in options) {
            if (start(context, option.intent)) return true
        }
        return openAppDetails(context)
    }

    fun openAppDetails(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return start(context, intent)
    }

    /** Human-readable name of the ROM's auto-start screen, for the settings summary. */
    fun autoStartLabel(context: Context): String? = autoStartOptions(context).firstOrNull()?.label

    private fun start(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            false
        }
    }

    private fun startFirst(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            if (start(context, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return true
        }
        return false
    }

    private fun candidates(context: Context): List<Pair<String, Intent>> {
        val list = mutableListOf<Pair<String, Intent>>()
        fun add(label: String, pkg: String, cls: String) {
            list += label to Intent().setComponent(ComponentName(pkg, cls))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        when (Build.MANUFACTURER.lowercase()) {
            "xiaomi", "redmi", "poco" -> {
                add("小米自启动", "com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                add("小米省电策略", "com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
            }
            "huawei", "honor" -> {
                add("华为自启动", "com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
                add("华为应用启动管理", "com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")
                add("荣耀自启动", "com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
            }
            "oppo", "realme", "oneplus" -> {
                add("OPPO 自启动", "com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
                add("OPPO 自启动（新版）", "com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity")
                add("OPPO 电量管理", "com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity")
                add("一加自启动", "com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
            }
            "vivo", "iqoo" -> {
                add("vivo 自启动", "com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
                add("iQOO 自启动", "com.iqoo.secure", "com.iqoo.secure.safeguard.PurviewTabActivity")
            }
            "meizu" -> add("魅族自启动", "com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity")
            "samsung" -> {
                add("三星电池", "com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
                add("三星应用管理", "com.samsung.android.sm", "com.samsung.android.sm.ui.appmanagement.AppManagementActivity")
            }
            "lenovo", "zuk" -> add("联想自启动", "com.lenovo.security", "com.lenovo.security.privacyauth.PrivacyAuthActivity")
            "letv" -> add("乐视自启动", "com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")
            "asus" -> add("华硕自启动", "com.asus.mobilemanager", "com.asus.mobilemanager.permission.PermissionManageActivity")
            "nubia" -> add("努比亚自启动", "cn.nubia.security2", "cn.nubia.security2.activity.AutoStartActivity")
            "smartisan" -> add("锤子自启动", "com.smartisanos.security", "com.smartisanos.security.AppPermissionActivity")
        }
        return list
    }
}
