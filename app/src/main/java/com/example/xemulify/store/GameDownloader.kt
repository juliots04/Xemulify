package com.example.xemulify.store

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.xemulify.core.CoreManager
import com.example.xemulify.library.GameRepository
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Gestor de descargas de juegos de PlayStation en segundo plano.
 * Soporta seguimiento de redirecciones HTTP (Archive.org), informe de progreso
 * en tiempo real a la interfaz gráfica y cancelación limpia.
 */
object GameDownloader {

    private const val TAG = "GameDownloader"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val downloadExecutor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "Xemulify-Downloader").apply { isDaemon = true }
    }

    enum class Status {
        NOT_INSTALLED,
        DOWNLOADING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    data class DownloadState(
        val gameId: String,
        val status: Status,
        val progressPercent: Int = 0,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L,
        val errorMessage: String? = null
    )

    interface Listener {
        fun onDownloadProgress(state: DownloadState)
    }

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<Listener>()
    private val activeStates = ConcurrentHashMap<String, DownloadState>()
    private val activeJobs = ConcurrentHashMap<String, DownloadJob>()

    private class DownloadJob(
        val game: StoreGame,
        @Volatile var isCancelled: Boolean = false,
        var connection: HttpURLConnection? = null
    )

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun getState(gameId: String): DownloadState? {
        return activeStates[gameId]
    }

    fun isDownloading(gameId: String): Boolean {
        return activeStates[gameId]?.status == Status.DOWNLOADING
    }

    /**
     * Comprueba si el juego ya fue descargado o importado en la biblioteca local.
     */
    fun isInstalled(context: Context, game: StoreGame): Boolean {
        // 1. Revisar si la carpeta existe directamente
        val sanitizedName = sanitizeFileName(game.title)
        val dir = File(CoreManager.gamesDir(context), sanitizedName)
        if (dir.exists() && dir.isDirectory) {
            val files = dir.listFiles()?.filter { it.isFile }
            if (!files.isNullOrEmpty()) {
                val hasGameFile = files.any { file ->
                    file.extension.lowercase() in setOf("chd", "pbp", "cue", "bin", "iso", "img")
                }
                if (hasGameFile) return true
            }
        }

        // 2. Revisar si existe en la lista de juegos cargados
        val localGames = GameRepository.listGames(context)
        return localGames.any {
            it.title.equals(game.title, ignoreCase = true) ||
            it.title.equals(sanitizedName, ignoreCase = true) ||
            it.title.startsWith(game.title.take(10), ignoreCase = true)
        }
    }

    fun startDownload(context: Context, game: StoreGame) {
        if (isDownloading(game.id)) return

        val job = DownloadJob(game)
        activeJobs[game.id] = job

        val initialState = DownloadState(
            gameId = game.id,
            status = Status.DOWNLOADING,
            progressPercent = 0,
            downloadedBytes = 0,
            totalBytes = game.sizeBytes
        )
        updateState(initialState)

        val appContext = context.applicationContext

        downloadExecutor.execute {
            var tempFile: File? = null
            try {
                val gamesDir = CoreManager.gamesDir(appContext)
                gamesDir.mkdirs()

                tempFile = File(gamesDir, ".dl_${game.id}.tmp")
                if (tempFile.exists()) tempFile.delete()

                val conn = openConnectionWithRedirects(game.downloadUrl, job)
                job.connection = conn

                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    throw IllegalStateException("Servidor respondió con código HTTP $responseCode")
                }

                val totalLength = conn.contentLengthLong.takeIf { it > 0 } ?: game.sizeBytes

                var downloaded = 0L
                var lastPostTime = 0L

                conn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            if (job.isCancelled) {
                                tempFile.delete()
                                updateState(DownloadState(game.id, Status.CANCELLED))
                                return@execute
                            }
                            output.write(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            if (now - lastPostTime > 150L || downloaded == totalLength) {
                                lastPostTime = now
                                val percent = if (totalLength > 0) ((downloaded * 100) / totalLength).toInt().coerceIn(0, 99) else 0
                                updateState(
                                    DownloadState(
                                        gameId = game.id,
                                        status = Status.DOWNLOADING,
                                        progressPercent = percent,
                                        downloadedBytes = downloaded,
                                        totalBytes = totalLength
                                    )
                                )
                            }
                        }
                    }
                }

                if (job.isCancelled) {
                    tempFile.delete()
                    updateState(DownloadState(game.id, Status.CANCELLED))
                    return@execute
                }

                // Mover a la carpeta definitiva del juego
                val targetDir = File(gamesDir, sanitizeFileName(game.title))
                targetDir.mkdirs()

                val finalFile = File(targetDir, game.targetFileName)
                if (finalFile.exists()) finalFile.delete()
                tempFile.renameTo(finalFile)

                // Descargar carátula local para que la biblioteca la muestre
                downloadCoverArt(game.coverUrl, File(targetDir, "cover.png"))

                Log.i(TAG, "Descarga completada: ${game.title} en ${finalFile.absolutePath}")
                updateState(
                    DownloadState(
                        gameId = game.id,
                        status = Status.COMPLETED,
                        progressPercent = 100,
                        downloadedBytes = downloaded,
                        totalBytes = downloaded
                    )
                )

            } catch (e: Exception) {
                if (!job.isCancelled) {
                    Log.e(TAG, "Error descargando ${game.title}", e)
                    tempFile?.delete()
                    updateState(
                        DownloadState(
                            gameId = game.id,
                            status = Status.FAILED,
                            errorMessage = e.message ?: "Error de conexión"
                        )
                    )
                }
            } finally {
                activeJobs.remove(game.id)
            }
        }
    }

    fun cancelDownload(gameId: String) {
        val job = activeJobs[gameId] ?: return
        job.isCancelled = true
        try {
            job.connection?.disconnect()
        } catch (_: Exception) {}
        updateState(DownloadState(gameId, Status.CANCELLED))
        activeJobs.remove(gameId)
    }

    private fun updateState(state: DownloadState) {
        activeStates[state.gameId] = state
        mainHandler.post {
            for (listener in listeners) {
                try {
                    listener.onDownloadProgress(state)
                } catch (_: Exception) {}
            }
        }
    }

    private fun openConnectionWithRedirects(initialUrl: String, job: DownloadJob): HttpURLConnection {
        var currentUrl = initialUrl
        var redirects = 0

        while (redirects < 6) {
            if (job.isCancelled) throw InterruptedException("Cancelado")
            val url = URL(currentUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
            }

            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 || code == 308
            ) {
                val location = connection.getHeaderField("Location")
                    ?: throw IllegalStateException("Redirección HTTP $code sin cabecera Location")
                currentUrl = if (location.startsWith("http")) location else URL(url, location).toString()
                connection.disconnect()
                redirects++
            } else {
                return connection
            }
        }
        throw IllegalStateException("Demasiadas redirecciones HTTP")
    }

    private fun downloadCoverArt(coverUrl: String, destFile: File) {
        if (destFile.exists()) return
        try {
            val url = URL(coverUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 12000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            if (conn.responseCode == 200) {
                conn.inputStream.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            conn.disconnect()
        } catch (_: Exception) {}
    }

    fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._ -]"), "_").trim()
    }
}
