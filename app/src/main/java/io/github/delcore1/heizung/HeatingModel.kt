package io.github.delcore1.heizung

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/*
 * Datenmodell der App.
 *
 * Die App rechnet NICHTS selbst und kennt KEINE Entitaeten fest.
 * Alles kommt aus Home Assistant, aus dem Skript script.heizung_app_status
 * (Makro status() in custom_templates/heizung_app.jinja). Dort steht pro
 * Raum: Ist, echtes Ziel (climate.*), Grund in derselben Rangfolge wie die
 * Raumskripte, die Wunsch-Entitaet und welche Schalter es gibt.
 *
 * Wird in Home Assistant ein Raum, ein Sensor oder ein Helfer geaendert,
 * passt man nur die jinja-Datei an - die App muss dafuer nicht neu gebaut
 * werden.
 *
 * Bedient wird ausschliesslich, was auch das Dashboard bedient: Helfer
 * (input_number/input_boolean/input_select/input_datetime) und das Skript
 * script.heizung_raum_betriebsart. Nie climate.* direkt - das wuerde das
 * Raumskript beim naechsten Lauf ohnehin wieder ueberschreiben.
 */

/** Version des Antwortformats, die diese App versteht. */
const val STATUS_VERSION = 1

enum class Betriebsart(val wert: String, val label: String) {
    AUS("aus", "Aus"), AUTOMATIK("automatik", "Automatik"), HEIZEN("heizen", "Heizen");

    companion object {
        fun of(v: String) = entries.firstOrNull { it.wert == v } ?: AUTOMATIK
    }
}

/** Wie der Grund in der App eingefaerbt wird. */
enum class ReasonTone { HEAT, NORMAL, ECO, PAUSE, OFF;

    companion object {
        fun of(v: String) = when (v) {
            "heizt" -> HEAT
            "normal" -> NORMAL
            "pause" -> PAUSE
            "aus" -> OFF
            else -> ECO
        }
    }
}

data class Info(val label: String, val value: String)

/** Bedienelemente, die HA pro Raum freigibt (z. B. Buero: Home-Office, Arbeitsbeginn). */
sealed class Control(open val label: String, open val entity: String) {
    data class Toggle(override val label: String, override val entity: String, val on: Boolean) : Control(label, entity)
    data class Time(override val label: String, override val entity: String, val value: String) : Control(label, entity)
}

data class RoomState(
    val id: String,
    val label: String,
    val icon: String,
    val current: Double?,
    val humidity: Double?,
    /** Wert, auf den das Thermostat gerade wirklich regelt. */
    val target: Double?,
    val heating: Boolean,
    val windowOpen: Boolean,
    /** Wunschtemperatur-Regler (input_number). */
    val wish: Double?,
    val wishEntity: String,
    val wishMin: Double,
    val wishMax: Double,
    val wishStep: Double,
    /** Wunschregler zeigt gerade einen erzwungenen Notwert -> Bedienung sperren. */
    val wishLocked: Boolean,
    val mode: Betriebsart,
    val reason: String,
    val tone: ReasonTone,
    val extras: List<String>,
    val infos: List<Info>,
    val controls: List<Control>,
    val hint: String,
)

data class HouseState(
    val mode: String,
    val modeOptions: List<String>,
    val modeEntity: String,
    val modeHint: String,
    val outdoor: Double?,
    val limit: Double?,
    val summerLock: Boolean,
    val weatherBonus: Double,
    val vacation: Boolean,
    val allOff: Boolean,
    val allOffEntity: String,
    val pumpRunning: Boolean,
    val lastLog: String,
    val facts: List<String>,
    val betriebsartScript: String,
    /** Nabu-Casa-Adresse aus HA; die App uebernimmt sie, wenn "unterwegs" noch leer ist. */
    val remoteUrl: String,
    /** Zustand von weather.* (sunny, cloudy, rainy ...) - nur fuer die Himmelsfarbe. */
    val weather: String = "",
    /** Sonne ueber dem Horizont? */
    val isDay: Boolean = true,
)

data class Snapshot(val version: Int, val house: HouseState, val rooms: List<RoomState>) {
    val heatingCount get() = rooms.count { it.heating }
}

// ------------------------------------------------------------------ Einlesen

private fun JSONObject.dbl(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

private fun JSONObject.str(key: String, fallback: String = ""): String =
    if (has(key) && !isNull(key)) optString(key, fallback) else fallback

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else List(length()) { optString(it) }.filter { it.isNotBlank() }

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else List(length()) { optJSONObject(it) }.filterNotNull()

/** Wandelt die Antwort von script.heizung_app_status in den App-Zustand um. */
fun parseStatus(json: JSONObject): Snapshot {
    val h = json.optJSONObject("haus") ?: throw IllegalStateException("Antwort ohne \"haus\"")
    val house = HouseState(
        mode = h.str("modus", "–"),
        modeOptions = h.optJSONArray("modus_optionen").strings(),
        modeEntity = h.str("modus_entity"),
        modeHint = h.str("modus_hinweis"),
        outdoor = h.dbl("aussen"),
        limit = h.dbl("grenze"),
        summerLock = h.optBoolean("sommersperre"),
        weatherBonus = h.dbl("witterung_bonus") ?: 0.0,
        vacation = h.optBoolean("urlaub"),
        allOff = h.optBoolean("alles_aus"),
        allOffEntity = h.str("alles_aus_entity"),
        pumpRunning = h.optBoolean("pumpe_laeuft"),
        lastLog = h.str("letzte_aktion"),
        facts = h.optJSONArray("fakten").strings(),
        betriebsartScript = h.str("betriebsart_script"),
        remoteUrl = h.str("fernzugriff_url"),
        weather = h.str("wetter"),
        isDay = if (h.has("tag") && !h.isNull("tag")) h.optBoolean("tag", true) else true,
    )
    val rooms = json.optJSONArray("raeume").objects().map { r ->
        RoomState(
            id = r.str("id"),
            label = r.str("label", r.str("id")),
            icon = r.str("icon", "🌡️"),
            current = r.dbl("ist"),
            humidity = r.dbl("feuchte"),
            target = r.dbl("ziel"),
            heating = r.optBoolean("heizt"),
            windowOpen = r.optBoolean("fenster_offen"),
            wish = r.dbl("wunsch"),
            wishEntity = r.str("wunsch_entity"),
            wishMin = r.dbl("wunsch_min") ?: 5.0,
            wishMax = r.dbl("wunsch_max") ?: 30.0,
            wishStep = (r.dbl("wunsch_schritt") ?: 0.5).takeIf { it > 0 } ?: 0.5,
            wishLocked = r.optBoolean("wunsch_gesperrt"),
            mode = Betriebsart.of(r.str("betriebsart")),
            reason = r.str("grund"),
            tone = ReasonTone.of(r.str("ton")),
            extras = r.optJSONArray("extras").strings(),
            infos = r.optJSONArray("infos").objects().map { Info(it.str("label"), it.str("wert", "–")) },
            controls = r.optJSONArray("steuerung").objects().mapNotNull { c ->
                val entity = c.str("entity").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                when (c.str("typ")) {
                    "schalter" -> Control.Toggle(c.str("label"), entity, c.optBoolean("an"))
                    "uhrzeit" -> Control.Time(c.str("label"), entity, c.str("wert"))
                    else -> null
                }
            },
            hint = r.str("hinweis"),
        )
    }.filter { it.id.isNotBlank() }
    return Snapshot(json.optInt("version", 0), house, rooms)
}

fun fmt(v: Double?, digits: Int = 1): String =
    if (v == null) "–" else String.format(Locale.GERMANY, "%.${digits}f", v)

// ------------------------------------------------------------------ Himmel nach Wetter

/** Stimmung des Himmels oben in der App - richtet sich nach Wetter und Tageszeit. */
enum class Sky { SUN, PARTLY, CLOUD, FOG, RAIN, STORM, SNOW, NIGHT }

fun skyOf(weather: String, isDay: Boolean): Sky {
    val w = weather.lowercase()
    val wet = when (w) {
        "rainy", "pouring" -> Sky.RAIN
        "lightning", "lightning-rainy", "hail", "exceptional" -> Sky.STORM
        "snowy", "snowy-rainy" -> Sky.SNOW
        else -> null
    }
    if (wet != null) return wet
    if (!isDay || w == "clear-night") return Sky.NIGHT
    return when (w) {
        "sunny" -> Sky.SUN
        "partlycloudy", "windy-variant" -> Sky.PARTLY
        "fog" -> Sky.FOG
        "" -> Sky.PARTLY
        else -> Sky.CLOUD   // cloudy, windy, unbekannt
    }
}

/** Deutscher Name fuer die Kopfzeile ("" = nichts anzeigen). */
fun weatherLabel(weather: String): String = when (weather.lowercase()) {
    "sunny" -> "Sonnig"
    "clear-night" -> "Klar"
    "partlycloudy" -> "Teils bewölkt"
    "cloudy" -> "Bewölkt"
    "fog" -> "Nebel"
    "rainy" -> "Regen"
    "pouring" -> "Starkregen"
    "snowy" -> "Schnee"
    "snowy-rainy" -> "Schneeregen"
    "hail" -> "Hagel"
    "lightning", "lightning-rainy" -> "Gewitter"
    "windy", "windy-variant" -> "Windig"
    else -> ""
}

// ------------------------------------------------------------------ Dienstaufrufe

/** Ein Home-Assistant-Dienstaufruf (Domain, Dienst, Daten). */
data class ServiceCall(val domain: String, val service: String, val data: Map<String, Any>)

/**
 * Wunschtemperatur setzen. Welche Entitaet das ist, bestimmt Home Assistant
 * in der Status-Antwort (wunsch_entity):
 *  - input_number.* (z. B. eigene Raumskripte lesen einen Helfer) -> set_value
 *  - climate.*      (einfache Installation ohne eigene Skripte)  -> set_temperature
 * So umgeht die App nie eine Logik, die der Besitzer in HA gebaut hat.
 */
fun wishCall(entity: String, value: Double): ServiceCall? {
    // Mehrere Entitaeten (z. B. zwei Thermostate in einem Raum) kommen
    // kommagetrennt und werden gemeinsam gestellt.
    val ids = entity.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    if (ids.isEmpty()) return null
    val domain = ids.first().substringBefore('.', "")
    if (ids.any { it.substringBefore('.', "") != domain }) return null
    val target: Any = if (ids.size == 1) ids.first() else ids
    return when (domain) {
        "input_number", "number" -> ServiceCall(domain, "set_value", mapOf("entity_id" to target, "value" to value))
        "climate" -> ServiceCall("climate", "set_temperature", mapOf("entity_id" to target, "temperature" to value))
        else -> null
    }
}
