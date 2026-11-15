package com.example.xemulify.controller

import android.graphics.Color

enum class ControllerTheme(
    val displayName: String,
    val shortName: String,
    val bodyBgColor: Int,
    val panelInsetBg: Int,
    val podStrokeColor: Int,
    val wellBgColor: Int,
    val wellStrokeColor: Int,
    val dpadColor: Int,
    val dpadPressedColor: Int,
    val buttonBezelColor: Int,
    val buttonFaceColor: Int,
    val buttonPressedColor: Int,
    val crossColor: Int,
    val circleColor: Int,
    val squareColor: Int,
    val triangleColor: Int,
    val shoulderColor: Int,
    val centerPillColor: Int,
    val ledOnColor: Int,
    val textColor: Int
) {
    // 1. PS1 Clásico Gris Oscuro (100% Gris Carbón Neutro PSX, cero azulados)
    CLASSIC_DARK_GRAY(
        displayName = "PS1 Clásico Gris Oscuro (Predeterminado)",
        shortName = "PS1 Gris Oscuro",
        bodyBgColor = Color.parseColor("#262626"),       // Chasis gris oscuro neutro
        panelInsetBg = Color.parseColor("#303030"),      // Pods ergonómicos gris neutro
        podStrokeColor = Color.parseColor("#424242"),    // Relieve del pod
        wellBgColor = Color.parseColor("#1C1C1C"),       // Pozo cóncavo de botones y cruceta (cero azul)
        wellStrokeColor = Color.parseColor("#383838"),   // Anillo de pozo
        dpadColor = Color.parseColor("#1F1F1F"),         // Cruceta carbón oscuro
        dpadPressedColor = Color.parseColor("#3D3D3D"),
        buttonBezelColor = Color.parseColor("#383838"),  // Biseles de botones
        buttonFaceColor = Color.parseColor("#222222"),   // Superficie táctil
        buttonPressedColor = Color.parseColor("#3D3D3D"),
        crossColor = Color.parseColor("#3A7AFE"),        // Símbolos PlayStation auténticos
        circleColor = Color.parseColor("#FF3B4E"),
        squareColor = Color.parseColor("#F74B9E"),
        triangleColor = Color.parseColor("#00E676"),
        shoulderColor = Color.parseColor("#222222"),     // L1, L2, R1, R2
        centerPillColor = Color.parseColor("#1F1F1F"),   // SELECT, START, ANALOG
        ledOnColor = Color.parseColor("#FF1744"),
        textColor = Color.parseColor("#EEEEEE")
    ),

    // 2. Negro Grafito Mate (Negro carbón neutro con profundidad)
    STEALTH_BLACK(
        displayName = "Negro Grafito Mate",
        shortName = "Negro Grafito",
        bodyBgColor = Color.parseColor("#0E0E0E"),       // Negro carbón profundo
        panelInsetBg = Color.parseColor("#161616"),
        podStrokeColor = Color.parseColor("#242424"),
        wellBgColor = Color.parseColor("#0A0A0A"),
        wellStrokeColor = Color.parseColor("#1E1E1E"),
        dpadColor = Color.parseColor("#161616"),
        dpadPressedColor = Color.parseColor("#2C2C2C"),
        buttonBezelColor = Color.parseColor("#282828"),
        buttonFaceColor = Color.parseColor("#181818"),
        buttonPressedColor = Color.parseColor("#2E2E2E"),
        crossColor = Color.parseColor("#3A7AFE"),
        circleColor = Color.parseColor("#FF3B4E"),
        squareColor = Color.parseColor("#F74B9E"),
        triangleColor = Color.parseColor("#00E676"),
        shoulderColor = Color.parseColor("#181818"),
        centerPillColor = Color.parseColor("#141414"),
        ledOnColor = Color.parseColor("#FF1744"),
        textColor = Color.parseColor("#E5E5E5")
    ),

    // 3. Gris Ceniza PlayStation (Tono medio mate, 100% neutro)
    CARBON_GRAY(
        displayName = "Gris Ceniza PlayStation",
        shortName = "Gris Ceniza",
        bodyBgColor = Color.parseColor("#333333"),       // Gris ceniza medio
        panelInsetBg = Color.parseColor("#3D3D3D"),
        podStrokeColor = Color.parseColor("#4F4F4F"),
        wellBgColor = Color.parseColor("#242424"),
        wellStrokeColor = Color.parseColor("#444444"),
        dpadColor = Color.parseColor("#222222"),
        dpadPressedColor = Color.parseColor("#454545"),
        buttonBezelColor = Color.parseColor("#484848"),
        buttonFaceColor = Color.parseColor("#282828"),
        buttonPressedColor = Color.parseColor("#484848"),
        crossColor = Color.parseColor("#3A7AFE"),
        circleColor = Color.parseColor("#FF3B4E"),
        squareColor = Color.parseColor("#F74B9E"),
        triangleColor = Color.parseColor("#00E676"),
        shoulderColor = Color.parseColor("#282828"),
        centerPillColor = Color.parseColor("#222222"),
        ledOnColor = Color.parseColor("#FF1744"),
        textColor = Color.parseColor("#F5F5F5")
    );

    companion object {
        fun safeValueOf(name: String?): ControllerTheme {
            if (name == null) return CLASSIC_DARK_GRAY
            return when (name) {
                "CLASSIC_DARK_GRAY", "PS1_CLASSIC" -> CLASSIC_DARK_GRAY
                "STEALTH_BLACK" -> STEALTH_BLACK
                "CARBON_GRAY", "CYBER_MIDNIGHT" -> CARBON_GRAY
                else -> try {
                    valueOf(name)
                } catch (_: Exception) {
                    CLASSIC_DARK_GRAY
                }
            }
        }
    }
}
