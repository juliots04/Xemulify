package com.example.xemulify.store

/**
 * Modelo de datos para un juego de la tienda online de PlayStation.
 */
data class StoreGame(
    val id: String,
    val title: String,
    val genre: String,
    val year: String,
    val developer: String,
    val sizeText: String,
    val sizeBytes: Long,
    val coverUrl: String,
    val description: String,
    val downloadUrl: String,
    val targetFileName: String
)
