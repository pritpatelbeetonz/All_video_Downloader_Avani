package app.messages.data.gif

import app.messages.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A GIF result: a small [previewUrl] for the grid + the full [gifUrl] to send. */
data class TenorGif(val id: String, val previewUrl: String, val gifUrl: String)

/**
 * Tenor (Google) GIF search (§F5 attachment GIF). Uses the v2 API with
 * `BuildConfig.TENOR_API_KEY`; when that's blank it falls back to the public v1
 * demo key so search works out of the box for testing. Plain `HttpURLConnection`
 * + kotlinx.serialization — no extra networking dependency.
 *
 * Privacy: the user's search query is sent to Google/Tenor only when they
 * actively search. Declare this in Data Safety once GIF search ships enabled.
 */
@Singleton
class TenorClient @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String, limit: Int = 24): List<TenorGif> = withContext(Dispatchers.IO) {
        val configuredKey = BuildConfig.TENOR_API_KEY
        val useV2 = configuredKey.isNotBlank()
        val key = if (useV2) configuredKey else DEMO_KEY
        val base = if (useV2) "https://tenor.googleapis.com/v2/search" else "https://g.tenor.com/v1/search"
        val filter = if (useV2) "tinygif,gif" else "minimal"
        val q = URLEncoder.encode(query, "UTF-8")
        val url = "$base?q=$q&key=$key&limit=$limit&contentfilter=high&media_filter=$filter"

        val body = httpGet(url) ?: return@withContext emptyList()
        runCatching { if (useV2) parseV2(body) else parseV1(body) }.getOrDefault(emptyList())
    }

    /** Downloads the raw GIF bytes for the chosen result. */
    suspend fun download(gifUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(gifUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            try {
                if (conn.responseCode in 200..299) conn.inputStream.readBytes() else null
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun httpGet(urlStr: String): String? = runCatching {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
        }
        try {
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun parseV2(body: String): List<TenorGif> =
        json.decodeFromString<V2Response>(body).results.mapNotNull { r ->
            val gif = r.mediaFormats["gif"]?.url ?: return@mapNotNull null
            TenorGif(r.id, r.mediaFormats["tinygif"]?.url ?: gif, gif)
        }

    private fun parseV1(body: String): List<TenorGif> =
        json.decodeFromString<V1Response>(body).results.mapNotNull { r ->
            val media = r.media.firstOrNull() ?: return@mapNotNull null
            val gif = media["gif"]?.url ?: return@mapNotNull null
            TenorGif(r.id, media["tinygif"]?.url ?: gif, gif)
        }

    private companion object {
        const val DEMO_KEY = "LIVDSRZULELA" // Tenor public v1 demo key
    }
}

@Serializable private data class V2Response(val results: List<V2Result> = emptyList())

@Serializable private data class V2Result(
    val id: String = "",
    @SerialName("media_formats") val mediaFormats: Map<String, TenorMediaItem> = emptyMap(),
)

@Serializable private data class V1Response(val results: List<V1Result> = emptyList())

@Serializable private data class V1Result(
    val id: String = "",
    val media: List<Map<String, TenorMediaItem>> = emptyList(),
)

@Serializable private data class TenorMediaItem(val url: String = "")
