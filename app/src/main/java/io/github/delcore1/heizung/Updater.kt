package io.github.delcore1.heizung

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Selbst-Update ueber GitHub Releases.
 *
 * Versionsnummern entstehen automatisch beim Bauen auf GitHub:
 *   versionCode = Laufnummer des Bau-Workflows (GITHUB_RUN_NUMBER)
 *   versionName = "1.0.<Laufnummer>", Release-Tag = "v1.0.<Laufnummer>"
 * Nichts davon wird mehr von Hand gepflegt. Ein Update wird nur gemeldet,
 * wenn die Laufnummer im Tag GROESSER ist als die eigene (vorher: "ist
 * anders" -> Meldung bei jedem Start, auch nach dem Update).
 *
 * Installation: Die APK wird in der App heruntergeladen und direkt an den
 * Android-Paketinstaller uebergeben (vorher: Link im Browser geoeffnet,
 * danach kam kein Installieren-Knopf). Android verlangt dafuer einmalig die
 * Erlaubnis "Apps aus dieser Quelle installieren" fuer die Heizungs-App.
 */
object Updater {

    private const val LATEST = "https://api.github.com/repos/Delcore1/Smarte-Heizung/releases/latest"

    data class Release(val code: Int, val name: String, val apkUrl: String, val notes: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** "v1.0.81" -> 81. Alte Tags (v1.0.10, v1.06) liefern kleine Zahlen und zaehlen damit nie als neuer. */
    fun codeFromTag(tag: String): Int? =
        tag.removePrefix("v").split('.').lastOrNull()?.toIntOrNull()

    /** Gibt es ueberhaupt etwas Neueres? (Dann zeigt der Kopf der App einen Update-Hinweis.) */
    fun isNewer(latest: Int, installed: Int): Boolean = latest > installed

    /**
     * Soll das Update-Fenster VON SELBST aufgehen? Nur, wenn die Version neuer
     * ist als die installierte UND nicht schon mit "Spaeter" weggeklickt wurde.
     * Ein noch neueres Release meldet sich wieder. (Vorher kam das Fenster bei
     * jedem Start - genau das "dauernd Update".)
     */
    fun shouldPopUp(latest: Int, installed: Int, dismissed: Int): Boolean =
        isNewer(latest, installed) && latest > dismissed

    private const val PREFS = "updater"
    private const val KEY_DISMISSED = "dismissed_code"
    private const val KEY_LAST_CHECK = "last_check_ms"

    fun dismissedCode(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_DISMISSED, 0)

    fun dismiss(context: Context, code: Int) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_DISMISSED, code).apply()

    /** Hoechstens alle 30 min bei GitHub nachfragen (beim Zurueckkehren in die App). */
    fun dueForCheck(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (nowMs - p.getLong(KEY_LAST_CHECK, 0L) < 30 * 60_000L) return false
        p.edit().putLong(KEY_LAST_CHECK, nowMs).apply()
        return true
    }

    /** Alte, schon installierte Update-Dateien wegraeumen (liegen sonst ~10 MB im Cache). */
    fun cleanup(context: Context) {
        File(context.cacheDir, "updates").listFiles()?.forEach { it.delete() }
    }

    /** Gibt das neuere Release zurueck oder null, wenn die App aktuell ist / keine Verbindung. */
    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(LATEST)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Heizung-App")
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                val json = JSONObject(res.body?.string() ?: return@withContext null)
                val tag = json.optString("tag_name")
                val code = codeFromTag(tag) ?: return@withContext null
                if (!isNewer(code, BuildConfig.VERSION_CODE)) return@withContext null
                val assets = json.optJSONArray("assets") ?: return@withContext null
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk")) {
                        return@withContext Release(
                            code = code,
                            name = tag.removePrefix("v"),
                            apkUrl = a.optString("browser_download_url"),
                            notes = json.optString("body").take(600),
                        )
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Laedt die APK in den App-Cache. onProgress bekommt 0..1 (oder -1, wenn die Groesse unbekannt ist). */
    suspend fun download(context: Context, r: Release, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "heizung-${r.name}.apk")
            val req = Request.Builder().url(r.apkUrl).header("User-Agent", "Heizung-App").build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) throw IOException("Download fehlgeschlagen (${res.code})")
                val body = res.body ?: throw IOException("Leere Antwort")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    target.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var last = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (done - last > 256 * 1024) {
                                last = done
                                val p = if (total > 0) done.toFloat() / total else -1f
                                withContext(Dispatchers.Main) { onProgress(p) }
                            }
                        }
                    }
                }
            }
            target
        }

    /** Darf die App Pakete installieren? (Android 8+: pro App einmal erlauben.) */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Oeffnet die Systemseite "Apps aus dieser Quelle installieren" fuer genau diese App. */
    fun openInstallPermission(activity: Activity) {
        activity.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
        )
    }

    /** Uebergibt die APK an den Paketinstaller von Android. */
    fun install(activity: Activity, apk: File) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(intent)
    }
}
