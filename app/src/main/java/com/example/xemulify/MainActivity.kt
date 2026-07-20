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
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.xemulify.core.BiosImporter
import com.example.xemulify.core.CoreManager
import com.example.xemulify.core.GameImporter
import com.example.xemulify.emulator.GameActivity
import com.example.xemulify.library.AppShortcut
import com.example.xemulify.library.Game
import com.example.xemulify.library.GameRepository
import com.example.xemulify.library.LibraryItem
import com.example.xemulify.library.ShortcutStore
import com.example.xemulify.settings.ControlsActivity
import com.example.xemulify.settings.GeneralSettingsActivity
import java.util.concurrent.Executors

/**
 * Home: dos secciones apiladas (Biblioteca y Ajustes). La sección enfocada se muestra brillante
 * y la otra atenuada ("fantasma"); al bajar con el mando el foco pasa a la otra sección y el
 * atenuado se invierte (efecto tipo Android TV / Google TV).
 */
class MainActivity : FragmentActivity() {

    private enum class Section { GAMES, SETTINGS }

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private lateinit var navGames: TextView
    private lateinit var navSettings: TextView
    private lateinit var statusText: TextView
    private lateinit var scroll: NestedScrollView
    private lateinit var secGames: View
    private lateinit var secSettings: View
    private lateinit var gridGames: RecyclerView
    private lateinit var gridSettings: RecyclerView

    private val gamesAdapter = HomeAdapter()
    private val settingsAdapter = HomeAdapter()

    private var activeSection = Section.GAMES
    private var firstLoad = true

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
        statusText = findViewById(R.id.txt_status)
        scroll = findViewById(R.id.scroll)
        secGames = findViewById(R.id.sec_games)
        secSettings = findViewById(R.id.sec_settings)
        gridGames = findViewById(R.id.grid_games)
        gridSettings = findViewById(R.id.grid_settings)

        gridGames.layoutManager = GridLayoutManager(this, 4)
        gridSettings.layoutManager = GridLayoutManager(this, 4)
        gridGames.adapter = gamesAdapter
        gridSettings.adapter = settingsAdapter
        gridGames.isNestedScrollingEnabled = false
        gridSettings.isNestedScrollingEnabled = false

        val onClick: (LibraryItem) -> Unit = { item ->
            when (item) {
                is LibraryItem.GameItem -> launchGame(item.game)
                is LibraryItem.ShortcutItem -> launchApp(item.shortcut)
                is LibraryItem.ActionItem -> onAction(item.id)
            }
        }
        gamesAdapter.onClick = onClick
        settingsAdapter.onClick = onClick
        gamesAdapter.onFocus = { setActiveSection(Section.GAMES) }
        settingsAdapter.onFocus = { setActiveSection(Section.SETTINGS) }
        // OK mantenido sobre un acceso directo: ofrecer quitarlo.
        gamesAdapter.onLongClick = { item ->
            if (item is LibraryItem.ShortcutItem) { confirmRemoveShortcut(item.shortcut); true } else false
        }

        setupNav(navGames, Section.GAMES)
        setupNav(navSettings, Section.SETTINGS)

        // Estado inicial: Biblioteca activa, Ajustes fantasma.
        secGames.alpha = 1f
        secSettings.alpha = GHOST
        navGames.isActivated = true
        styleNav(navGames, true, false)
        styleNav(navSettings, false, false)
    }

    override fun onResume() {
        super.onResume()
        reloadAll()
        updateStatus()
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (activeSection == Section.SETTINGS) {
            setActiveSection(Section.GAMES)
            gridGames.post { gridGames.getChildAt(0)?.requestFocus() }
        } else {
            super.onBackPressed()
        }
    }

    // ---- Secciones / efecto fantasma ----------------------------------------

    private fun setActiveSection(s: Section) {
        if (activeSection == s) return
        activeSection = s
        secGames.animate().alpha(if (s == Section.GAMES) 1f else GHOST).setDuration(180).start()
        secSettings.animate().alpha(if (s == Section.SETTINGS) 1f else GHOST).setDuration(180).start()
        navGames.isActivated = s == Section.GAMES
        navSettings.isActivated = s == Section.SETTINGS
        styleNav(navGames, navGames.isActivated, navGames.hasFocus())
        styleNav(navSettings, navSettings.isActivated, navSettings.hasFocus())
        // Scroll suave que engancha la sección activa arriba (evita el salto corto del auto-scroll).
        val targetY = if (s == Section.GAMES) 0 else secSettings.top
        scroll.post { scroll.smoothScrollTo(0, targetY) }
    }

    private fun setupNav(view: TextView, target: Section) {
        view.setOnFocusChangeListener { _, hasFocus -> styleNav(view, view.isActivated, hasFocus) }
        view.setOnClickListener {
            setActiveSection(target)
            val grid = if (target == Section.GAMES) gridGames else gridSettings
            grid.post { grid.getChildAt(0)?.requestFocus() }
        }
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

    private fun reloadAll() {
        submit(gridGames, gamesAdapter, buildGames())
        submit(gridSettings, settingsAdapter, buildSettings())
        if (firstLoad) {
            firstLoad = false
            gridGames.post { gridGames.getChildAt(0)?.requestFocus() }
        }
    }

    /** Diferir el submit evita el crash "RecyclerView is computing a layout" al volver de otra pantalla. */
    private fun submit(grid: RecyclerView, adapter: HomeAdapter, items: List<LibraryItem>) {
        if (grid.isComputingLayout) grid.post { adapter.submit(items) } else adapter.submit(items)
    }

    /** Biblioteca: juegos de PS1 + accesos directos a apps + acciones para añadir ambos. */
    private fun buildGames(): List<LibraryItem> {
        val list = ArrayList<LibraryItem>()
        GameRepository.listGames(this).forEach { list.add(LibraryItem.GameItem(it)) }
        ShortcutStore(this).getShortcuts().forEach { list.add(LibraryItem.ShortcutItem(it)) }
        list.add(
            LibraryItem.ActionItem(
                LibraryItem.ActionItem.ADD,
                getString(R.string.action_add_game),
                getString(R.string.action_add_game_sub),
                R.drawable.ic_action_add,
            )
        )
        list.add(
            LibraryItem.ActionItem(
                LibraryItem.ActionItem.ADD_APP,
                getString(R.string.action_add_app),
                getString(R.string.action_add_app_sub),
                R.drawable.ic_action_add,
            )
        )
        return list
    }

    private fun buildSettings(): List<LibraryItem> = listOf(
        LibraryItem.ActionItem(
            LibraryItem.ActionItem.CONTROLS,
            getString(R.string.action_controls),
            getString(R.string.action_controls_sub),
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
            LibraryItem.ActionItem.ADD_APP -> showAppPicker()
            LibraryItem.ActionItem.CONTROLS -> startActivity(Intent(this, ControlsActivity::class.java))
            LibraryItem.ActionItem.GENERAL -> startActivity(Intent(this, GeneralSettingsActivity::class.java))
            LibraryItem.ActionItem.BIOS -> pickBiosLauncher.launch(arrayOf("*/*"))
            LibraryItem.ActionItem.HELP -> showHelp()
        }
    }

    private fun showAppPicker() {
        val pm = packageManager
        val seen = HashSet<String>()
        val apps = ArrayList<AppShortcut>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(intent, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName || !seen.add(pkg)) continue
                apps.add(AppShortcut(pkg, ri.loadLabel(pm).toString()))
            }
        }
        apps.sortBy { it.label.lowercase() }

        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.apps_none, Toast.LENGTH_LONG).show()
            return
        }
        val labels = apps.map { it.label }.toTypedArray()
        AlertDialog.Builder(this, R.style.Theme_Xemulify_Dialog)
            .setTitle(R.string.action_add_app)
            .setItems(labels) { _, which ->
                ShortcutStore(this).add(apps[which])
                submit(gridGames, gamesAdapter, buildGames())
            }
            .show()
    }

    /** Confirma y quita un acceso directo de la biblioteca (OK mantenido sobre la tarjeta). */
    private fun confirmRemoveShortcut(shortcut: AppShortcut) {
        AlertDialog.Builder(this, R.style.Theme_Xemulify_Dialog)
            .setTitle(R.string.shortcut_remove_title)
            .setMessage(getString(R.string.shortcut_remove_msg, shortcut.label))
            .setPositiveButton(R.string.shortcut_remove) { _, _ ->
                ShortcutStore(this).remove(shortcut.packageName)
                submit(gridGames, gamesAdapter, buildGames())
            }
            .setNegativeButton(R.string.cheat_cancel, null)
            .show()
    }

    private fun launchApp(shortcut: AppShortcut) {
        val pm = packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(shortcut.packageName)
            ?: pm.getLaunchIntentForPackage(shortcut.packageName)
        if (intent == null) {
            Toast.makeText(this, getString(R.string.app_not_found, shortcut.label), Toast.LENGTH_LONG).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
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
                    submit(gridGames, gamesAdapter, buildGames())
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
                    submit(gridSettings, settingsAdapter, buildSettings())
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
        var onFocus: (() -> Unit)? = null
        var onLongClick: ((LibraryItem) -> Boolean)? = null

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
                is LibraryItem.ShortcutItem -> {
                    holder.title.text = item.shortcut.label
                    holder.subtitle.text = context.getString(R.string.card_app)
                    val icon = runCatching {
                        context.packageManager.getApplicationIcon(item.shortcut.packageName)
                    }.getOrNull()
                    if (icon != null) holder.icon.setImageDrawable(icon)
                    else holder.icon.setImageResource(R.drawable.ic_action_play)
                }
                is LibraryItem.ActionItem -> {
                    holder.title.text = item.title
                    holder.subtitle.text = item.subtitle
                    holder.icon.setImageResource(item.iconRes)
                }
            }
            holder.itemView.setOnClickListener { onClick?.invoke(item) }
            holder.itemView.setOnLongClickListener { onLongClick?.invoke(item) ?: false }
            holder.itemView.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) onFocus?.invoke()
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

    companion object {
        private const val GHOST = 0.32f
    }
}
