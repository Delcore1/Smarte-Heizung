package io.github.delcore1.heizung

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Szenario-Tests, laufen bei jedem Bau auf GitHub (vor dem Release).
 * Schlaegt einer fehl, wird KEINE neue Version veroeffentlicht.
 *
 * status_live.json: anonymisierte echte Antwort von script.heizung_app_status
 * (Home Office, Schlafzimmerfenster offen, Buero auf "Heizen").
 */
class ScenarioTest {

    private fun live(): JSONObject =
        JSONObject(javaClass.classLoader!!.getResource("status_live.json")!!.readText())

    // ------------------------------------------------------------ echte Antwort

    @Test fun echteAntwort_alleRaeume() {
        val s = parseStatus(live())
        assertEquals(STATUS_VERSION, s.version)
        assertEquals(listOf("wohnzimmer", "esszimmer", "badezimmer", "schlafzimmer", "buro"), s.rooms.map { it.id })
        assertEquals("Home Office", s.house.mode)
        assertEquals(5, s.house.modeOptions.size)
        assertTrue(s.house.remoteUrl.startsWith("https://"))
        assertEquals("script.heizung_betriebsart", s.house.betriebsartScript)
    }

    @Test fun fensterOffen_istGesperrtUndPause() {
        val r = parseStatus(live()).rooms.first { it.id == "schlafzimmer" }
        assertTrue(r.windowOpen)
        assertTrue(r.wishLocked)
        assertEquals(ReasonTone.PAUSE, r.tone)
        assertEquals(5.0, r.target!!, 0.0)
    }

    @Test fun buero_heizen_mitSchalterUndUhrzeit() {
        val r = parseStatus(live()).rooms.first { it.id == "buro" }
        assertEquals(Betriebsart.HEIZEN, r.mode)
        assertEquals(ReasonTone.HEAT, r.tone)
        val toggle = r.controls.filterIsInstance<Control.Toggle>().single()
        assertEquals("input_boolean.home_office_morgen", toggle.entity)
        val time = r.controls.filterIsInstance<Control.Time>().single()
        assertEquals("07:30", time.value)
    }

    /** Die App darf nur Helfer bedienen - nie climate.* direkt. */
    @Test fun keineDirekteThermostatBedienung() {
        val s = parseStatus(live())
        val ziele = s.rooms.map { it.wishEntity } + s.rooms.flatMap { r -> r.controls.map { it.entity } } +
            listOf(s.house.modeEntity, s.house.allOffEntity, s.house.betriebsartScript)
        assertTrue(ziele.all { it.isNotBlank() })
        assertTrue(ziele.none { it.startsWith("climate.") })
    }

    // ------------------------------------------------------------ kaputte / fremde Daten

    @Test fun sensorWegOderNull_stuerztNichtAb() {
        val j = live()
        val r0 = j.getJSONArray("raeume").getJSONObject(0)
        r0.put("ist", JSONObject.NULL); r0.put("ziel", JSONObject.NULL); r0.remove("feuchte")
        r0.put("wunsch", JSONObject.NULL); r0.put("wunsch_schritt", 0)
        val r = parseStatus(j).rooms[0]
        assertNull(r.current); assertNull(r.target); assertNull(r.humidity); assertNull(r.wish)
        assertEquals(0.5, r.wishStep, 0.0)  // Schritt 0 wuerde den Regler einfrieren
    }

    @Test fun unbekannteWerte_werdenSicherBehandelt() {
        val j = live()
        val r0 = j.getJSONArray("raeume").getJSONObject(0)
        r0.put("ton", "irgendwas"); r0.put("betriebsart", "neu")
        r0.put("steuerung", org.json.JSONArray().put(JSONObject().put("typ", "regler").put("entity", "x.y"))
            .put(JSONObject().put("typ", "schalter").put("label", "ohne Entitaet")))
        val r = parseStatus(j).rooms[0]
        assertEquals(ReasonTone.ECO, r.tone)
        assertEquals(Betriebsart.AUTOMATIK, r.mode)
        assertTrue(r.controls.isEmpty())  // unbekannter Typ und fehlende Entitaet fliegen raus
    }

    @Test fun raumOhneId_wirdIgnoriert() {
        val j = live()
        j.getJSONArray("raeume").put(JSONObject().put("label", "Geist"))
        assertEquals(5, parseStatus(j).rooms.size)
    }

    @Test(expected = IllegalStateException::class)
    fun antwortOhneHaus_istFehler() {
        parseStatus(JSONObject().put("version", 1))
    }

    @Test fun neueresFormat_wirdErkannt() {
        val s = parseStatus(live().put("version", STATUS_VERSION + 1))
        assertTrue(s.version > STATUS_VERSION)
    }

    @Test fun alteAntwortOhneFernzugriff_bleibtLeer() {
        val j = live(); j.getJSONObject("haus").remove("fernzugriff_url")
        assertEquals("", parseStatus(j).house.remoteUrl)
    }

    // ------------------------------------------------------------ Updates

    @Test fun tagNummern() {
        assertEquals(81, Updater.codeFromTag("v1.0.81"))
        assertEquals(10, Updater.codeFromTag("v1.0.10"))
        assertEquals(6, Updater.codeFromTag("v1.06"))
        assertNull(Updater.codeFromTag("latest"))
    }

    @Test fun update_nurWennNeuer() {
        assertTrue(Updater.isNewer(81, 79))
        assertFalse(Updater.isNewer(79, 79))   // gleiche Version: keine Meldung (alter Fehler)
        assertFalse(Updater.isNewer(10, 79))   // alte Tags v1.0.10 duerfen nie als neuer gelten
    }

    @Test fun update_spaeterWirdGemerkt() {
        assertTrue(Updater.shouldPopUp(latest = 81, installed = 79, dismissed = 0))
        assertFalse(Updater.shouldPopUp(latest = 81, installed = 79, dismissed = 81))  // weggeklickt
        assertTrue(Updater.shouldPopUp(latest = 83, installed = 79, dismissed = 81))   // noch neuer -> wieder
        assertFalse(Updater.shouldPopUp(latest = 81, installed = 81, dismissed = 0))   // schon drauf
    }

    // ------------------------------------------------------------ Anzeige

    @Test fun zahlenformat_deutsch() {
        assertEquals("20,5", fmt(20.5))
        assertEquals("–", fmt(null))
        assertEquals("55", fmt(54.6, 0))
    }

    // ------------------------------------------------------------ Kacheln (Farbe = Zustand)

    @Test fun kachelFarbeUndZeile() {
        val s = parseStatus(live())
        val schlaf = s.rooms.first { it.id == "schlafzimmer" }
        assertEquals(Look.WINDOW, lookOf(schlaf))
        assertEquals("Fenster offen · pausiert", tileLine(schlaf, lookOf(schlaf)))
        val wohn = s.rooms.first { it.id == "wohnzimmer" }
        assertEquals(Look.ECO, lookOf(wohn))
        assertEquals("Absenkung · 14,7°", tileLine(wohn, lookOf(wohn)))
        val buero = s.rooms.first { it.id == "buro" }   // Heizen, aber Ventil zu (warm genug)
        assertEquals(Look.CALM, lookOf(buero))
        assertEquals("Warm genug · 21,4°", tileLine(buero, lookOf(buero)))
    }

    @Test fun emojiVorFaktenWirdEntfernt() {
        assertEquals("Umwälzpumpe aus", stripEmoji("💧 Umwälzpumpe aus"))
        assertEquals("Fenster offen: Schlafzimmer", stripEmoji("🪟 Fenster offen: Schlafzimmer"))
        assertEquals("Ohne", stripEmoji("Ohne"))
    }

    // ------------------------------------------------------------ Wetter-Himmel

    @Test fun himmelNachWetter() {
        assertEquals(Sky.SUN, skyOf("sunny", true))
        assertEquals(Sky.NIGHT, skyOf("sunny", false))       // nachts kein Sonnen-Himmel
        assertEquals(Sky.RAIN, skyOf("rainy", false))        // Regen sieht man auch nachts
        assertEquals(Sky.SNOW, skyOf("snowy-rainy", true))
        assertEquals(Sky.STORM, skyOf("lightning-rainy", true))
        assertEquals(Sky.CLOUD, skyOf("cloudy", true))
        assertEquals(Sky.CLOUD, skyOf("irgendwas-neues", true))
        assertEquals("Bewölkt", weatherLabel("cloudy"))
        assertEquals("", weatherLabel("unknown"))
    }

    @Test fun wetterAusAntwort_undAlteAntwortOhneWetter() {
        val j = live(); j.getJSONObject("haus").put("wetter", "rainy").put("tag", false)
        val h = parseStatus(j).house
        assertEquals("rainy", h.weather); assertFalse(h.isDay)
        val alt = parseStatus(live()).house       // Fixture hat noch kein Wetter
        assertEquals("", alt.weather); assertTrue(alt.isDay)
    }

    // ------------------------------------------------------------ Wunsch setzen

    @Test fun wunschUeberHelferOderThermostat() {
        val h = wishCall("input_number.heizung_temp_bad_override", 21.5)!!
        assertEquals("input_number", h.domain); assertEquals("set_value", h.service); assertEquals(21.5, h.data["value"])
        val c = wishCall("climate.bad", 20.0)!!
        assertEquals("climate", c.domain); assertEquals("set_temperature", c.service); assertEquals(20.0, c.data["temperature"])
        assertNull(wishCall("sensor.irgendwas", 20.0))   // unbekannt -> nichts senden
        assertNull(wishCall("", 20.0))
    }

    @Test fun beispielVorlageFuerAndere_wirdGelesen() {
        val j = JSONObject(javaClass.classLoader!!.getResource("status_beispiel.json")!!.readText())
        val s = parseStatus(j)
        assertEquals(2, s.rooms.size)
        assertEquals("climate.bad", s.rooms[0].wishEntity)
        assertEquals("", s.house.modeEntity)        // ohne Hausmodus -> Knoepfe ausgeblendet
        assertEquals("", s.house.allOffEntity)      // ohne "Alles aus" -> Kachel ausgeblendet
        assertEquals("", s.house.betriebsartScript) // ohne Betriebsart-Skript -> Umschalter ausgeblendet
    }

    // ------------------------------------------------------------ automatische Erkennung

    @Test fun zweiThermostateImRaum_werdenGemeinsamGestellt() {
        val c = wishCall("climate.wohnzimmer_1,climate.wohnzimmer_2", 21.0)!!
        assertEquals("set_temperature", c.service)
        assertEquals(listOf("climate.wohnzimmer_1", "climate.wohnzimmer_2"), c.data["entity_id"])
        assertNull(wishCall("climate.a,input_number.b", 21.0))   // gemischt -> nichts senden
    }

    @Test fun automatischeVorlage_wirdGelesen() {
        val j = JSONObject(javaClass.classLoader!!.getResource("status_auto.json")!!.readText())
        val s = parseStatus(j)
        assertEquals(5, s.rooms.size)
        val wz = s.rooms.first { it.id == "wohnzimmer" }
        assertEquals("climate.wohnzimmer_1,climate.wohnzimmer_2", wz.wishEntity)
        assertEquals("2", wz.infos.first { it.label == "Thermostate" }.value)
    }
}
