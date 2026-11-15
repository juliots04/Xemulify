package com.example.xemulify.store

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.xemulify.R

/**
 * Adaptador para mostrar las tarjetas del catálogo de la tienda de PlayStation.
 */
class StoreAdapter(
    private var games: List<StoreGame> = emptyList(),
    var onGameClick: ((StoreGame) -> Unit)? = null,
    var onFocus: (() -> Unit)? = null
) : RecyclerView.Adapter<StoreAdapter.StoreVH>() {

    private val downloadListener = object : GameDownloader.Listener {
        override fun onDownloadProgress(state: GameDownloader.DownloadState) {
            val idx = games.indexOfFirst { it.id == state.gameId }
            if (idx != -1) {
                notifyItemChanged(idx)
            }
        }
    }

    init {
        GameDownloader.addListener(downloadListener)
    }

    fun submit(newGames: List<StoreGame>) {
        games = newGames
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = games.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoreVH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_store_card, parent, false)
        return StoreVH(view)
    }

    override fun onBindViewHolder(holder: StoreVH, position: Int) {
        val game = games[position]
        val context = holder.itemView.context

        holder.txtTitle.text = game.title
        holder.txtGenre.text = game.genre
        holder.txtMeta.text = "${game.developer} · ${game.year}"
        holder.txtSize.text = game.sizeText

        Glide.with(context)
            .load(game.coverUrl)
            .fitCenter()
            .placeholder(R.drawable.bg_home_card)
            .error(R.drawable.ic_game_disc)
            .into(holder.imgCover)

        val isInstalled = GameDownloader.isInstalled(context, game)
        val state = GameDownloader.getState(game.id)

        if (isInstalled) {
            holder.txtStatusBadge.visibility = View.VISIBLE
            holder.txtStatusBadge.text = "✓ Instalado"
            holder.txtStatusBadge.setBackgroundResource(R.drawable.bg_store_badge_installed)
            holder.layoutOverlay.visibility = View.GONE
        } else if (state?.status == GameDownloader.Status.DOWNLOADING) {
            holder.txtStatusBadge.visibility = View.GONE
            holder.layoutOverlay.visibility = View.VISIBLE
            holder.pbProgress.progress = state.progressPercent
            holder.txtProgress.text = "${state.progressPercent}%"
        } else {
            holder.txtStatusBadge.visibility = View.GONE
            holder.layoutOverlay.visibility = View.GONE
        }

        // Al pulsar D-pad arriba en la fila superior de la tienda, salta a Vincular nuevo mando
        if (position < 4) {
            holder.itemView.nextFocusUpId = R.id.btn_controllers_hub
        } else {
            holder.itemView.nextFocusUpId = View.NO_ID
        }

        // Al pulsar D-pad izquierda en la primera columna, regresa al botón Tienda del sidebar
        if (position % 4 == 0) {
            holder.itemView.nextFocusLeftId = R.id.nav_store
        } else {
            holder.itemView.nextFocusLeftId = View.NO_ID
        }

        // Al pulsar D-pad izquierda en la primera columna, salta directamente al nav rail
        if (position % 4 == 0) {
            holder.itemView.nextFocusLeftId = R.id.nav_store
        } else {
            holder.itemView.nextFocusLeftId = View.NO_ID
        }

        holder.itemView.setOnClickListener { onGameClick?.invoke(game) }
        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.04f else 1.0f
            v.animate()
                .scaleX(scale)
                .scaleY(scale)
                .translationZ(if (hasFocus) 12f else 0f)
                .setDuration(140)
                .start()
            if (hasFocus) onFocus?.invoke()
        }
    }

    class StoreVH(view: View) : RecyclerView.ViewHolder(view) {
        val imgCover: ImageView = view.findViewById(R.id.img_store_cover)
        val layoutOverlay: FrameLayout = view.findViewById(R.id.layout_download_overlay)
        val pbProgress: ProgressBar = view.findViewById(R.id.pb_card_progress)
        val txtProgress: TextView = view.findViewById(R.id.txt_card_progress)
        val txtGenre: TextView = view.findViewById(R.id.txt_store_genre)
        val txtStatusBadge: TextView = view.findViewById(R.id.txt_store_status_badge)
        val txtTitle: TextView = view.findViewById(R.id.txt_store_title)
        val txtMeta: TextView = view.findViewById(R.id.txt_store_meta)
        val txtSize: TextView = view.findViewById(R.id.txt_store_size)
    }
}
