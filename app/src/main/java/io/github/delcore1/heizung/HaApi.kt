package io.github.delcore1.heizung

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Verbindung zu Home Assistant.
 *
 * - Zwei Adressen: "zuhause" (lokal) wird zuerst probiert, "unterwegs"
 *   (Nabu Casa / eigene Domain) nur, wenn die lokale nicht antwortet.
 * - Pro Aktualisierung genau EIN Aufruf von script.heizung_app_status.
 *   Die App kennt keine Entitaeten fest, HA sagt ihr alles.
 * - Fehler werden nicht mehr verschluckt, sondern als Text nach oben
 *   gegeben, damit die App sagen kann, was los ist.
 */
class HaApi(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val probeClient = client.newBuilder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    /** Zuletzt erfolgreich benutzte Adresse (fuer die Anzeige "zuhause/unterwegs"). */
    @Volatile var activeUrl: String = ""
        private set

    init {
        migrateOldSettings(context)
    }

    // ---------------------------------------------------------------- Einstellungen

    var localUrl: String
        get() = prefs.getString(KEY_LOCAL, "") ?: ""
        set(v) = prefs.edit().putString(KEY_LOCAL, cleanUrl(v)).apply()

    var remoteUrl: String
        get() = prefs.getString(KEY_REMOTE, "") ?: ""
        set(v) = prefs.edit().putString(KEY_REMOTE, cleanUrl(v)).apply()

    var token: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(v) = prefs.edit().putString(KEY_TOKEN, v.trim()).apply()

    fun isConfigured() = token.isNotBlank() && (localUrl.isNotBlank() || remoteUrl.isNotBlank())

    val isRemote: Boolean get() = activeUrl.isNotEmpty() && activeUrl == remoteUrl && activeUrl != localUrl

    /**
     * Die Vorgaenger-Version speicherte unter "heizung" (local_url, remote_url,
     * access_token), die Compose-Version 1.0.5-1.0.10 unter "ha_config"
     * (ha_url, ha_token). Beides wird einmalig uebernommen.
     */
    private fun migrateOldSettings(context: Context) {
        if (prefs.contains(KEY_TOKEN)) return
        val old = context.getSharedPreferences("heizung", Context.MODE_PRIVATE)
        val oldToken = old.getString("access_token", "") ?: ""
        val oldLocal = old.getString("local_url", "") ?: ""
        val oldRemote = old.getString("remote_url", "") ?: ""
        if (oldToken.isNotBlank()) {
            prefs.edit()
                .putString(KEY_LOCAL, cleanUrl(oldLocal))
                .putString(KEY_REMOTE, cleanUrl(oldRemote))
                .putString(KEY_TOKEN, oldToken.trim())
                .apply()
        }
        // "ha_config" ist zugleich PREFS: ha_url/ha_token wurden dort schon unter
        // denselben Schluesselnamen (KEY_LOCAL/KEY_TOKEN) gespeichert.
    }

    // ---------------------------------------------------------------- Abrufe

    /**
     * Holt den kompletten Heizungszustand mit EINEM Aufruf von
     * script.heizung_app_status (liest nur, schaltet nichts). Die Antwort
     * enthaelt Raeume, Gruende und die zu bedienenden Entitaeten.
     * Funktioniert auch mit dem Token eines Nicht-Admin-Benutzers.
     */
    suspend fun fetchStatus(): JSONObject = withContext(Dispatchers.IO) {
        try {
            fetchStatusRaw()
        } catch (first: IOException) {
            // Netz gewechselt (WLAN -> Mobilfunk oder zurueck)? Die zuletzt
            // benutzte Adresse vergessen und sofort die andere probieren, statt
            // eine Fehlermeldung zu zeigen.
            if (isNetworkError(first) && hasBothUrls()) {
                activeUrl = ""
                try {
                    return@withContext fetchStatusRaw()
                } catch (second: IOException) {
                    throw IOException(friendly(second))
                }
            }
            throw IOException(friendly(first))
        }
    }

    /** Beim Zurueckkehren in die App wieder zuerst "zuhause" probieren (schneller, ohne Cloud). */
    fun preferLocalAgain() {
        if (hasBothUrls()) activeUrl = ""
    }

    private fun hasBothUrls() = localUrl.isNotBlank() && remoteUrl.isNotBlank() && localUrl != remoteUrl

    private fun isNetworkError(e: IOException) =
        e is java.net.ConnectException || e is java.net.SocketTimeoutException ||
            e is java.net.UnknownHostException || e is java.net.NoRouteToHostException ||
            e is javax.net.ssl.SSLException

    private fun fetchStatusRaw(): JSONObject {
        val base = resolveBaseUrl()
        val req = Request.Builder()
            .url("$base/api/services/script/$STATUS_SCRIPT?return_response")
            .header("Authorization", "Bearer $token")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { res ->
            if (res.code == 400) throw IOException(
                "Home Assistant kennt script.$STATUS_SCRIPT nicht (oder es liefert keine Antwort). " +
                    "Das Skript muss in HA angelegt sein – siehe README."
            )
            if (!res.isSuccessful) throw IOException(httpError(res.code))
            val body = JSONObject(res.body?.string() ?: "{}")
            return body.optJSONObject("service_response")
                ?: throw IOException("script.$STATUS_SCRIPT hat keine Daten geliefert")
        }
    }

    /**
     * Netzwerkfehler auf Deutsch und mit Hinweis, was zu tun ist. Vorher
     * stand dort roher Java-Text ("failed to connect to ... after 4000ms").
     */
    private fun friendly(e: IOException): String = when {
        isNetworkError(e) ->
            if (remoteUrl.isBlank())
                "Home Assistant zuhause nicht erreichbar – bist du unterwegs? Die Adresse für unterwegs " +
                    "ist noch nicht eingetragen. Öffne die App einmal im WLAN zuhause, dann übernimmt sie sie " +
                    "automatisch aus Home Assistant – oder trage sie unter ⚙️ ein."
            else "Home Assistant ist gerade weder zuhause noch unterwegs erreichbar. Internet am Handy prüfen."
        else -> e.message ?: "Keine Verbindung"
    }

    /** Ruft einen Dienst auf. Gibt null bei Erfolg zurueck, sonst eine Fehlermeldung. */
    suspend fun callService(domain: String, service: String, data: JSONObject): String? =
        withContext(Dispatchers.IO) {
            try {
                val base = resolveBaseUrl()
                val req = Request.Builder()
                    .url("$base/api/services/$domain/$service")
                    .header("Authorization", "Bearer $token")
                    .post(data.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(req).execute().use { res ->
                    if (res.isSuccessful) null else httpError(res.code)
                }
            } catch (e: IOException) {
                friendly(e)
            } catch (e: Exception) {
                e.message ?: "Unbekannter Fehler"
            }
        }

    /**
     * Lokale Adresse zuerst (schnell, ohne Umweg ueber das Internet). Antwortet
     * sie nicht innerhalb von 2-3 Sekunden, kommt die Fernzugriffs-Adresse dran.
     * Die zuletzt funktionierende Adresse wird zuerst probiert.
     */
    private fun resolveBaseUrl(): String {
        val candidates = listOf(activeUrl, localUrl, remoteUrl).filter { it.isNotBlank() }.distinct()
        if (candidates.isEmpty()) throw IOException("Keine Adresse eingetragen")
        if (candidates.size == 1) { activeUrl = candidates[0]; return activeUrl }
        for (url in candidates) {
            if (ping(url)) { activeUrl = url; return url }
        }
        throw java.net.ConnectException("weder zuhause noch unterwegs erreichbar")
    }

    private fun ping(base: String): Boolean = try {
        val req = Request.Builder()
            .url("$base/api/")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        probeClient.newCall(req).execute().use { it.isSuccessful }
    } catch (_: Exception) {
        false
    }

    private fun httpError(code: Int) = when (code) {
        401 -> "Zugriffstoken ungültig (401) – bitte in den Einstellungen neu eintragen"
        403 -> "Zugriff verweigert (403)"
        404 -> "Adresse stimmt nicht (404)"
        else -> "Home Assistant antwortet mit Fehler $code"
    }

    companion object {
        const val PREFS = "ha_config"
        const val STATUS_SCRIPT = "heizung_app_status"
        private const val KEY_LOCAL = "ha_url"
        private const val KEY_REMOTE = "ha_url_remote"
        private const val KEY_TOKEN = "ha_token"

        fun cleanUrl(v: String) = v.trim().trimEnd('/')
    }
}
