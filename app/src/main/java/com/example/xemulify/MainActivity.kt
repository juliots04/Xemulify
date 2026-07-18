package com.example.xemulify

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.xemulify.core.BiosImporter
import com.example.xemulify.core.CoreManager
import com.example.xemulify.core.GameImporter
import com.example.xemulify.emulator.GameActivity
import com.example.xemulify.library.Game
import com.example.xemulify.library.GameRepository
import com.example.xemulify.library.LibraryItem
import com.example.xemulify.settings.GeneralSettingsActivity
import com.example.xemulify.settings.KeyConfigActivity
import java.util.concurrent.Executors

/** Pantalla principal: home totalmente personalizada (rail de navegación + rejilla). */
class MainActivity : FragmentActivity() {

    private enum class Section { GAMES, SETTINGS }

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private lateinit var navGames: TextView
    private lateinit var navSettings: TextView
    private lateinit var sectionLabel: TextView
    private lateinit var statusText: TextView
    private lateinit var grid: RecyclerView
    private val adapter = HomeAdapter()

    private var section = Section.GAMES

    private val pickGamesLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (!uris.isNullOrEmpty()) importGames(uris)
        }

    private val pickBiosLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importBios(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        navGames = findViewById(R.id.nav_games)
        navSettings = findViewById(R.id.nav_settings)
        sectionLabel = findViewById(R.id.txt_section)
        statusText = findViewById(R.id.txt_status)
        grid = findViewById(R.id.grid)

        grid.layoutManager = GridLayoutManager(this, 4)
        grid.adapter = adapter
        adapter.onClick = { item ->
            when (item) {
                is LibraryItem.GameItem -> launchGame(item.game)
                is LibraryItem.ActionItem -> onAction(item.id)
            }
        }

        setupNav(navGames, Section.GAMES)
        setupNav(navSettings, Section.SETTINGS)

        select(Section.GAMES)
        navGames.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        reload()
        updateStatus()
    }

    /** ATRÁS en Ajustes vuelve a Juegos (página principal); solo sale desde Juegos. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (section == Section.SETTINGS) {
            select(Section.GAMES)
            navGames.requestFocus()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    // ---- Navegación ----------------------------------------------------------

    private fun setupNav(view: TextView, target: Section) {
        view.nextFocusRightId = R.id.grid
        view.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && section != target) select(target)
            styleNav(view, view.isActivated, hasFocus)
        }
        view.setOnClickListener {
            select(target)
            grid.getChildAt(0)?.requestFocus()
        }
    }

    private fun select(target: Section) {
        section = target
        navGames.isActivated = target == Section.GAMES
        navSettings.isActivated = target == Section.SETTINGS
        styleNav(navGames, navGames.isActivated, navGames.hasFocus())
        styleNav(navSettings, navSettings.isActivated, navSettings.hasFocus())
        reload()
    }

    private fun styleNav(view: TextView, activated: Boolean, focused: Boolean) {
        val color = when {
            focused -> 0xFFFFFFFF.toInt()
            activated -> 0xFF9B7BFF.toInt()
            else -> 0xFF9A9AA5.toInt()
        }
        view.setTextColor(color)
    }

    // ---- Contenido -----------------------------------------------------------

    private fun reload() {
        val items = when (section) {
            Section.GAMES -> buildGames()
            Section.SETTINGS -> buildSettings()
        }
        sectionLabel.text = when (section) {
            Section.GAMES -> getString(R.string.section_games_label)
            Section.SETTINGS -> getString(R.string.section_settings_label)
        }
        adapter.submit(items)
    }

    private fun buildGames(): List<LibraryItem> {
        val list = ArrayList<LibraryItem>()
        GameRepository.listGames(this).forEach { list.add(LibraryItem.GameItem(it)) }
        list.add(
            LibraryItem.ActionItem(
                LibraryItem.ActionItem.ADD,
                getString(R.string.action_add_game),
                getString(R.string.action_add_game_sub),
                R.drawable.ic_action_add,
            )
        )
        return list
    }

    private fun buildSettings(): List<LibraryItem> = listOf(
        LibraryItem.ActionItem(
            LibraryItem.ActionItem.KEYS,
            getString(R.string.action_keys),
            getString(R.string.action_keys_sub),
            R.drawable.ic_action_keys,
        ),
        LibraryItem.ActionItem(
            LibraryItem.ActionItem.GENERAL,
            getString(R.string.action_general),
            getString(R.string.action_general_sub),
            R.drawable.ic_action_general,
        ),
        LibraryItem.ActionItem(
            LibraryItem.ActionItem.BIOS,
            getString(R.string.action_bios),
            if (CoreManager.hasBios(this)) getString(R.string.status_bios_ok)
            else getString(R.string.status_bios_missing),
            R.drawable.ic_action_bios,
        ),
        LibraryItem.ActionItem(
            LibraryItem.ActionItem.HELP,
            getString(R.string.action_help),
            if (CoreManager.isCoreAvailable(this)) getString(R.string.status_core_ok)
            else getString(R.string.status_core_missing),
            R.drawable.ic_action_help,
        ),
    )

    private fun updateStatus() {
        val core = if (CoreManager.isCoreAvailable(this)) "Core ✓" else "Core ✗"
        val bios = if (CoreManager.hasBios(this)) "BIOS ✓" else "BIOS ✗"
        statusText.text = "$core   ·   $bios"
    }

    // ---- Acciones ------------------------------------------------------------

    private fun onAction(id: String) {
        when (id) {
            LibraryItem.ActionItem.ADD -> pickGamesLauncher.launch(arrayOf("*/*"))
            LibraryItem.ActionItem.KEYS -> startActivity(Intent(this, KeyConfigActivity::class.java))
            LibraryItem.ActionItem.GENERAL -> startActivity(Intent(this, GeneralSettingsActivity::class.java))
            LibraryItem.ActionItem.BIOS -> pickBiosLauncher.launch(arrayOf("*/*"))
            LibraryItem.ActionItem.HELP -> showHelp()
        }
    }

    private fun launchGame(game: Game) {
        if (!CoreManager.isCoreAvailable(this)) {
            Toast.makeText(this, R.string.error_core_missing, Toast.LENGTH_LONG).show()
            showHelp()
            return
        }
        if (!CoreManager.hasBios(this)) {
            Toast.makeText(this, R.string.warn_no_bios, Toast.LENGTH_LONG).show()
        }
        GameActivity.start(this, game.file.absolutePath, game.id)
    }

    private fun importGames(uris: List<Uri>) {
        val context = applicationContext
        Toast.makeText(context, R.string.importing, Toast.LENGTH_SHORT).show()
        io.execute {
            try {
                val imported = GameImporter.import(context, uris)
                main.post {
                    Toast.makeText(context, getString(R.string.imported_ok, imported.name), Toast.LENGTH_LONG).show()
                    reload()
                }
            } catch (e: Exception) {
                main.post {
                    Toast.makeText(context, getString(R.string.import_error, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun importBios(uri: Uri) {
        val context = applicationContext
        io.execute {
            try {
                val file = BiosImporter.import(context, uri)
                main.post {
                    Toast.makeText(context, getString(R.string.bios_imported, file.name), Toast.LENGTH_LONG).show()
                    reload()
                    updateStatus()
                }
            } catch (e: Exception) {
                main.post {
                    Toast.makeText(context, getString(R.string.import_error, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showHelp() {
        val coreOk = CoreManager.isCoreAvailable(this)
        val biosOk = CoreManager.hasBios(this)
        val message = getString(R.string.help_body, if (coreOk) "✓" else "✗", if (biosOk) "✓" else "✗")
        AlertDialog.Builder(this, R.style.Theme_Xemulify_Dialog)
            .setTitle(R.string.help_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ---- Adapter -------------------------------------------------------------

    private class HomeAdapter : RecyclerView.Adapter<HomeAdapter.CardVH>() {

        private val items = ArrayList<LibraryItem>()
        var onClick: ((LibraryItem) -> Unit)? = null

        fun submit(newItems: List<LibraryItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardVH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_home_card, parent, false)
            return CardVH(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: CardVH, position: Int) {
            val item = items[position]
            val context = holder.itemView.context
            when (item) {
                is LibraryItem.GameItem -> {
                    holder.title.text = item.game.title
                    holder.subtitle.text = context.getString(R.string.card_playstation)
                    holder.icon.setImageResource(R.drawable.ic_game_disc)
                }
                is LibraryItem.ActionItem -> {
                    holder.title.text = item.title
                    holder.subtitle.text = item.subtitle
                    holder.icon.setImageResource(item.iconRes)
                }
            }
            holder.itemView.setOnClickListener { onClick?.invoke(item) }
            holder.itemView.setOnFocusChangeListener { v, hasFocus ->
                val scale = if (hasFocus) 1.05f else 1f
                v.animate().scaleX(scale).scaleY(scale).setDuration(140).start()
            }
        }

        class CardVH(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.img_thumbnail)
            val title: TextView = view.findViewById(R.id.txt_title)
            val subtitle: TextView = view.findViewById(R.id.txt_subtitle)
        }
    }
}
