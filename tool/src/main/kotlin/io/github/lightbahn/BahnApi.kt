package io.github.lightbahn

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

/**
 * Fahrplandaten über Transitous (api.transitous.org), einen offenen, von der Community
 * betriebenen Routenplaner auf Basis von MOTIS. Er nutzt die offiziellen deutschen
 * Fahrplandaten (DELFI) und, wo verfügbar, Echtzeitdaten. Kein API-Schlüssel nötig.
 *
 * Die Antworten werden hier auf die einfachen Datenklassen unten abgebildet,
 * damit der Rest des Tools nicht von der Schnittstelle abhängt.
 */
private const val API_BASE = "https://api.transitous.org"
private const val USER_AGENT = "FOX-Gleis-Light/0.6 (Light Phone III community tool; github.com/meisterreineke83/FOX-Gleis-Light)"

// ---------------------------------------------------------------- Daten fürs Tool

@Serializable
data class Station(
    val type: String? = null,
    val id: String? = null,
    val name: String? = null,
    val area: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
)

@Serializable
data class Line(
    val name: String? = null,
)

@Serializable
data class StopRef(
    val id: String? = null,
    val name: String? = null,
)

@Serializable
data class Leg(
    val origin: StopRef? = null,
    val destination: StopRef? = null,
    val departure: String? = null,
    val plannedDeparture: String? = null,
    val departureDelay: Int? = null,
    val departurePlatform: String? = null,
    val plannedDeparturePlatform: String? = null,
    val arrival: String? = null,
    val plannedArrival: String? = null,
    val arrivalDelay: Int? = null,
    val arrivalPlatform: String? = null,
    val plannedArrivalPlatform: String? = null,
    val line: Line? = null,
    val direction: String? = null,
    val walking: Boolean = false,
    val cancelled: Boolean = false,
    val distance: Int? = null,
    val realTime: Boolean = false,
)

@Serializable
data class Journey(
    val legs: List<Leg> = emptyList(),
)

data class JourneyPage(
    val journeys: List<Journey>,
    val laterRef: String?,
)

// ---------------------------------------------------------------- Transitous-Antworten

@Serializable
internal data class MotisArea(
    val name: String? = null,
    val default: Boolean = false,
)

@Serializable
internal data class MotisMatch(
    val type: String? = null,
    val id: String? = null,
    val name: String? = null,
    val areas: List<MotisArea> = emptyList(),
    val lat: Double? = null,
    val lon: Double? = null,
)

/** Fehlerantwort des Fahrplandienstes (HTTP 4xx/5xx) – im Gegensatz zu fehlendem Netz. */
internal class ApiException(val status: Int, message: String) : IllegalStateException(message)

/**
 * Manche Bahnhöfe liefert die Suche zusätzlich als Gruppe mit der Endung "_G".
 * Diese Gruppen-Kennungen kennt die Verbindungssuche nicht – daher ohne Endung verwenden.
 */
internal fun stopId(id: String): String = id.removeSuffix("_G")

@Serializable
internal data class MotisPlace(
    val name: String? = null,
    val stopId: String? = null,
    val departure: String? = null,
    val scheduledDeparture: String? = null,
    val arrival: String? = null,
    val scheduledArrival: String? = null,
    val track: String? = null,
    val scheduledTrack: String? = null,
    val cancelled: Boolean = false,
)

@Serializable
internal data class MotisLeg(
    val mode: String? = null,
    val from: MotisPlace? = null,
    val to: MotisPlace? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val scheduledStartTime: String? = null,
    val scheduledEndTime: String? = null,
    val realTime: Boolean = false,
    val headsign: String? = null,
    val displayName: String? = null,
    val tripShortName: String? = null,
    val routeShortName: String? = null,
    val cancelled: Boolean = false,
    val distance: Double? = null,
)

@Serializable
internal data class MotisItinerary(
    val legs: List<MotisLeg> = emptyList(),
)

@Serializable
internal data class MotisPlan(
    val itineraries: List<MotisItinerary> = emptyList(),
    val nextPageCursor: String? = null,
)

// ---------------------------------------------------------------- Client

internal class BahnApi {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
        }
    }

    suspend fun searchStations(query: String): List<Station> {
        val response = client.get("$API_BASE/api/v1/geocode") {
            header("User-Agent", USER_AGENT)
            parameter("text", query.trim())
            parameter("type", "STOP")
            parameter("language", "de")
        }
        response.ensureOk()
        val matches: List<MotisMatch> = response.body()
        return matches
            .filter { it.type == "STOP" && !it.id.isNullOrBlank() && !it.name.isNullOrBlank() }
            .map { match ->
                Station(
                    type = "stop",
                    id = stopId(match.id!!),
                    name = match.name,
                    area = match.areas.firstOrNull { it.default }?.name,
                    lat = match.lat,
                    lon = match.lon,
                )
            }
            .distinctBy { it.id }
            .distinctBy { it.name + "|" + it.area }
            .take(8)
    }

    /** Für jede Station die Angabe, mit der die Verbindungssuche zuletzt geklappt hat. */
    private val workingPlace = mutableMapOf<String, String>()

    private fun coords(s: Station): String? =
        if (s.lat != null && s.lon != null) "${s.lat},${s.lon}" else null

    /** Koordinaten einer Station; fehlen sie (ältere Speicherung), wird der Name neu gesucht. */
    private suspend fun coordsOrLookup(s: Station): String? =
        coords(s) ?: s.name?.let { name ->
            runCatching { searchStations(name).let { r -> r.firstOrNull { it.name == name } ?: r.firstOrNull() } }
                .getOrNull()?.let(::coords)
        }

    /**
     * @param departure Abfahrtszeit, null = jetzt
     * @param laterRef  Fortsetzungs-Token für "spätere Verbindungen"
     */
    suspend fun searchJourneys(
        from: Station,
        to: Station,
        departure: OffsetDateTime?,
        laterRef: String? = null,
    ): JourneyPage {
        val fromKey = from.id.orEmpty()
        val toKey = to.id.orEmpty()
        val fromPlace = workingPlace[fromKey] ?: stopId(fromKey)
        val toPlace = workingPlace[toKey] ?: stopId(toKey)
        val page = try {
            plan(fromPlace, toPlace, departure, laterRef)
        } catch (e: ApiException) {
            // Kennung im Fahrplan unbekannt → über die Koordinaten der Bahnhöfe suchen.
            if (e.status != 404) throw e
            val fromCoords = coordsOrLookup(from)
            val toCoords = coordsOrLookup(to)
            if (fromCoords == null && toCoords == null) throw e
            val p = plan(fromCoords ?: fromPlace, toCoords ?: toPlace, departure, laterRef)
            fromCoords?.let { workingPlace[fromKey] = it }
            toCoords?.let { workingPlace[toKey] = it }
            p
        }
        return JourneyPage(
            journeys = page.itineraries.map { itinerary ->
                Journey(itinerary.legs.map { it.toLeg(from.name, to.name) })
            },
            laterRef = page.nextPageCursor,
        )
    }

    private suspend fun plan(fromPlace: String, toPlace: String, departure: OffsetDateTime?, laterRef: String?): MotisPlan {
        val response = client.get("$API_BASE/api/v5/plan") {
            header("User-Agent", USER_AGENT)
            parameter("fromPlace", fromPlace)
            parameter("toPlace", toPlace)
            parameter("numItineraries", 6)
            parameter("language", "de")
            if (laterRef != null) {
                parameter("pageCursor", laterRef)
            } else if (departure != null) {
                parameter("time", departure.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            }
        }
        response.ensureOk()
        return response.body()
    }

    fun close() = client.close()

    private suspend fun HttpResponse.ensureOk() {
        if (!status.isSuccess()) {
            val text = runCatching { bodyAsText() }.getOrDefault("")
            throw ApiException(
                status.value,
                when {
                    status.value == 429 -> "Zu viele Anfragen. Bitte kurz warten."
                    status.value >= 500 -> "Fahrplandienst gerade nicht erreichbar (${status.value})."
                    text.contains("timetable location", ignoreCase = true) ->
                        "Bahnhof im Fahrplan nicht gefunden. Bitte Start oder Ziel neu auswählen."
                    else -> "Fahrplandienst meldet Fehler ${status.value}."
                },
            )
        }
    }
}

/** Bei Suche über Koordinaten heißen Start und Ziel "START"/"END" – dann den Bahnhofsnamen zeigen. */
private fun placeName(name: String?, start: String?, end: String?): String? = when (name) {
    "START" -> start ?: name
    "END" -> end ?: name
    else -> name
}

private fun MotisLeg.toLeg(startName: String? = null, endName: String? = null): Leg {
    val walking = mode == "WALK" || mode == "BIKE"
    val depPlanned = from?.scheduledDeparture ?: scheduledStartTime
    val depActual = from?.departure ?: startTime
    val arrPlanned = to?.scheduledArrival ?: scheduledEndTime
    val arrActual = to?.arrival ?: endTime
    return Leg(
        origin = StopRef(from?.stopId, placeName(from?.name, startName, endName)),
        destination = StopRef(to?.stopId, placeName(to?.name, startName, endName)),
        departure = depActual,
        plannedDeparture = depPlanned,
        departureDelay = delaySeconds(depPlanned, depActual),
        departurePlatform = from?.track,
        plannedDeparturePlatform = from?.scheduledTrack,
        arrival = arrActual,
        plannedArrival = arrPlanned,
        arrivalDelay = delaySeconds(arrPlanned, arrActual),
        arrivalPlatform = to?.track,
        plannedArrivalPlatform = to?.scheduledTrack,
        line = if (walking) null else Line(displayName ?: tripShortName ?: routeShortName),
        direction = headsign,
        walking = walking,
        cancelled = cancelled || from?.cancelled == true || to?.cancelled == true,
        distance = distance?.roundToInt(),
        realTime = realTime,
    )
}

private fun delaySeconds(planned: String?, actual: String?): Int? {
    if (planned == null || actual == null) return null
    return try {
        Duration.between(OffsetDateTime.parse(planned), OffsetDateTime.parse(actual)).seconds.toInt()
    } catch (e: DateTimeParseException) {
        null
    }
}
