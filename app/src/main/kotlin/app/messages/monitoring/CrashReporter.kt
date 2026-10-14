package app.messages.monitoring

import android.content.Context
import android.os.Build
import app.messages.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Crash + non-fatal reporting seam (§7.1 "Firebase Crashlytics", §10 "crash-free
 * sessions ≥ 99.7%"). **Privacy rule §2.2: reports never contain message content,
 * phone numbers, or contact names** — only stack traces + build/device metadata.
 *
 * [LocalCrashReporter] writes reports to app-private storage and chains the prior
 * handler. A `FirebaseCrashlyticsReporter` drops in via one DI line once a
 * Firebase project / google-services.json is configured (not wired yet).
 */
interface CrashReporter {
    /** Installs the uncaught-exception handler. Call once, early, from Application. */
    fun install()

    /** Records a handled (non-fatal) error for later inspection. */
    fun recordNonFatal(throwable: Throwable)

    /** Breadcrumb-style log (no PII) attached to the next report. */
    fun log(message: String)
}

@Singleton
class LocalCrashReporter @Inject constructor(
    @ApplicationContext private val context: Context,
) : CrashReporter {

    private val dir: File by lazy { File(context.filesDir, "crashes").apply { mkdirs() } }
    private val breadcrumbs = ArrayDeque<String>()

    override fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write("crash", thread.name, throwable) }
            previous?.uncaughtException(thread, throwable) // keep the system's dialog/restart
        }
    }

    override fun recordNonFatal(throwable: Throwable) {
        runCatching { write("nonfatal", Thread.currentThread().name, throwable) }
    }

    override fun log(message: String) {
        synchronized(breadcrumbs) {
            breadcrumbs.addLast(message)
            while (breadcrumbs.size > MAX_BREADCRUMBS) breadcrumbs.removeFirst()
        }
    }

    private fun write(kind: String, threadName: String, throwable: Throwable) {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
        val crumbs = synchronized(breadcrumbs) { breadcrumbs.toList() }
        File(dir, "$kind-$stamp.txt").writeText(
            buildString {
                appendLine("kind=$kind")
                appendLine("time=$stamp")
                appendLine("thread=$threadName")
                appendLine("app=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}, API ${Build.VERSION.SDK_INT}")
                if (crumbs.isNotEmpty()) {
                    appendLine("breadcrumbs:")
                    crumbs.forEach { appendLine("  - $it") }
                }
                appendLine()
                append(throwable.stackTraceToString())
            },
        )
        // Bound storage: keep only the most recent reports.
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_REPORTS)
            ?.forEach { it.delete() }
    }

    private companion object {
        const val MAX_BREADCRUMBS = 30
        const val MAX_REPORTS = 20
    }
}
