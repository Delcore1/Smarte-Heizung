# Änderungen

Neueste oben. Die Versionsnummer vergibt GitHub beim Bauen (`1.0.<Laufnummer>`).
Der oberste Abschnitt erscheint als Text im Release und in der Update-Meldung der App.

## Kleines Update

- In den Einstellungen steht jetzt, wo man Fehler und Ideen meldet (GitHub-Issues).
- Erstes Update mit dem neuen Signierschlüssel – prüft den Update-Weg in der App.

## Erste öffentliche Test-Version

- Alle Räume auf einen Blick, **Farbe = Zustand**: heizt (orange), warm genug, Absenkung, Fenster offen, aus.
- Raumseite in der Zustandsfarbe mit Drehregler für die Wunschtemperatur, Fakten und dem Grund in einem Satz.
- **Himmel nach Wetter**: Sonne, Wolken, Regen, Schnee, Gewitter, Nacht – aus der Wetter-Entität in Home Assistant.
- Die App kennt keine Entitäten fest: Alles kommt aus `script.heizung_app_status` (Vorlage in `homeassistant/`).
  Sie bedient nur Helfer, Thermostate und Skripte, die Home Assistant ihr nennt.
- Zwei Adressen (zuhause/unterwegs) mit automatischem Wechsel bei Netzwechsel.
- **Selbst-Update** aus den GitHub-Releases mit Installieren-Knopf; „Später“ wird gemerkt.
- Szenario-Tests laufen vor jedem Release.
