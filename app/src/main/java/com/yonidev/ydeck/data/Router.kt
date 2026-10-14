package com.yonidev.ydeck.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Browser as AndroidBrowser
import android.widget.Toast

/**
 * Opens a project link in the right account:
 * - Google services go through Google's AccountChooser with the project's email, so the right signed-in account is used.
 * - Vercel opens in the Chrome channel (Chrome / Beta / Dev / Canary) that holds that Vercel account's session.
 * - Everything else opens in regular Chrome.
 */
object Router {
    const val CHROME = "com.android.chrome"

    fun open(context: Context, project: Project, action: DeckAction) {
        val pkg = browserFor(project, action).pkg
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl(project, action)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(AndroidBrowser.EXTRA_CREATE_NEW_TAB, true)
        try {
            context.startActivity(Intent(view).setPackage(pkg))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "${browserFor(project, action).label} לא מותקן, נפתח בדפדפן ברירת המחדל", Toast.LENGTH_SHORT).show()
            runCatching { context.startActivity(view) }
        }
    }

    fun browserFor(project: Project, action: DeckAction): Browser =
        if (action.service.route == Route.VERCEL && project.vercel != null) project.vercel
        else Browser(CHROME, "Chrome")

    fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** The address actually opened: Google services go through the account chooser for the project's email. */
    fun targetUrl(project: Project, action: DeckAction): String =
        if (action.service.route == Route.GOOGLE && project.googleEmail != null) Links.accountChooser(project.googleEmail, action.url)
        else action.url

    /** Short "host/path" for display. */
    fun displayUrl(url: String): String = Links.display(url)
}
