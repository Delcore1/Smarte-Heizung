# Antwortformat von `script.heizung_app_status` (Version 1)

Die App kennt keine Entitäten fest. Sie ruft alle 10 s (nur solange sie offen ist)
`POST /api/services/script/heizung_app_status?return_response` auf und zeigt, was zurückkommt.
Bedient wird nur, was hier genannt ist.

## `haus`
| Feld | Typ | Bedeutung | leer/fehlt |
|---|---|---|---|
| `modus`, `modus_optionen`, `modus_entity` | Text, Liste, Entität (`input_select.*`) | Hausmodus-Knöpfe | Knöpfe ausgeblendet |
| `modus_hinweis` | Text | Hinweis unter „Zuletzt“ | – |
| `aussen` | Zahl | Außentemperatur | „–“ |
| `wetter`, `tag` | `weather.*`-Zustand, ja/nein | Himmelsfarbe oben | neutral |
| `alles_aus`, `alles_aus_entity` | ja/nein, `input_boolean.*` | Kachel „Alles aus“ | Kachel ausgeblendet |
| `sommersperre`, `urlaub` | ja/nein | Überschrift | – |
| `pumpe_laeuft`, `letzte_aktion`, `fakten` | ja/nein, Text, Textliste | Bereich „Zuletzt“ | – |
| `betriebsart_script` | `script.*` mit Feldern `raum`, `modus` (`aus`/`automatik`/`heizen`) | Umschalter Aus/Automatik/Heizen | Umschalter ausgeblendet |
| `fernzugriff_url` | `https://…` | wird als Adresse „unterwegs“ übernommen, wenn dort nichts steht | – |

## `raeume[]`
| Feld | Bedeutung |
|---|---|
| `id`, `label` | Kennung (für das Betriebsart-Skript) und Anzeigename |
| `ist`, `feuchte` | Raumtemperatur, Luftfeuchte |
| `ziel`, `heizt` | worauf das Thermostat regelt, ob das Ventil gerade offen ist |
| `fenster_offen` | Kachel blau |
| `wunsch`, `wunsch_entity`, `wunsch_min/max/schritt` | Regler. `input_number.*` → `set_value`, `climate.*` → `set_temperature` |
| `wunsch_gesperrt` | Regler gesperrt (z. B. Notwert aktiv) |
| `betriebsart` | `aus` / `automatik` / `heizen` |
| `grund`, `ton` | Satz „warum“; `ton` = `heizt` / `normal` / `eco` / `pause` / `aus` (Farbe) |
| `extras`, `infos[{label,wert}]` | Zusatzzeilen auf der Raumseite |
| `steuerung[]` | `{typ:'schalter', label, entity, an}` oder `{typ:'uhrzeit', label, entity, wert:'HH:MM'}` |
| `hinweis` | Erklärtext unten |

`version` > 1 → die App bittet um ein Update.
