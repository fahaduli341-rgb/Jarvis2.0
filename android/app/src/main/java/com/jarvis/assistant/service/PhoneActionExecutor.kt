package com.jarvis.assistant.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import androidx.core.content.ContextCompat
import com.jarvis.assistant.data.ActionType
import com.jarvis.assistant.data.PendingPhoneAction

data class ActionExecutionResult(
    val executed: Boolean,
    val spokenFeedback: String
)

class PhoneActionExecutor(private val context: Context) {

    fun execute(action: PendingPhoneAction): ActionExecutionResult {
        return when (action.type) {
            ActionType.OPEN_APP -> openAppByName(action.target)
            ActionType.LAUNCH_DIALER -> launchDialer(action.target)
            ActionType.OPEN_WEBSITE -> openWebsite(action.target)
            ActionType.START_NAVIGATION -> startNavigation(action.target)
            ActionType.SET_ALARM -> setAlarm(action.hour, action.minute, action.target)
        }
    }

    private fun openAppByName(appName: String): ActionExecutionResult {
        val query = appName.trim().lowercase()
        if (query.isEmpty()) {
            return ActionExecutionResult(false, "I need an app name to open.")
        }

        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcherIntent, 0)

        val exactMatch = apps.firstOrNull {
            it.loadLabel(pm).toString().lowercase() == query
        }
        val partialMatch = exactMatch ?: apps.firstOrNull {
            val label = it.loadLabel(pm).toString().lowercase()
            label.contains(query) || query.contains(label)
        }

        if (partialMatch == null) {
            return ActionExecutionResult(
                false,
                "I could not find an installed app matching $appName on this phone."
            )
        }

        val packageName = partialMatch.activityInfo.packageName
        val launchIntent = pm.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return ActionExecutionResult(
            false,
            "Android did not allow launching $appName."
        )

        return try {
            context.startActivity(launchIntent)
            val resolvedName = partialMatch.loadLabel(pm).toString()
            ActionExecutionResult(true, "Opening $resolvedName.")
        } catch (_: Exception) {
            ActionExecutionResult(false, "I was unable to launch $appName.")
        }
    }

    private fun launchDialer(phoneNumber: String): ActionExecutionResult {
        val sanitized = phoneNumber.replace(Regex("[^0-9+*#]"), "")
        val uri = if (sanitized.isNotEmpty()) Uri.parse("tel:$sanitized") else Uri.parse("tel:")
        val intent = Intent(Intent.ACTION_DIAL, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            if (sanitized.isNotEmpty()) {
                ActionExecutionResult(true, "Opening the dialer with $sanitized ready.")
            } else {
                ActionExecutionResult(true, "Opening the phone dialer.")
            }
        } catch (_: Exception) {
            ActionExecutionResult(false, "No dialer app is available to handle that request.")
        }
    }

    private fun openWebsite(rawUrl: String): ActionExecutionResult {
        val normalized = rawUrl.trim().let {
            when {
                it.startsWith("https://", ignoreCase = true) -> it
                it.startsWith("http://", ignoreCase = true) -> "https://" + it.removePrefix("http://")
                else -> "https://$it"
            }
        }

        val parsed = runCatching { Uri.parse(normalized) }.getOrNull()
        if (parsed == null || (parsed.scheme != "https" && parsed.scheme != "http") || parsed.host.isNullOrBlank()) {
            return ActionExecutionResult(false, "That does not look like a valid web address.")
        }

        val intent = Intent(Intent.ACTION_VIEW, parsed).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            ActionExecutionResult(true, "Opening ${parsed.host} in your browser.")
        } catch (_: Exception) {
            ActionExecutionResult(false, "No web browser is available to open that website.")
        }
    }

    private fun startNavigation(destination: String): ActionExecutionResult {
        val cleanDest = destination.trim()
        if (cleanDest.isEmpty()) {
            return ActionExecutionResult(false, "Please tell me the destination you want to navigate to.")
        }

        val navUri = Uri.parse("google.navigation:q=${Uri.encode(cleanDest)}")
        val navIntent = Intent(Intent.ACTION_VIEW, navUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (navIntent.resolveActivity(context.packageManager) != null) {
            return try {
                context.startActivity(navIntent)
                ActionExecutionResult(true, "Starting navigation to $cleanDest.")
            } catch (_: Exception) {
                ActionExecutionResult(false, "Could not start navigation to $cleanDest.")
            }
        }

        val fallbackUri = Uri.parse("geo:0,0?q=${Uri.encode(cleanDest)}")
        val fallbackIntent = Intent(Intent.ACTION_VIEW, fallbackUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(fallbackIntent)
            ActionExecutionResult(true, "Opening maps for $cleanDest.")
        } catch (_: Exception) {
            ActionExecutionResult(false, "No maps or navigation app is installed on this device.")
        }
    }

    private fun setAlarm(hour: Int?, minute: Int?, label: String): ActionExecutionResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SET_ALARM)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return ActionExecutionResult(false, "The set alarm permission is missing.")
        }

        if (hour == null || hour !in 0..23 || minute == null || minute !in 0..59) {
            return ActionExecutionResult(false, "I need a valid hour and minute to set an alarm.")
        }

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            if (label.isNotBlank()) {
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
            }
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            val formattedMinute = minute.toString().padStart(2, '0')
            ActionExecutionResult(true, "Alarm set for $hour:$formattedMinute.")
        } catch (_: Exception) {
            ActionExecutionResult(false, "Your clock app did not allow setting an alarm automatically.")
        }
    }
}
