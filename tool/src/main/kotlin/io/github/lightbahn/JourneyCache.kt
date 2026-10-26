package io.github.lightbahn

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.OffsetDateTime

@Serializable
internal data class SavedSearch(
    val fromId: String,
    val toId: String,
    /** Gewünschte Abfahrt (ISO) oder null = "jetzt" zum Zeitpunkt der Suche. */
    val departure: String?,
    /** Wann gesucht wurde (ISO). */
    val savedAt: String,
    val journeys: List<Journey>,
)

@Serializable
private data class SavedSearches(val searches: List<SavedSearch> = emptyList())

/**
 * Speichert die letzten Suchergebnisse je Strecke auf dem Telefon.
 * Ohne Netz (z. B. im Funkloch) zeigt das Tool dann die gespeicherten Verbindungen.
 */
internal class JourneyCache(filesDir: File) {
    private val file = File(filesDir, "searches.json")
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun save(fromId: String, toId: String, departure: OffsetDateTime?, journeys: List<Journey>) {
        if (journeys.isEmpty()) return
        val entry = SavedSearch(
            fromId = fromId,
            toId = toId,
            departure = departure?.toString(),
            savedAt = OffsetDateTime.now(BERLIN).toString(),
            journeys = journeys,
        )
        val others = read().searches.filterNot { it.fromId == fromId && it.toId == toId && it.departure == entry.departure }
        write(SavedSearches((listOf(entry) + others).take(MAX_SEARCHES)))
    }

    /** Ergänzt "spätere Verbindungen" zur zuletzt gespeicherten Suche dieser Strecke. */
    @Synchronized
    fun append(fromId: String, toId: String, journeys: List<Journey>) {
        val all = read().searches
        val i = all.indexOfFirst { it.fromId == fromId && it.toId == toId }
        if (i < 0 || journeys.isEmpty()) return
        val updated = all.toMutableList()
        updated[i] = all[i].copy(journeys = (all[i].journeys + journeys).distinct())
        write(SavedSearches(updated))
    }

    /**
     * Gespeicherte Verbindungen für diese Strecke, die noch nicht vorbei sind.
     * Bevorzugt eine Suche mit derselben gewünschten Abfahrt, sonst die jüngste der Strecke.
     */
    @Synchronized
    fun find(fromId: String, toId: String, departure: OffsetDateTime?): SavedSearch? {
        val now = OffsetDateTime.now(BERLIN)
        val matches = read().searches.filter { it.fromId == fromId && it.toId == toId }
        val best = matches.firstOrNull { it.departure == departure?.toString() } ?: matches.firstOrNull() ?: return null
        val wanted = departure ?: now
        val upcoming = best.journeys.filter { journey ->
            val arrival = journey.legs.lastOrNull()?.let { it.arrival ?: it.plannedArrival }
            val dep = journey.legs.firstOrNull()?.let { it.departure ?: it.plannedDeparture }
            val arrivalTime = arrival?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
            val depTime = dep?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
            // Noch nicht angekommen und nicht deutlich vor der gewünschten Abfahrt.
            (arrivalTime == null || arrivalTime.isAfter(now)) &&
                (depTime == null || !depTime.isBefore(wanted.minusMinutes(30)))
        }
        return if (upcoming.isEmpty()) null else best.copy(journeys = upcoming)
    }

    private fun read(): SavedSearches =
        runCatching { json.decodeFromString<SavedSearches>(file.readText()) }.getOrDefault(SavedSearches())

    private fun write(data: SavedSearches) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(data))
        tmp.renameTo(file)
    }

    private companion object {
        const val MAX_SEARCHES = 10
    }
}
