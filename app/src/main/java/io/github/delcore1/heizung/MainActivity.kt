package io.github.delcore1.heizung

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ------------------------------------------------------------------ Farben (Warmup-Stil)

private val Flame = Color(0xFFFF6B00)
private val Amber = Color(0xFFFFB703)
private val Gold = Color(0xFFFF9E00)
private val Ink = Color(0xFF070404)
private val CardBg = Color(0x881A100C)
private val CardLine = Color(0x22FFB703)
private val Muted = Color(0xFFA1A1AA)
private val Soft = Color(0xFFE4E4E7)
private val Good = Color(0xFF22C55E)
private val Bad = Color(0xFFEF4444)
private val Cool = Color(0xFF38BDF8)

// Neue Palette (tado/Nest-Stil)
private val Bg = Color(0xFF0E0F12)
private val Tile = Color(0xFF1B1D22)
private val TileOff = Color(0xFF22252B)
private val TextDim = Color(0xFF9CA3AF)
private val TextSoft = Color(0xFFCBD5E1)
private val HeatA = Color(0xFFFF9A4D)
private val HeatB = Color(0xFFE8590C)
private val WinA = Color(0xFF35597A)
private val WinB = Color(0xFF1F3448)
private val EcoDot = Color(0xFF34D399)

private fun toneColor(t: ReasonTone) = when (t) {
    ReasonTone.HEAT -> Color(0xFFFF8A3D)
    ReasonTone.NORMAL -> Amber
    ReasonTone.ECO -> Good
    ReasonTone.PAUSE -> Bad
    ReasonTone.OFF -> Cool
}

/** "input_boolean.xyz" -> "input_boolean" */
private fun domainOf(entity: String) = entity.substringBefore('.')

/** Zustand der Update-Anzeige. */
sealed class UpdateUi {
    data object None : UpdateUi()
    data class Available(val r: Updater.Release) : UpdateUi()
    data class Downloading(val r: Updater.Release, val progress: Float) : UpdateUi()
    data class Ready(val r: Updater.Release, val apk: File, val needsPermission: Boolean) : UpdateUi()
    data class Failed(val r: Updater.Release, val msg: String) : UpdateUi()
}

data class ChatMessage(val sender: String, val text: String, val isUser: Boolean)

class MainActivity : ComponentActivity() {

    private lateinit var api: HaApi

    private var snapshot by mutableStateOf<Snapshot?>(null)
    private var error by mutableStateOf<String?>(null)
    private var configured by mutableStateOf(false)
    private var update by mutableStateOf<UpdateUi>(UpdateUi.None)
    /** Neuestes Release, falls neuer als installiert - auch wenn mit "Spaeter" weggeklickt. */
    private var latestRelease by mutableStateOf<Updater.Release?>(null)
    /** Zeitpunkt der letzten erfolgreichen Abfrage (fuer "Stand hh:mm" bei Verbindungsproblemen). */
    private var lastOkMs by mutableStateOf(0L)
    private var refreshing by mutableStateOf(false)

    /** Sofort neu laden (nach einer Aktion), statt bis zu 10 s zu warten. */
    private val refreshNow = Channel<Unit>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = HaApi(this)
        configured = api.isConfigured()

        // Laedt alle 10 s, aber NUR solange die App im Vordergrund ist.
        Updater.cleanup(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Zurueck in der App: zuerst wieder die Zuhause-Adresse probieren.
                api.preferLocalAgain()
                while (isActive) {
                    load()
                    withTimeoutOrNull(10_000) { refreshNow.receive() }
                }
            }
        }
        // Update-Pruefung nicht nur beim Kaltstart, sondern bei jeder Rueckkehr
        // in die App (hoechstens alle 30 min). Android haelt die App oft tagelang
        // im Speicher - vorher wurde dann nie wieder nachgesehen.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (Updater.dueForCheck(this@MainActivity)) checkUpdate(manual = false)
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = HeatB, background = Bg, surface = Tile), typography = InterTypography) {
                Surface(modifier = Modifier.fillMaxSize(), color = Bg) { AppRoot() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Zurueck aus "Apps aus dieser Quelle zulassen": Erlaubnis neu pruefen.
        val u = update
        if (u is UpdateUi.Ready && u.needsPermission && Updater.canInstall(this)) {
            update = u.copy(needsPermission = false)
        }
    }

    private suspend fun checkUpdate(manual: Boolean) {
        val r = Updater.check()
        latestRelease = r
        when {
            r == null && manual -> Toast.makeText(this, "App ist aktuell (v${BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show()
            r == null -> {}
            manual || Updater.shouldPopUp(r.code, BuildConfig.VERSION_CODE, Updater.dismissedCode(this)) ->
                if (update == UpdateUi.None) update = UpdateUi.Available(r)
        }
    }

    private fun dismissUpdate(r: Updater.Release) {
        Updater.dismiss(this, r.code)
        update = UpdateUi.None
    }

    private suspend fun load() {
        if (!api.isConfigured()) return
        try {
            val snap = parseStatus(api.fetchStatus())
            // Adresse fuer unterwegs automatisch aus HA uebernehmen, solange keine eingetragen ist.
            if (api.remoteUrl.isBlank() && snap.house.remoteUrl.startsWith("https://")) {
                api.remoteUrl = snap.house.remoteUrl
            }
            snapshot = snap
            error = null
            lastOkMs = System.currentTimeMillis()
        } catch (e: Exception) {
            error = e.message ?: "Keine Verbindung"
        }
    }

    // ================================================================== Aktionen
    //
    // Nur das, was auch ein Fingertipp im Dashboard ausloest: Helfer setzen
    // oder das Betriebsart-Skript starten. Die Automationen und Raumskripte
    // in HA reagieren darauf und stellen die Thermostate. Die Entitaeten
    // kommen aus der Antwort von script.heizung_app_status, nicht aus der App.

    private fun call(domain: String, service: String, data: JSONObject) {
        lifecycleScope.launch {
            val err = api.callService(domain, service, data)
            if (err != null) Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
            // Die Raumskripte brauchen einen Moment, bis die Thermostate den neuen Wert melden.
            delay(1200)
            refreshNow.trySend(Unit)
        }
    }

    private fun setWish(r: RoomState, value: Double) {
        val c = wishCall(r.wishEntity, value) ?: return
        call(c.domain, c.service, JSONObject(c.data))
    }

    private fun setMode(h: HouseState, r: RoomState, mode: Betriebsart) {
        val script = h.betriebsartScript.takeIf { it.startsWith("script.") } ?: return
        call("script", script.removePrefix("script."), JSONObject().put("raum", r.id).put("modus", mode.wert))
    }

    private fun setHouseMode(h: HouseState, option: String) {
        if (h.modeEntity.isBlank()) return
        call(domainOf(h.modeEntity), "select_option", JSONObject().put("entity_id", h.modeEntity).put("option", option))
    }

    private fun setAllOff(h: HouseState, on: Boolean) {
        if (h.allOffEntity.isBlank()) return
        call(domainOf(h.allOffEntity), if (on) "turn_on" else "turn_off", JSONObject().put("entity_id", h.allOffEntity))
    }

    private fun setToggle(c: Control.Toggle, on: Boolean) =
        call(domainOf(c.entity), if (on) "turn_on" else "turn_off", JSONObject().put("entity_id", c.entity))

    private fun pickTime(c: Control.Time) {
        val parts = c.value.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 7
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 30
        TimePickerDialog(this, { _, hh, mm ->
            call(domainOf(c.entity), "set_datetime",
                JSONObject().put("entity_id", c.entity).put("time", String.format(Locale.ROOT, "%02d:%02d:00", hh, mm)))
        }, h, m, true).show()
    }

    private fun startDownload(r: Updater.Release) {
        update = UpdateUi.Downloading(r, 0f)
        lifecycleScope.launch {
            try {
                val apk = Updater.download(this@MainActivity, r) { p -> update = UpdateUi.Downloading(r, p) }
                val ready = UpdateUi.Ready(r, apk, needsPermission = !Updater.canInstall(this@MainActivity))
                update = ready
                if (!ready.needsPermission) Updater.install(this@MainActivity, apk)
            } catch (e: Exception) {
                update = UpdateUi.Failed(r, e.message ?: "Download fehlgeschlagen")
            }
        }
    }

    // ================================================================== Oberflaeche
    //
    // Gestaltung nach dem Vorbild tado / Nest (16.10.2026):
    //  - Die FARBE ist der Zustand: heizender Raum = orange Flaeche, Fenster
    //    offen = blau, sonst ruhiges Dunkelgrau. Keine Rahmen, keine Emojis.
    //  - Grosse, duenne Zahlen. Die Raumseite ist ganz in der Zustandsfarbe.

    @Composable
    private fun AppRoot() {
        var showSettings by remember { mutableStateOf(!configured) }
        var selectedId by remember { mutableStateOf<String?>(null) }
        var chatOpen by remember { mutableStateOf(false) }
        val snap = snapshot

        BackHandler(enabled = selectedId != null || chatOpen) {
            if (chatOpen) chatOpen = false else selectedId = null
        }

        Box(Modifier.fillMaxSize().background(Bg)) {
            val room = snap?.rooms?.firstOrNull { it.id == selectedId }
            when {
                showSettings -> {}
                room != null && snap != null -> RoomDetail(room, snap.house, onBack = { selectedId = null })
                else -> Box(Modifier.fillMaxSize().statusBarsPadding()) {
                    Home(snap, onOpenRoom = { selectedId = it }, onOpenSettings = { showSettings = true }, onOpenChat = { chatOpen = true })
                }
            }

            AnimatedVisibility(
                visible = chatOpen && !showSettings && snap != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) { if (snap != null) EvaChat(snap, onClose = { chatOpen = false }) }

            if (showSettings) {
                SettingsDialog(
                    onSave = { local, remote, token ->
                        api.localUrl = local; api.remoteUrl = remote; api.token = token
                        configured = api.isConfigured()
                        if (configured) { showSettings = false; refreshNow.trySend(Unit) }
                    },
                    onDismiss = { if (configured) showSettings = false },
                )
            }
            UpdateDialog()
        }
    }

    // ------------------------------------------------------------------ Start

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Home(snap: Snapshot?, onOpenRoom: (String) -> Unit, onOpenSettings: () -> Unit, onOpenChat: () -> Unit) {
        var confirmAllOff by remember { mutableStateOf(false) }
        val heating = snap?.rooms?.count { it.heating } ?: 0
        // Himmel oben passend zum Wetter draussen (Sonne, Wolken, Regen, Schnee, Nacht).
        if (snap != null) WeatherSky(skyOf(snap.house.weather, snap.house.isDay))

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { lifecycleScope.launch { refreshing = true; load(); refreshing = false } },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Header(snap, heating, onOpenSettings, onOpenChat) }
                error?.let { msg ->
                    val stand = if (snap != null && lastOkMs > 0)
                        " Angezeigt wird der Stand von " +
                            java.text.SimpleDateFormat("HH:mm", Locale.GERMANY).format(java.util.Date(lastOkMs)) + " Uhr."
                    else ""
                    item { Notice(msg + stand, Bad) }
                }
                if (snap != null && snap.version > STATUS_VERSION) {
                    item { Notice("Home Assistant liefert ein neueres Datenformat. Bitte die App aktualisieren.", Amber) }
                }
                if (snap == null) {
                    if (error == null) item { Text("Verbinde mit Home Assistant …", color = TextDim, fontSize = 14.sp) }
                    return@LazyColumn
                }

                if (snap.house.modeOptions.isNotEmpty() && snap.house.modeEntity.isNotBlank()) {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                            items(snap.house.modeOptions) { mode ->
                                ModeChip(mode, snap.house.mode == mode) { setHouseMode(snap.house, mode) }
                            }
                        }
                    }
                }

                // Kacheln: alle Raeume aus HA, danach "Alles aus".
                val withAllOff = snap.house.allOffEntity.isNotBlank()
                val count = snap.rooms.size + if (withAllOff) 1 else 0
                for (start in 0 until count step 2) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (i in start..minOf(start + 1, count - 1)) {
                                if (i < snap.rooms.size) {
                                    val r = snap.rooms[i]
                                    RoomTile(r, Modifier.weight(1f)) { onOpenRoom(r.id) }
                                } else {
                                    AllOffTile(snap.house, Modifier.weight(1f)) { confirmAllOff = true }
                                }
                            }
                            if (start + 1 >= count) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                item {
                    Column(Modifier.padding(top = 14.dp, start = 4.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("ZULETZT", color = TextDim, fontSize = 12.sp, letterSpacing = 1.sp)
                        if (snap.house.lastLog.isNotBlank()) Text(snap.house.lastLog, color = TextSoft, fontSize = 13.sp, lineHeight = 18.sp)
                        snap.house.facts.forEach { Text(stripEmoji(it), color = TextDim, fontSize = 13.sp, lineHeight = 18.sp) }
                        if (snap.house.modeHint.isNotBlank()) Text(snap.house.modeHint, color = TextDim, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }

        if (confirmAllOff && snap != null) {
            val turnOn = !snap.house.allOff
            AlertDialog(
                onDismissRequest = { confirmAllOff = false },
                title = { Text(if (turnOn) "Alle Räume ausschalten?" else "Heizung wieder einschalten?") },
                text = {
                    Text(
                        if (turnOn) "Alle Räume gehen auf Frostschutz, bis du wieder einschaltest."
                        else "Alle Räume heizen wieder nach Zeitplan, Präsenz und Hausmodus."
                    )
                },
                confirmButton = {
                    Button(onClick = { setAllOff(snap.house, turnOn); confirmAllOff = false },
                        colors = ButtonDefaults.buttonColors(containerColor = if (turnOn) Bad else Good)) {
                        Text(if (turnOn) "Ausschalten" else "Einschalten")
                    }
                },
                dismissButton = { TextButton(onClick = { confirmAllOff = false }) { Text("Abbrechen") } },
            )
        }
    }

    @Composable
    private fun Header(snap: Snapshot?, heating: Int, onOpenSettings: () -> Unit, onOpenChat: () -> Unit) {
        val h = snap?.house
        val title = when {
            snap == null -> "Heizung"
            h!!.allOff -> "Alles aus"
            h.summerLock -> "Sommerpause"
            h.vacation -> "Urlaub"
            heating == 1 -> "1 Raum heizt"
            heating > 1 -> "$heating Räume heizen"
            else -> "Alles warm"
        }
        val ok = snap != null && error == null
        val sub = buildString {
            if (h != null) {
                append("${fmt(h.outdoor)}°")
                weatherLabel(h.weather).takeIf { it.isNotEmpty() }?.let { append(" · $it") }
                append(" · ${h.mode}")
            }
            if (!ok) append(if (isEmpty()) "keine Verbindung" else " · keine Verbindung")
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(sub, color = if (ok) TextSoft else Bad, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.5).sp)
            }
            latestRelease?.let { r ->
                Box(
                    Modifier.padding(top = 4.dp).clip(CircleShape).background(HeatB).clickable { update = UpdateUi.Available(r) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) { Text("Update", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
                Spacer(Modifier.width(8.dp))
            }
            if (snap != null) {
                CircleIcon(onClick = onOpenChat) { Text("?", color = TextSoft, fontSize = 18.sp, fontWeight = FontWeight.Medium) }
                Spacer(Modifier.width(8.dp))
            }
            CircleIcon(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Einstellungen", tint = TextSoft) }
        }
    }

    @Composable
    private fun Notice(msg: String, color: Color) {
        Text(
            msg, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.18f)).padding(14.dp),
        )
    }

    @Composable
    private fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
        Box(
            Modifier.clip(CircleShape).background(if (selected) Color.White else Color(0x0FFFFFFF))
                .clickable(enabled = !selected) { onClick() }.padding(horizontal = 16.dp, vertical = 9.dp)
        ) {
            Text(text, color = if (selected) Color(0xFF111111) else TextSoft, fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
        }
    }

    @Composable
    private fun RoomTile(r: RoomState, modifier: Modifier, onClick: () -> Unit) {
        val look = lookOf(r)
        val breathe = if (look == Look.HEAT) {
            val t = rememberInfiniteTransition(label = "atem")
            t.animateFloat(0f, 0.14f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a").value
        } else 0f
        Column(
            modifier.height(150.dp).clip(RoundedCornerShape(22.dp)).background(look.tileBrush())
                .background(Color.White.copy(alpha = breathe))
                .clickable { onClick() }.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.label, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (r.mode != Betriebsart.AUTOMATIK) Text(r.mode.label, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
            }
            BigTemp(r.current, 44.sp, 20.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (look == Look.ECO) { Box(Modifier.size(6.dp).clip(CircleShape).background(EcoDot)); Spacer(Modifier.width(6.dp)) }
                Text(tileLine(r, look), color = Color.White.copy(alpha = if (look == Look.HEAT) 0.92f else 0.72f),
                    fontSize = 12.sp, maxLines = 2, lineHeight = 15.sp, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    @Composable
    private fun AllOffTile(h: HouseState, modifier: Modifier, onClick: () -> Unit) {
        Column(
            modifier.height(150.dp).clip(RoundedCornerShape(22.dp)).background(if (h.allOff) WinB else TileOff)
                .clickable { onClick() }.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Alles aus", color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(if (h.allOff) "Einschalten" else "Ausschalten", color = if (h.allOff) Color.White else TextDim,
                fontSize = 20.sp, fontWeight = FontWeight.Light, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
            Text(if (h.allOff) "Gerade aus – Frostschutz" else "Frostschutz für alle", color = TextDim, fontSize = 12.sp)
        }
    }

    // ------------------------------------------------------------------ Raumseite

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun RoomDetail(r: RoomState, h: HouseState, onBack: () -> Unit) {
        val look = lookOf(r)
        val dialMin = maxOf(r.wishMin, 15.0)
        val dialMax = minOf(r.wishMax, 25.0)
        Column(
            Modifier.fillMaxSize().background(look.pageBrush()).statusBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleIcon(bg = Color(0x26000000), onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Color.White) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(r.label, color = Color.White, fontSize = 22.sp)
                    Text(
                        when { r.windowOpen -> "Fenster offen · Heizung pausiert"; r.heating -> "Heizt · Ventil offen"; else -> "Ventil zu" },
                        color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp,
                    )
                }
            }

            WishDial(r, dialMin, dialMax, look)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                Fact("${fmt(r.current)}°", "IM RAUM")
                Fact(r.humidity?.let { "${fmt(it, 0)} %" } ?: "–", "FEUCHTE")
                r.infos.firstOrNull()?.let { Fact(it.value, it.label.uppercase()) }
            }

            if (h.betriebsartScript.isNotBlank()) {
                Segmented(
                    options = Betriebsart.entries.map { it.label },
                    selected = Betriebsart.entries.indexOf(r.mode),
                    accent = look.accent,
                    onSelect = { setMode(h, r, Betriebsart.entries[it]) },
                )
            }

            Glass {
                Text(r.reason, color = Color.White, fontSize = 15.sp, lineHeight = 20.sp)
                if (r.extras.isNotEmpty()) Text(r.extras.joinToString(" · "), color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                Text(
                    if (r.wishLocked) "Der Regler ist gesperrt, solange ein Notwert gilt. Dein Wunsch ist gesichert und kommt danach von selbst zurück."
                    else when (r.mode) {
                        Betriebsart.AUS -> "Betriebsart Aus: Frostschutz, egal was Zeitplan oder Hausmodus sagen."
                        Betriebsart.AUTOMATIK -> "Automatik: Zeitplan, Präsenz und Hausmodus entscheiden, wann der Wunschwert gilt."
                        Betriebsart.HEIZEN -> "Heizen: hält dauerhaft den Wunschwert, bis du zurückstellst."
                    },
                    color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 16.sp,
                )
            }

            val restInfos = r.infos.drop(1)
            if (restInfos.isNotEmpty() || r.controls.isNotEmpty() || r.hint.isNotBlank()) {
                Glass {
                    restInfos.forEach { InfoLine(it.label, it.value) }
                    r.controls.forEach { c ->
                        when (c) {
                            is Control.Toggle -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(c.label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Switch(checked = c.on, onCheckedChange = { setToggle(c, it) },
                                    colors = SwitchDefaults.colors(checkedTrackColor = Color.White, checkedThumbColor = look.accent))
                            }
                            is Control.Time -> Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { pickTime(c) }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(c.label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Text("${c.value.ifBlank { "–" }} Uhr", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    if (r.hint.isNotBlank()) Text(r.hint, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    /**
     * Runder Regler fuer die Wunschtemperatur (wie Nest/tado: weisser Ring auf
     * Farbflaeche). Gesendet wird erst 0,7 s nach der letzten Bewegung.
     */
    @Composable
    private fun WishDial(r: RoomState, minT: Double, maxT: Double, look: Look) {
        var local by remember(r.id) { mutableStateOf(r.wish ?: minT) }
        var pending by remember(r.id) { mutableStateOf<Double?>(null) }
        LaunchedEffect(r.wish) { if (pending == null && r.wish != null) local = r.wish }
        LaunchedEffect(pending) {
            val p = pending ?: return@LaunchedEffect
            delay(700)
            setWish(r, p)
            pending = null
        }
        val enabled = !r.wishLocked && r.wish != null && r.wishEntity.isNotBlank()
        val target = ((local - minT) / (maxT - minT)).toFloat().coerceIn(0f, 1f)
        val progress by animateFloatAsState(target, tween(250), label = "ring")
        val haptic = LocalHapticFeedback.current
        fun setLocal(v: Double) {
            val stepped = ((v / r.wishStep).roundToInt() * r.wishStep).coerceIn(minT, maxT)
            if (stepped != local) {
                local = stepped; pending = stepped
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(260.dp).alpha(if (enabled) 1f else 0.55f).pointerInput(enabled, minT, maxT) {
                    if (!enabled) return@pointerInput
                    detectDragGestures { change, _ ->
                        val c = Offset(size.width / 2f, size.height / 2f)
                        var a = (atan2(change.position.y - c.y, change.position.x - c.x) * 180f / PI).toFloat() - 135f
                        if (a < 0) a += 360f
                        if (a <= 270f) setLocal(minT + a / 270f * (maxT - minT))
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 6.dp.toPx()
                    val radius = size.minDimension / 2f - 14.dp.toPx()
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val tl = Offset(c.x - radius, c.y - radius)
                    val sz = Size(radius * 2, radius * 2)
                    drawArc(Color.White.copy(alpha = 0.22f), 135f, 270f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(Color.White, 135f, progress * 270f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                    val ang = (135f + progress * 270f) * PI / 180f
                    drawCircle(Color.White, 11.dp.toPx(), Offset(c.x + radius * cos(ang).toFloat(), c.y + radius * sin(ang).toFloat()))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (enabled) "WUNSCH" else "GESPERRT", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, letterSpacing = 1.sp)
                    Text("${fmt(if (enabled) local else r.wish)}°", color = Color.White, fontSize = 72.sp,
                        fontWeight = FontWeight.ExtraLight, letterSpacing = (-2).sp)
                    Text(if (pending != null) "wird gesendet …" else "regelt auf ${fmt(r.target)}°",
                        color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                StepButton("–", enabled) { setLocal(local - r.wishStep) }
                StepButton("+", enabled) { setLocal(local + r.wishStep) }
            }
        }
    }

    // ------------------------------------------------------------------ Eva

    /** Eva antwortet aus den Live-Daten von Home Assistant (keine KI, keine erfundenen Werte). */
    @Composable
    private fun EvaChat(snap: Snapshot, onClose: () -> Unit) {
        var messages by remember {
            mutableStateOf(listOf(ChatMessage("Eva", "Frag mich z. B. „Wo wird geheizt?“, „Warum ist das Bad kalt?“ oder „Wie spare ich?“. Ich antworte aus den aktuellen Daten deiner Heizung.", false)))
        }
        var input by remember { mutableStateOf("") }
        Column(
            Modifier.fillMaxWidth().height(500.dp).padding(10.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xF71B1D22)).padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Fragen zur Heizung", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("Antworten aus den Live-Daten", color = TextDim, fontSize = 12.sp)
                }
                CircleIcon(onClick = onClose) { Icon(Icons.Filled.Close, "Schließen", tint = TextSoft) }
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(messages) { ChatBubble(it) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = input, onValueChange = { input = it },
                    placeholder = { Text("Frage …", fontSize = 14.sp, color = TextDim) },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp), singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Tile, unfocusedContainerColor = Tile,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                CircleIcon(bg = HeatB, onClick = {
                    if (input.isNotBlank()) {
                        val q = input
                        messages = messages + ChatMessage("Du", q, true) + ChatMessage("Eva", evaAnswer(q, snap), false)
                        input = ""
                    }
                }) { Icon(Icons.AutoMirrored.Filled.Send, "Senden", tint = Color.White) }
            }
        }
    }

    // ------------------------------------------------------------------ Einstellungen

    @Composable
    private fun SettingsDialog(onSave: (String, String, String) -> Unit, onDismiss: () -> Unit) {
        var local by remember { mutableStateOf(api.localUrl) }
        var remote by remember { mutableStateOf(api.remoteUrl) }
        var token by remember { mutableStateOf(api.token) }
        var hint by remember { mutableStateOf<String?>(null) }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Tile),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚙️ Verbindung", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Installierte Version: ${BuildConfig.VERSION_NAME}", fontSize = 11.sp, color = Amber, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    Field(local, { local = it }, "Adresse zuhause (z. B. http://192.168.1.10:8123)")
                    Spacer(Modifier.height(10.dp))
                    Field(remote, { remote = it }, "Adresse unterwegs (Nabu Casa) – optional")
                    Spacer(Modifier.height(10.dp))
                    Field(token, { token = it }, "Langlebiger Zugriffstoken", secret = true)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Zuhause wird die lokale Adresse benutzt, unterwegs wechselt die App von selbst. Den Token erstellst du in Home Assistant unter Profil → Sicherheit. Er bleibt nur auf diesem Handy.",
                        fontSize = 11.sp, color = Muted, lineHeight = 15.sp,
                    )
                    hint?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Bad, fontSize = 12.sp) }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val l = HaApi.cleanUrl(local); val rm = HaApi.cleanUrl(remote); val t = token.trim()
                            hint = when {
                                l.isEmpty() && rm.isEmpty() -> "Mindestens eine Adresse wird gebraucht."
                                listOf(l, rm).any { it.isNotEmpty() && !it.startsWith("http") } -> "Adressen beginnen mit http:// oder https://"
                                t.isEmpty() -> "Ohne Token kann die App nicht mit Home Assistant sprechen."
                                else -> null
                            }
                            if (hint == null) onSave(l, rm, t)
                        },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Flame),
                    ) { Text("Speichern & verbinden", fontWeight = FontWeight.Bold) }
                    Text(
                        "Fehler oder Ideen? github.com/Delcore1/Smarte-Heizung → Issues",
                        fontSize = 11.sp, color = Muted, lineHeight = 15.sp,
                    )
                    Text(
                        "Fehler oder Ideen? github.com/Delcore1/Smarte-Heizung → Issues",
                        fontSize = 11.sp, color = Muted, lineHeight = 15.sp,
                    )
                    TextButton(onClick = { lifecycleScope.launch { checkUpdate(manual = true) } }) {
                        Text("Nach Updates suchen", fontSize = 12.sp, color = Amber)
                    }
                    if (configured) TextButton(onClick = onDismiss) { Text("Abbrechen", fontSize = 12.sp, color = Color.Gray) }
                }
            }
        }
    }

    @Composable
    private fun Field(value: String, onChange: (String) -> Unit, label: String, secret: Boolean = false) {
        OutlinedTextField(
            value = value, onValueChange = onChange, label = { Text(label, fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Flame, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
        )
    }

    // ------------------------------------------------------------------ Update

    @Composable
    private fun UpdateDialog() {
        when (val u = update) {
            UpdateUi.None -> {}
            is UpdateUi.Available -> AlertDialog(
                onDismissRequest = { dismissUpdate(u.r) },
                title = { Text("Update ${u.r.name} verfügbar") },
                text = {
                    Text("Installiert ist ${BuildConfig.VERSION_NAME}." + if (u.r.notes.isNotBlank()) "\n\nNeu:\n${u.r.notes}" else "")
                },
                confirmButton = {
                    Button(onClick = { startDownload(u.r) }, colors = ButtonDefaults.buttonColors(containerColor = Flame)) { Text("Herunterladen & installieren") }
                },
                dismissButton = { TextButton(onClick = { dismissUpdate(u.r) }) { Text("Später") } },
            )
            is UpdateUi.Downloading -> AlertDialog(
                onDismissRequest = {},
                title = { Text("Lade ${u.r.name} …") },
                text = {
                    Column {
                        if (u.progress >= 0f) LinearProgressIndicator(progress = { u.progress }, modifier = Modifier.fillMaxWidth(), color = Flame)
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Flame)
                        Spacer(Modifier.height(8.dp))
                        Text(if (u.progress >= 0f) "${(u.progress * 100).roundToInt()} %" else "", fontSize = 12.sp)
                    }
                },
                confirmButton = {},
            )
            is UpdateUi.Ready -> AlertDialog(
                onDismissRequest = { update = UpdateUi.None },
                title = { Text("Bereit zum Installieren") },
                text = {
                    Text(
                        if (u.needsPermission)
                            "Android muss der Heizungs-App einmalig erlauben, Updates zu installieren.\n\n1. „Einstellungen öffnen“ tippen\n2. „Aus dieser Quelle zulassen“ einschalten\n3. Zurück in die App und „Installieren“ tippen"
                        else "Version ${u.r.name} ist heruntergeladen. Im nächsten Fenster auf „Aktualisieren“ tippen. Deine Einstellungen bleiben erhalten."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (u.needsPermission) Updater.openInstallPermission(this@MainActivity)
                            else Updater.install(this@MainActivity, u.apk)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Flame),
                    ) { Text(if (u.needsPermission) "Einstellungen öffnen" else "Installieren") }
                },
                dismissButton = { TextButton(onClick = { update = UpdateUi.None }) { Text("Später") } },
            )
            is UpdateUi.Failed -> AlertDialog(
                onDismissRequest = { update = UpdateUi.None },
                title = { Text("Update fehlgeschlagen") },
                text = { Text(u.msg) },
                confirmButton = { Button(onClick = { startDownload(u.r) }, colors = ButtonDefaults.buttonColors(containerColor = Flame)) { Text("Nochmal") } },
                dismissButton = { TextButton(onClick = { update = UpdateUi.None }) { Text("Schließen") } },
            )
        }
    }

    // ------------------------------------------------------------------ Bausteine

    @Composable
    private fun CircleIcon(bg: Color = Color(0x12FFFFFF), onClick: () -> Unit, content: @Composable () -> Unit) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(bg).clickable { onClick() }, contentAlignment = Alignment.Center) { content() }
    }

    @Composable
    private fun BigTemp(v: Double?, size: androidx.compose.ui.unit.TextUnit, degSize: androidx.compose.ui.unit.TextUnit) {
        // Gradzeichen direkt an der Zahl (wie tado/Nest), nicht abgesetzt.
        Text("${fmt(v)}°", color = Color.White, fontSize = size, fontWeight = FontWeight.Light, letterSpacing = (-1.5).sp, lineHeight = size)
    }

    @Composable
    private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))
                .alpha(if (enabled) 1f else 0.4f).clickable(enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center,
        ) { Text(label, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Light) }
    }

    @Composable
    private fun Segmented(options: List<String>, selected: Int, accent: Color, onSelect: (Int) -> Unit) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x2E000000)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEachIndexed { i, label ->
                val sel = i == selected
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (sel) Color.White else Color.Transparent)
                        .clickable(enabled = !sel) { onSelect(i) }.padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (sel) accent else Color.White.copy(alpha = 0.85f), fontSize = 14.sp,
                        fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal)
                }
            }
        }
    }

    @Composable
    private fun Glass(content: @Composable () -> Unit) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.12f)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }

    @Composable
    private fun Fact(value: String, label: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = Color.White, fontSize = 20.sp)
            Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, letterSpacing = 0.6.sp)
        }
    }

    @Composable
    private fun InfoLine(label: String, value: String) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
            Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ------------------------------------------------------------------ Schrift

/**
 * Inter (SIL Open Font License, fonts/LICENSE-Inter-OFL.txt) fest in der App.
 * Vorher wurde die Systemschrift genommen - auf Samsung "SamsungOne", breit
 * und gesperrt; daher sah die App anders aus als der Entwurf.
 */
val Inter = FontFamily(
    Font(R.font.inter_extralight, FontWeight.ExtraLight),
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
)

val InterTypography: Typography = Typography().let { t ->
    fun TextStyle.i() = copy(fontFamily = Inter)
    t.copy(
        displayLarge = t.displayLarge.i(), displayMedium = t.displayMedium.i(), displaySmall = t.displaySmall.i(),
        headlineLarge = t.headlineLarge.i(), headlineMedium = t.headlineMedium.i(), headlineSmall = t.headlineSmall.i(),
        titleLarge = t.titleLarge.i(), titleMedium = t.titleMedium.i(), titleSmall = t.titleSmall.i(),
        bodyLarge = t.bodyLarge.i(), bodyMedium = t.bodyMedium.i(), bodySmall = t.bodySmall.i(),
        labelLarge = t.labelLarge.i(), labelMedium = t.labelMedium.i(), labelSmall = t.labelSmall.i(),
    )
}

// ------------------------------------------------------------------ Himmel

private fun skyColor(s: Sky) = when (s) {
    Sky.SUN -> Color(0xFFF59E0B)
    Sky.PARTLY -> Color(0xFFD9A15B)
    Sky.CLOUD -> Color(0xFF64748B)
    Sky.FOG -> Color(0xFF94A3B8)
    Sky.RAIN -> Color(0xFF3B6FB0)
    Sky.STORM -> Color(0xFF5B4BB0)
    Sky.SNOW -> Color(0xFFBFDBFE)
    Sky.NIGHT -> Color(0xFF2E3A8C)
}

/**
 * Der obere Bereich faerbt sich nach dem Wetter draussen und bewegt sich leicht:
 * Sonne = warmer Schein, der langsam wandert; Regen = feine fallende Striche;
 * Schnee = Flocken; Nacht = dunkelblau mit funkelnden Punkten.
 */
@Composable
fun WeatherSky(sky: Sky) {
    val color by animateColorAsState(skyColor(sky), tween(1500), label = "himmel")
    val anim = rememberInfiniteTransition(label = "wetter")
    val t by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val drift by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "d")
    Canvas(Modifier.fillMaxWidth().height(520.dp)) {
        val w = size.width; val h = size.height
        // Kraeftig genug, dass man das Wetter wirklich sieht (v1.0.90 war zu blass).
        val strength = when (sky) { Sky.SUN -> 0.62f; Sky.NIGHT -> 0.70f; Sky.SNOW -> 0.40f; Sky.FOG -> 0.42f; else -> 0.55f }
        val c = Offset(w * (0.25f + 0.5f * drift), -h * 0.15f)
        drawRect(Brush.radialGradient(listOf(color.copy(alpha = strength), color.copy(alpha = strength * 0.33f), Color.Transparent), c, h * 1.15f))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Bg), startY = h * 0.55f, endY = h))
        when (sky) {
            Sky.RAIN, Sky.STORM -> for (k in 0 until 40) {
                val x = (k * 97.3f) % w
                val y = ((k * 53.1f) + t * h * 3f * (0.8f + (k % 4) * 0.1f)) % h
                val a = 0.22f * (1f - y / h)
                drawLine(Color(0xFFBFD7FF).copy(alpha = a), Offset(x, y), Offset(x - 4f, y + 26f), 2f, cap = StrokeCap.Round)
            }
            Sky.SNOW -> for (k in 0 until 34) {
                val y = ((k * 71.7f) + t * h * (0.6f + (k % 5) * 0.08f)) % h
                val x = (k * 89.1f + sin(t * 2 * PI.toFloat() * 2 + k) * 18f) % w
                drawCircle(Color.White.copy(alpha = 0.55f * (1f - y / h)), 2.5f + (k % 3), Offset(x, y))
            }
            Sky.NIGHT -> for (k in 0 until 28) {
                val x = (k * 131.7f) % w
                val y = (k * 47.3f) % (h * 0.6f)
                val tw = 0.5f + 0.5f * sin(t * 2 * PI.toFloat() * 3 + k * 1.7f)
                drawCircle(Color.White.copy(alpha = 0.15f + 0.35f * tw * (1f - y / (h * 0.6f))), 1.6f, Offset(x, y))
            }
            else -> {}
        }
    }
}

// ------------------------------------------------------------------ Zustand -> Aussehen

enum class Look(val accent: Color) {
    HEAT(Color(0xFFC2410C)), WINDOW(Color(0xFF1F3448)), OFF(Color(0xFF374151)), ECO(Color(0xFF0F5E4E)), CALM(Color(0xFF7C4A1E));

    // Jeder Zustand hat seine Farbe (16.10.2026: vorher nur Heizen/Fenster farbig,
    // im Alltag war damit alles grau). Heizen bleibt die einzige helle Flaeche.
    fun tileBrush(): Brush = when (this) {
        HEAT -> Brush.linearGradient(listOf(HeatA, HeatB))
        WINDOW -> Brush.linearGradient(listOf(WinA, WinB))
        ECO -> Brush.linearGradient(listOf(Color(0xFF1F4A40), Color(0xFF142B26)))
        OFF -> Brush.linearGradient(listOf(Color(0xFF2A2F38), Color(0xFF1E2229)))
        CALM -> Brush.linearGradient(listOf(Color(0xFF4A3320), Color(0xFF2B1E14)))
    }

    fun pageBrush(): Brush = when (this) {
        HEAT -> Brush.verticalGradient(listOf(HeatA, HeatB, Color(0xFFB8410A)))
        WINDOW -> Brush.verticalGradient(listOf(Color(0xFF3E6687), WinB, Color(0xFF15222F)))
        ECO -> Brush.verticalGradient(listOf(Color(0xFF2B6556), Color(0xFF173A32), Color(0xFF0F1F1B)))
        OFF -> Brush.verticalGradient(listOf(Color(0xFF3A404B), Color(0xFF242830), Bg))
        CALM -> Brush.verticalGradient(listOf(Color(0xFF6B4626), Color(0xFF3A2716), Color(0xFF1C130C)))
    }
}

fun lookOf(r: RoomState): Look = when {
    r.windowOpen -> Look.WINDOW
    r.heating -> Look.HEAT
    r.tone == ReasonTone.OFF -> Look.OFF
    r.tone == ReasonTone.ECO -> Look.ECO
    else -> Look.CALM
}

/** Eine kurze Zeile fuer die Kachel - wie bei tado: was passiert, auf welchen Wert. */
fun tileLine(r: RoomState, look: Look): String = when (look) {
    Look.WINDOW -> "Fenster offen · pausiert"
    Look.HEAT -> "Heizt auf ${fmt(r.target)}°"
    Look.OFF -> r.reason.substringBefore(" – ")
    Look.ECO -> "${r.reason.substringBefore(" – ")} · ${fmt(r.target)}°"
    Look.CALM -> if ((r.current ?: 0.0) >= (r.target ?: 99.0)) "Warm genug · ${fmt(r.target)}°" else "Ziel ${fmt(r.target)}°"
}

/** Emojis vorne an HA-Fakten entfernen ("💧 Umwaelzpumpe aus" -> "Umwaelzpumpe aus"). */
fun stripEmoji(s: String): String = s.dropWhile { !it.isLetterOrDigit() }.ifBlank { s }

@Composable
fun ChatBubble(msg: ChatMessage) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start) {
        Text(
            msg.text, color = Color.White, fontSize = 14.sp, lineHeight = 19.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp, 18.dp, if (msg.isUser) 18.dp else 6.dp, if (msg.isUser) 6.dp else 18.dp))
                .background(if (msg.isUser) HeatB else Color(0xFF2A2D33)).padding(12.dp),
        )
    }
}

// ------------------------------------------------------------------ Texte aus Live-Daten

/** Eva: beantwortet Fragen aus den aktuellen Zustaenden. */
fun evaAnswer(query: String, s: Snapshot): String {
    val q = query.lowercase(Locale.GERMAN)
    val room = s.rooms.firstOrNull { r ->
        val n = r.label.lowercase(Locale.GERMAN)
        q.contains(n) || q.contains(n.take(4)) || (r.id == "badezimmer" && q.contains("bad"))
    }
    fun describe(r: RoomState) = buildString {
        append("${r.label}: ${fmt(r.current)} °C, ")
        append(if (r.heating) "Ventil offen, regelt auf ${fmt(r.target)} °C. " else "heizt gerade nicht (regelt auf ${fmt(r.target)} °C). ")
        append("Grund: ${r.reason}.")
        if (r.extras.isNotEmpty()) append(" Zusätzlich: ${r.extras.joinToString()}.")
        if (r.mode != Betriebsart.AUTOMATIK) append(" Betriebsart steht auf „${r.mode.label}“.")
    }
    return when {
        room != null -> describe(room)
        q.contains("wo") && q.contains("heiz") || q.contains("welche") -> {
            val heating = s.rooms.filter { it.heating }
            if (heating.isEmpty()) "Gerade ist in keinem Raum ein Ventil offen."
            else "Ventil offen in: " + heating.joinToString { "${it.label} (${fmt(it.current)} → ${fmt(it.target)} °C)" } + "."
        }
        q.contains("fenster") -> s.rooms.filter { it.windowOpen }.let { w ->
            if (w.isEmpty()) "Alle Fenster sind zu." else "Offen: ${w.joinToString { it.label }}. Dort ist die Heizung pausiert."
        }
        q.contains("spar") || q.contains("effizien") || q.contains("energie") -> buildString {
            append("Was ich in den Daten sehe: ")
            val tips = mutableListOf<String>()
            if (s.house.weatherBonus > 1.0) tips += "Der Witterungsbonus legt gerade ${fmt(s.house.weatherBonus)}° auf die Räume. Jedes Grad kostet grob 6 % Heizenergie – den Faktor kannst du in Home Assistant senken."
            s.rooms.filter { it.mode == Betriebsart.HEIZEN }.takeIf { it.isNotEmpty() }?.let { tips += "„Heizen“ ist dauerhaft an in: ${it.joinToString { r -> r.label }}. Auf Automatik zurückstellen, wenn nicht mehr nötig." }
            s.rooms.filter { it.windowOpen && it.heating }.takeIf { it.isNotEmpty() }?.let { tips += "Ventil noch offen bei offenem Fenster: ${it.joinToString { r -> r.label }}." }
            if (tips.isEmpty()) tips += "Nichts Auffälliges – Absenkung, Zeitpläne und Fensterpausen laufen."
            append(tips.joinToString(" "))
        }
        q.contains("außen") || q.contains("aussen") || q.contains("wetter") ->
            "Draußen ${fmt(s.house.outdoor)} °C. " + if (s.house.summerLock) "Sommersperre ist aktiv (ab ${fmt(s.house.limit, 0)} °C)." else "Witterungsbonus: +${fmt(s.house.weatherBonus)}°."
        q.contains("pumpe") -> if (s.house.pumpRunning) "Die Umwälzpumpe läuft, weil mindestens ein Raum Wärme braucht." else "Die Umwälzpumpe ist aus – kein Raum braucht gerade Wärme."
        q.contains("letzt") || q.contains("log") -> "Letzte Aktion der Heizung: ${s.house.lastLog.ifBlank { "–" }}"
        else -> "Hausmodus ${s.house.mode}, ${s.heatingCount} von ${s.rooms.size} Räumen heizen gerade. Frag mich nach einem Raum („Warum ist das Bad kalt?“), nach Fenstern, der Pumpe oder nach Spartipps."
    }
}
