package com.example.xemulify.mode

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import androidx.fragment.app.FragmentActivity
import com.example.xemulify.MainActivity
import com.example.xemulify.R
import com.example.xemulify.controller.PairingActivity

class ModeSelectActivity : FragmentActivity() {

    private lateinit var modeStore: ModeStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        modeStore = ModeStore(this)

        // Si ya hay un modo guardado y el usuario marcó recordar elección:
        if (modeStore.rememberChoice && intent.getBooleanExtra(EXTRA_FORCE_SELECTION, false).not()) {
            when (modeStore.selectedMode) {
                ModeStore.AppMode.HOST_TV -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    return
                }
                ModeStore.AppMode.CONTROLLER_PHONE -> {
                    startActivity(Intent(this, PairingActivity::class.java))
                    finish()
                    return
                }
                else -> Unit
            }
        }

        setContentView(R.layout.activity_mode_select)

        val cardHost = findViewById<View>(R.id.card_mode_host)
        val cardController = findViewById<View>(R.id.card_mode_controller)
        val chkRemember = findViewById<CheckBox>(R.id.chk_remember)

        chkRemember.isChecked = modeStore.rememberChoice

        cardHost.setOnClickListener {
            modeStore.rememberChoice = chkRemember.isChecked
            modeStore.selectedMode = ModeStore.AppMode.HOST_TV
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        cardController.setOnClickListener {
            modeStore.rememberChoice = chkRemember.isChecked
            modeStore.selectedMode = ModeStore.AppMode.CONTROLLER_PHONE
            startActivity(Intent(this, PairingActivity::class.java))
            finish()
        }

        // Foco por defecto en TV
        cardHost.requestFocus()
    }

    companion object {
        const val EXTRA_FORCE_SELECTION = "force_selection"
    }
}
