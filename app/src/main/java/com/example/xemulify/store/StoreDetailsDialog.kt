package com.example.xemulify.store

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.example.xemulify.R

/**
 * Diálogo modal para TV y pantalla táctil que muestra los detalles de un juego
 * de la tienda online (carátula, sinopsis, desarrollador, tamaño) y gestiona
 * su descarga con progreso en tiempo real y lanzamiento inmediato al finalizar.
 */
class StoreDetailsDialog(
    private val activity: Activity,
    private val game: StoreGame,
    private val onPlayRequested: (StoreGame) -> Unit,
    private val onGameDownloaded: (StoreGame) -> Unit
) {

    private val dialog: Dialog = Dialog(activity, R.style.Theme_Xemulify_FullscreenDialog)
    val innerDialog: Dialog get() = dialog
    var onDismiss: (() -> Unit)? = null
    private lateinit var imgCover: ImageView
    private lateinit var txtTitle: TextView
    private lateinit var txtGenre: TextView
    private lateinit var txtDescription: TextView
    private lateinit var txtStatSize: TextView
    private lateinit var txtStatYear: TextView
    private lateinit var txtStatDeveloper: TextView

    private lateinit var layoutProgress: LinearLayout
    private lateinit var pbProgress: ProgressBar
    private lateinit var txtProgressPct: TextView
    private lateinit var txtProgressBytes: TextView

    private lateinit var btnAction: Button
    private lateinit var btnCancel: Button
    private lateinit var btnClose: View

    private val downloadListener = object : GameDownloader.Listener {
        override fun onDownloadProgress(state: GameDownloader.DownloadState) {
            if (state.gameId == game.id) {
                activity.runOnUiThread { updateUIState() }
            }
        }
    }

    init {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_store_game_details, null)
        dialog.setContentView(view)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(0xFF13141C.toInt()))
            clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = 0xFF13141C.toInt()
            navigationBarColor = 0xFF13141C.toInt()
        }

        bindViews(view)
        setupContent()
        updateUIState()

        GameDownloader.addListener(downloadListener)
        dialog.setOnDismissListener {
            GameDownloader.removeListener(downloadListener)
            onDismiss?.invoke()
        }
    }

    private fun bindViews(root: View) {
        imgCover = root.findViewById(R.id.img_dialog_cover)
        txtTitle = root.findViewById(R.id.txt_dialog_title)
        txtGenre = root.findViewById(R.id.txt_dialog_genre)
        txtDescription = root.findViewById(R.id.txt_dialog_description)
        txtStatSize = root.findViewById(R.id.txt_dialog_stat_size)
        txtStatYear = root.findViewById(R.id.txt_dialog_stat_year)
        txtStatDeveloper = root.findViewById(R.id.txt_dialog_stat_developer)

        layoutProgress = root.findViewById(R.id.layout_dialog_progress)
        pbProgress = root.findViewById(R.id.pb_dialog_progress)
        txtProgressPct = root.findViewById(R.id.txt_dialog_progress_pct)
        txtProgressBytes = root.findViewById(R.id.txt_dialog_progress_bytes)

        btnAction = root.findViewById(R.id.btn_dialog_action)
        btnCancel = root.findViewById(R.id.btn_dialog_cancel)
        btnClose = root.findViewById(R.id.btn_dialog_close)

        applyFocusEffect(btnAction)
        applyFocusEffect(btnCancel)
        applyFocusEffect(btnClose)

        btnClose.setOnClickListener { dialog.dismiss() }

        btnCancel.setOnClickListener {
            GameDownloader.cancelDownload(game.id)
            updateUIState()
        }
    }

    private fun applyFocusEffect(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.06f else 1.0f
            v.animate()
                .scaleX(scale)
                .scaleY(scale)
                .translationZ(if (hasFocus) 10f else 0f)
                .setDuration(120)
                .start()
        }
    }

    private fun setupContent() {
        txtTitle.text = game.title
        txtGenre.text = game.genre
        txtDescription.text = game.description
        txtStatSize.text = "Tamaño: ${game.sizeText}"
        txtStatYear.text = "Lanzamiento: ${game.year}"
        txtStatDeveloper.text = "Desarrollador: ${game.developer}"

        Glide.with(activity)
            .load(game.coverUrl)
            .fitCenter()
            .placeholder(R.drawable.bg_home_card)
            .error(R.drawable.ic_game_disc)
            .into(imgCover)
    }

    private fun updateUIState() {
        val isInstalled = GameDownloader.isInstalled(activity, game)
        val state = GameDownloader.getState(game.id)

        if (isInstalled) {
            layoutProgress.visibility = View.GONE
            btnCancel.visibility = View.GONE
            btnAction.text = "▶ Jugar ahora"
            btnAction.setBackgroundResource(R.drawable.bg_button_rounded_play)
            btnAction.setOnClickListener {
                dialog.dismiss()
                onPlayRequested(game)
            }
        } else if (state?.status == GameDownloader.Status.DOWNLOADING) {
            layoutProgress.visibility = View.VISIBLE
            btnCancel.visibility = View.VISIBLE
            pbProgress.progress = state.progressPercent
            txtProgressPct.text = "${state.progressPercent}%"

            val mbDone = state.downloadedBytes / (1024 * 1024)
            val mbTotal = state.totalBytes / (1024 * 1024)
            txtProgressBytes.text = "$mbDone MB / $mbTotal MB"

            btnAction.text = "Descargando... ${state.progressPercent}%"
            btnAction.setBackgroundResource(R.drawable.bg_button_rounded_downloading)
            btnAction.setOnClickListener(null)
        } else {
            layoutProgress.visibility = View.GONE
            btnCancel.visibility = View.GONE
            btnAction.text = "⬇ Descargar juego (${game.sizeText})"
            btnAction.setBackgroundResource(R.drawable.bg_button_rounded_accent)
            btnAction.setOnClickListener {
                Toast.makeText(activity, "Iniciando descarga de ${game.title}...", Toast.LENGTH_SHORT).show()
                GameDownloader.startDownload(activity, game)
                updateUIState()
            }
        }

        if (state?.status == GameDownloader.Status.COMPLETED) {
            onGameDownloaded(game)
        }
    }

    fun show() {
        dialog.show()
        dialog.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.decorView.setPadding(0, 0, 0, 0)
            window.decorView.setBackgroundColor(0xFF13141C.toInt())
        }
        btnAction.post { btnAction.requestFocus() }
    }
}
