# FOX Gleis Light

**Gleis** is a calm public-transport tool for the [Light Phone III](https://www.thelightphone.com):
find a connection in Germany and show your Deutsche Bahn ticket – nothing more.
No account, no ads, no tracking.

🇩🇪 [Deutsche Version](README.de.md)

<p>
  <img src="docs/screenshots/start.jpg" width="250" alt="Start screen">
  <img src="docs/screenshots/verbindungen.jpg" width="250" alt="Connections">
  <img src="docs/screenshots/fahrkarten.jpg" width="250" alt="Tickets">
</p>

> The app itself is in German, since it is made for travelling in Germany.

## What it does

- **Journey planner** – From, To, day and time; then *Suchen* (search). Shows departure, arrival,
  duration, changes, delays and platforms (platform changes are marked). Covers all public
  transport: trains, long-distance buses, S-/U-Bahn, trams and buses.
- **Works in dead zones** – the latest results per route are saved on the phone. Without signal,
  Gleis shows them (with a note that delays are not live).
- **Tickets from e-mail** – Gleis reads a mailbox you set up *only for train tickets*, picks up
  mails from Deutsche Bahn (`bahn.de`, `deutschebahn.com`, `bahn.com`, `db.com`, `db.de`, also
  forwarded ones) and stores the PDF tickets on the phone. Date, time and route are read from the
  ticket.
- **Full-screen barcode** – the Aztec code is cut out of the PDF pixel-for-pixel (never re-drawn)
  and shown black on white across the whole screen. Works offline and has passed a real
  ticket inspection.
- **Tidy by itself** – tickets are removed automatically 14 days after the journey. You can also
  delete them yourself (trash icon, with confirmation). Mails in your mailbox are never touched.

**What it can't do:** buy tickets (there is no open API for that) or show the Deutschlandticket
(its code is usually dynamic).

## Privacy and data

| What | Sent to | Why |
|---|---|---|
| Station names, from/to, time | [`api.transitous.org`](https://transitous.org) – open, community-run journey planner using official German timetable data (DELFI) | Timetable |
| Ticket mailbox address + app password | Your mail provider's IMAP/POP3 server (TLS only, certificate and hostname verified) | Read ticket mails |

- The mailbox is opened **read-only** (IMAP `EXAMINE` / `BODY.PEEK`, POP3 without `DELE`):
  nothing is marked as read, moved or deleted.
- Only mails mentioning a Deutsche Bahn domain from the last 365 days are processed.
- The app password is encrypted with a key from the Android Keystore.
- No analytics, no third-party services besides the two above.

## Set up a ticket mailbox (once)

Gleis does **not** get access to your main mailbox – only to an address used for train tickets.

1. Create a new address, e.g. `yourname.tickets@web.de` (web.de, GMX, Gmail, Posteo, … all work).
2. Enable IMAP (or POP3) access there. With two-factor login, create an **app password**.
3. Use this address for your Deutsche Bahn bookings (bahn.de → *Mein Konto*, or when buying
   without an account). Older tickets can simply be forwarded to it.
4. In Gleis: ⚙ → enter address and app password → *TESTEN*.

Server, port, protocol (IMAP/POP3), encryption (SSL/TLS or STARTTLS) and folder can be set
manually; left empty they are chosen automatically. SMTP is not needed.

## Install

The easiest way is a GitHub-based installer such as **Bright Market** on the Light Phone:
add `github.com/meisterreineke83/FOX-Gleis-Light`. Every version is published under
[Releases](https://github.com/meisterreineke83/FOX-Gleis-Light/releases) as `FOX-Gleis-Light.apk`.

Other ways:
- **Tool Manager (Wi-Fi):** enable developer mode in the [Light dashboard](https://dashboard.thelightphone.com),
  start the Tool Manager on the phone, upload
  [`docs/sideloading/light_debug_signing_key.txt`](docs/sideloading/light_debug_signing_key.txt) under
  *Developer Keys*, then the APK under *Upload Tools*. See [`docs/sideloading`](docs/sideloading/README.md).
- **adb:** `adb install FOX-Gleis-Light.apk`

The APK is signed with the Light SDK development key; it is not (yet) signed by Light.

## Build

GitHub Actions builds every version automatically (`.github/workflows/build-apk.yml`), the same
way Light's build server does:

```
./gradlew :tool:testReleaseUnitTest :tool:assembleRelease -DlightSdk.toolOnly=true -DlightSdk.abiFilters=arm64-v8a
```

## Code

The tool lives in [`tool/src/main/kotlin/io/github/lightbahn`](tool/src/main/kotlin/io/github/lightbahn):

| File | Purpose |
|---|---|
| `BahnScreen.kt` | UI, built with the Light SDK components |
| `BahnViewModel.kt` | State and navigation |
| `BahnApi.kt` | Timetable queries (Transitous) |
| `JourneyCache.kt` | Saved results for offline use |
| `MailSettings.kt`, `ImapClient.kt` | Small read-only IMAP/POP3 client (the SDK allows no mail library) |
| `MimeParser.kt` | Subject, text and PDF attachments from a mail |
| `PdfText.kt`, `TicketInfo.kt` | Reads date, time and route from the ticket PDF |
| `TicketStore.kt` | Local ticket storage, cleanup, encrypted password |
| `TicketRenderer.kt` | Renders the PDF and cuts out the barcode unchanged |
| `Formatting.kt` | Times, delays, departure input |

Everything else in this repository is the [Light SDK](https://github.com/lightphone/light-sdk)
(October 2026); its original README is in [`README.light-sdk.md`](README.light-sdk.md).

## License

MIT – see [LICENSE](LICENSE). Not affiliated with Deutsche Bahn or Light.
