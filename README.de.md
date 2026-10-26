# FOX Gleis Light

**Gleis** ist ein ruhiges Tool für Bahn und Bus auf dem [Light Phone III](https://www.thelightphone.com):
Verbindung finden und die Fahrkarte der Deutschen Bahn zeigen – mehr nicht.
Kein Konto, keine Werbung, kein Tracking.

🇬🇧 [English version](README.md)

<p>
  <img src="docs/screenshots/start.jpg" width="250" alt="Startseite">
  <img src="docs/screenshots/verbindungen.jpg" width="250" alt="Verbindungen">
  <img src="docs/screenshots/fahrkarten.jpg" width="250" alt="Fahrkarten">
</p>

## Was es kann

- **Verbindungssuche** – Von, Nach, Tag und Uhrzeit, dann *Suchen*. Zeigt Abfahrt, Ankunft, Dauer,
  Umstiege, Verspätungen und Gleise (Gleiswechsel werden markiert). Alle öffentlichen
  Verkehrsmittel: Züge, Fernbusse, S- und U-Bahn, Straßenbahn und Bus.
- **Auch im Funkloch** – die letzten Ergebnisse je Strecke werden auf dem Telefon gespeichert.
  Ohne Netz zeigt Gleis diese an (mit Hinweis, dass Verspätungen nicht aktuell sind).
- **Fahrkarten aus der Mail** – Gleis liest eine Adresse, die du *nur für Bahn-Tickets* nutzt,
  holt Mails der Deutschen Bahn (`bahn.de`, `deutschebahn.com`, `bahn.com`, `db.com`, `db.de`,
  auch weitergeleitete) und speichert die PDF-Fahrkarten auf dem Telefon. Datum, Uhrzeit und
  Strecke werden aus dem Ticket gelesen.
- **Barcode bildschirmfüllend** – der Aztec-Code wird pixelgenau aus dem PDF ausgeschnitten (nie
  neu gezeichnet) und schwarz auf weiß über den ganzen Bildschirm gezeigt. Funktioniert offline
  und hat eine echte Kontrolle bestanden.
- **Räumt selbst auf** – Tickets verschwinden 14 Tage nach der Fahrt automatisch. Löschen geht
  auch von Hand (Papierkorb, mit Rückfrage). Die Mails im Postfach bleiben immer unverändert.

**Was es nicht kann:** Tickets kaufen (dafür gibt es keine offene Schnittstelle) und das
Deutschlandticket zeigen (dessen Code ist meist dynamisch).

## Datenschutz

| Was | Wohin | Wozu |
|---|---|---|
| Bahnhofsnamen, Start/Ziel, Zeit | [`api.transitous.org`](https://transitous.org) – offener Community-Routenplaner mit amtlichen Fahrplandaten (DELFI) | Fahrplan |
| Ticket-Adresse + App-Passwort | IMAP-/POP3-Server deines Anbieters (nur TLS, Zertifikat und Hostname geprüft) | Ticket-Mails lesen |

- Das Postfach wird nur **lesend** geöffnet (IMAP `EXAMINE`/`BODY.PEEK`, POP3 ohne `DELE`):
  nichts wird als gelesen markiert, verschoben oder gelöscht.
- Verarbeitet werden nur Mails der letzten 365 Tage, in denen eine Bahn-Adresse vorkommt.
- Das App-Passwort liegt mit einem Schlüssel aus dem Android-Keystore verschlüsselt auf dem Gerät.
- Keine Statistik, keine weiteren Dienste außer den beiden oben.

## Ticket-Adresse einrichten (einmalig)

Gleis bekommt **nicht** dein normales Postfach, sondern nur eine Adresse für Bahn-Tickets.

1. Neue Adresse anlegen, z. B. `deinname.tickets@web.de` (web.de, GMX, Gmail, Posteo … gehen alle).
2. Dort IMAP- (oder POP3-)Zugriff einschalten; bei Zwei-Faktor-Anmeldung ein **App-Passwort** anlegen.
3. Diese Adresse bei der Bahn nutzen (bahn.de → *Mein Konto* oder beim Kauf ohne Konto).
   Ältere Tickets kannst du einfach dorthin weiterleiten.
4. In Gleis: ⚙ → Adresse und App-Passwort eintragen → *TESTEN*.

Server, Port, Protokoll (IMAP/POP3), Verschlüsselung (SSL/TLS oder STARTTLS) und Ordner lassen
sich von Hand setzen; leer gelassen werden sie automatisch gewählt. SMTP wird nicht gebraucht.

## Installieren

Am einfachsten mit einem GitHub-Installer wie **Bright Market** auf dem Light Phone:
`github.com/meisterreineke83/FOX-Gleis-Light` hinzufügen. Jede Version liegt unter
[Releases](https://github.com/meisterreineke83/FOX-Gleis-Light/releases) als `FOX-Gleis-Light.apk`.

Alternativ:
- **Tool Manager (WLAN):** Entwicklermodus im [Light-Dashboard](https://dashboard.thelightphone.com)
  einschalten, Tool Manager am Telefon starten, unter *Developer Keys*
  [`docs/sideloading/light_debug_signing_key.txt`](docs/sideloading/light_debug_signing_key.txt)
  hochladen, dann unter *Upload Tools* die APK. Siehe [`docs/sideloading`](docs/sideloading/README.md).
- **adb:** `adb install FOX-Gleis-Light.apk`

Die APK ist mit dem Entwicklerschlüssel der Light SDK signiert, (noch) nicht von Light.

## Bauen

GitHub Actions baut jede Version automatisch (`.github/workflows/build-apk.yml`), genauso wie
Lights eigener Build-Server:

```
./gradlew :tool:testReleaseUnitTest :tool:assembleRelease -DlightSdk.toolOnly=true -DlightSdk.abiFilters=arm64-v8a
```

Der Code liegt in [`tool/src/main/kotlin/io/github/lightbahn`](tool/src/main/kotlin/io/github/lightbahn);
eine Übersicht der Dateien steht im [englischen README](README.md#code). Der Rest des Repositorys
ist die [Light SDK](https://github.com/lightphone/light-sdk) (Stand Oktober 2026).

## Lizenz

MIT – siehe [LICENSE](LICENSE). Keine Verbindung zur Deutschen Bahn oder zu Light.
