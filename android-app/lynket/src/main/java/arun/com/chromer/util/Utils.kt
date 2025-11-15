/*
 *
 *  Lynket
 *
 *  Copyright (C) 2022 Arunkumar
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

// Phase 7: Converted from Java to Kotlin
// Phase 8.7: Converted RxJava deleteCache() to Kotlin Coroutines

package arun.com.chromer.util

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.AppOpsManager
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.RecognizerIntent
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.Toast
import arun.com.chromer.R
import arun.com.chromer.browsing.customtabs.CustomTabs
import arun.com.chromer.data.common.App
import arun.com.chromer.shared.Constants
import arun.com.chromer.shared.views.IntentPickerBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NotNull
import timber.log.Timber
import java.io.File
import java.net.URL
import java.util.regex.Pattern

/**
 * Created by Arun on 17/12/2015.
 */
object Utils {

    val ANDROID_OREO = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    const val ANDROID_LOLLIPOP = true

    fun isLollipopAbove(): Boolean = true

    fun openPlayStore(context: Context, appPackageName: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName")))
        } catch (anfe: android.content.ActivityNotFoundException) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=$appPackageName")))
        }
    }

    fun findURLs(string: String?): List<String> {
        if (string == null) {
            return ArrayList()
        }
        val links = ArrayList<String>()
        val m = Pattern.compile(
            "\\b((?:[a-z][\\w-]+:(?:/{1,3}|[a-z0-9%])|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\))+(?:\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\)|[^\\s`!()\\[\\]{};:'\".,<>?«»""'']))",
            Pattern.CASE_INSENSITIVE
        ).matcher(string)

        while (m.find()) {
            var url = m.group()
            if (!url.lowercase().matches(Regex("^\\w+://.*"))) {
                url = "http://$url"
            }
            links.add(url)
        }

        return links
    }

    fun isPackageInstalled(c: Context, pkgName: String?): Boolean {
        if (pkgName == null) return false

        val pm = c.applicationContext.packageManager
        return try {
            pm.getPackageInfo(pkgName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getAppNameWithPackage(context: Context, pack: String): String {
        val pm = context.applicationContext.packageManager
        val ai: ApplicationInfo? = try {
            pm.getApplicationInfo(pack, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        return if (ai != null) pm.getApplicationLabel(ai) as String else "(unknown)"
    }

    fun getBrowserComponentForPackage(context: Context, pkg: String): ComponentName? {
        val webIntentImplicit = Intent(Intent.ACTION_VIEW, Uri.parse(Constants.GOOGLE_URL))
        @SuppressLint("InlinedApi")
        val resolvedActivityList = context.applicationContext.packageManager
            .queryIntentActivities(webIntentImplicit, PackageManager.MATCH_ALL)

        var componentName: ComponentName? = null
        for (info in resolvedActivityList) {
            if (info.activityInfo.packageName.equals(pkg, ignoreCase = true)) {
                componentName = ComponentName(info.activityInfo.packageName, info.activityInfo.name)
                webIntentImplicit.component = componentName
            }
        }
        return componentName
    }

    fun getDefaultBrowserPackage(context: Context): String {
        val resolveInfo = context.applicationContext
            .packageManager
            .resolveActivity(
                Constants.WEB_INTENT,
                PackageManager.MATCH_DEFAULT_ONLY
            )

        return resolveInfo?.activityInfo?.packageName?.trim() ?: ""
    }

    fun isDefaultBrowser(context: Context): Boolean {
        return getDefaultBrowserPackage(context).equals(context.packageName, ignoreCase = true)
    }

    fun getCustomTabActivityInfos(context: Context): List<IntentPickerBottomSheet.ActivityInfo> {
        val apps = ArrayList<IntentPickerBottomSheet.ActivityInfo>()
        val pm = context.applicationContext.packageManager
        @SuppressLint("InlinedApi")
        val resolvedActivityList = pm.queryIntentActivities(Constants.WEB_INTENT, PackageManager.MATCH_ALL)
        for (info in resolvedActivityList) {
            val packageName = info.activityInfo.packageName
            if (CustomTabs.isPackageSupportCustomTabs(context, packageName)) {
                val componentName = ComponentName(info.activityInfo.packageName, info.activityInfo.name)
                val icon = info.loadIcon(pm)
                val label = info.loadLabel(pm).toString()
                val activityInfo = IntentPickerBottomSheet.ActivityInfo(
                    info,
                    label,
                    componentName,
                    icon
                )
                apps.add(activityInfo)
            }
        }
        return apps
    }

    fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            val bitmapDrawable = drawable
            if (bitmapDrawable.bitmap != null) {
                return bitmapDrawable.bitmap
            }
        }

        val bitmap: Bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        } else {
            Bitmap.createBitmap(
                drawable.intrinsicWidth,
                drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
        }

        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun canReadUsageStats(context: Context): Boolean {
        if (!isLollipopAbove()) return true
        return try {
            val packageManager = context.applicationContext.packageManager
            val applicationInfo = packageManager.getApplicationInfo(context.packageName, 0)
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                applicationInfo.uid, applicationInfo.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isVoiceRecognizerPresent(context: Context): Boolean {
        val pm = context.applicationContext.packageManager
        val activities = pm.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0)
        return activities.isNotEmpty()
    }

    fun dpToPx(dp: Double): Int {
        val displayMetrics = Resources.getSystem().displayMetrics
        return ((dp * displayMetrics.density) + 0.5).toInt()
    }

    fun sp2px(context: Context, spValue: Float): Int {
        val fontScale = context.resources.displayMetrics.scaledDensity
        return (spValue * fontScale + 0.5f).toInt()
    }

    fun pxTosp(context: Context, px: Float): Float {
        val scaledDensity = context.resources.displayMetrics.scaledDensity
        return px / scaledDensity
    }

    fun pxToDp(px: Int): Int {
        return (px / Resources.getSystem().displayMetrics.density).toInt()
    }

    fun getFirstLetter(address: String?): String {
        var result = "X"
        if (address != null) {
            try {
                val url = URL(address)
                val host = url.host
                if (host != null && host.isNotEmpty()) {
                    result = if (host.startsWith("www")) {
                        val splits = host.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        if (splits.size > 1) {
                            splits[1][0].toString()
                        } else {
                            splits[0][0].toString()
                        }
                    } else {
                        host[0].toString()
                    }
                } else {
                    if (address.isNotEmpty()) {
                        return address[0].toString()
                    }
                }
            } catch (e: Exception) {
                if (address.isNotEmpty()) {
                    return address[0].toString()
                } else {
                    return result
                }
            }
        }
        return result
    }

    fun getRecognizerIntent(context: Context): Intent {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.voice_prompt))
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
        return intent
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetworkInfo = connectivityManager.activeNetworkInfo
        return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting
    }

    fun getClipBoardText(context: Context): String? {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return try {
            if (clipboardManager.hasPrimaryClip() && clipboardManager.primaryClip!!.itemCount != 0) {
                val item = clipboardManager.primaryClip!!.getItemAt(0)
                if (item != null && item.text != null) {
                    item.text.toString()
                } else {
                    null
                }
            } else {
                null
            }
        } catch (ignored: Exception) {
            null
        }
    }

    fun shareText(context: Context, url: String?) {
        if (url != null) {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_TEXT, url)

            val chooserIntent = Intent.createChooser(shareIntent, "Share URL via")
            chooserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(chooserIntent)
        } else {
            Toast.makeText(context, R.string.invalid_link, Toast.LENGTH_SHORT).show()
        }
    }

    fun scale(orgImage: Bitmap, targetSizePx: Float, filter: Boolean): Bitmap {
        val ratio = minOf(targetSizePx / orgImage.width, targetSizePx / orgImage.height)
        val width = Math.round(ratio * orgImage.width)
        val height = Math.round(ratio * orgImage.height)
        return Bitmap.createScaledBitmap(orgImage, width, height, filter)
    }

    fun printThread() {
        Timber.d("Thread: %s", Thread.currentThread().name)
    }

    fun createApp(context: Context, packageName: String): App {
        val app = App()
        app.packageName = packageName
        app.appName = getAppNameWithPackage(context, packageName)
        return app
    }

    fun doAfterLayout(view: View, end: Runnable) {
        view.requestLayout()
        val viewTreeObserver = view.viewTreeObserver
        if (viewTreeObserver.isAlive) {
            viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    end.run()
                }
            })
        }
    }

    fun isOverlayGranted(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    @TargetApi(Build.VERSION_CODES.M)
    fun openDrawOverlaySettings(context: Context) {
        try {
            Toast.makeText(context, context.getString(R.string.web_head_permission_toast), Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e)
            Toast.makeText(context, R.string.overlay_missing, Toast.LENGTH_LONG).show()
        }
    }

    fun isValidFavicon(favicon: Bitmap?): Boolean {
        return favicon != null && !(favicon.width == 16 || favicon.height == 16 ||
                favicon.width == 32 || favicon.height == 32)
    }

    fun isOnline(@NotNull context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.activeNetworkInfo != null &&
                cm.activeNetworkInfo!!.isAvailable &&
                cm.activeNetworkInfo!!.isConnected
    }

    /**
     * Deletes the app's cache directories.
     * This is a suspend function that performs I/O operations on Dispatchers.IO.
     *
     * @param context The application context
     * @return true if cache was successfully deleted, false otherwise
     */
    suspend fun deleteCache(context: Context): Boolean = withContext(Dispatchers.IO) {
        fun deleteDir(dir: File?): Boolean {
            if (dir != null && dir.isDirectory) {
                val children = dir.list()
                for (path in children) {
                    val success = deleteDir(File(dir, path))
                    if (!success) {
                        return false
                    }
                }
            }
            return dir?.delete() ?: false
        }

        try {
            var deleted = true
            val internalCache = context.applicationContext.cacheDir
            if (internalCache != null && internalCache.isDirectory) {
                deleted = deleteDir(internalCache)
            }
            val externalCache = context.applicationContext.externalCacheDir
            if (externalCache != null && externalCache.isDirectory) {
                deleted = deleted && deleteDir(externalCache)
            }
            Timber.d("Cache deletion %b", deleted)
            deleted
        } catch (e: Exception) {
            Timber.e(e, "Error deleting cache")
            false
        }
    }

    /**
     * A helper class for providing a shadow on sheets
     */
    @TargetApi(21)
    class ShadowOutline(private val width: Int, private val height: Int) : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRect(0, 0, width, height)
        }
    }
}
