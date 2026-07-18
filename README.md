# Xemulify

Emulador de PlayStation 1 para Android, orientado a TV boxes / Android TV (interfaz Leanback), que embebe el core libretro **PCSX-ReARMed** mediante [LibretroDroid](https://github.com/Swordfish90/LibretroDroid).

## Características

- Importación de BIOS de PS1 (`scph*.bin`) vía selector de archivos del sistema.
- Importación de juegos en múltiples formatos: `.cue`+`.bin`, `.m3u`, `.pbp`, `.chd`, `.iso`, o `.bin` suelto (genera el `.cue` automáticamente).
- Biblioteca de juegos con carátulas.
- Mapeo de botones configurable para mandos físicos.
- Estados guardados (save states) y tarjetas de memoria por juego.

## Requisitos

- Android Studio con JDK 11+.
- SDK mínimo: API 21 (Android 5.0) — SDK objetivo: API 36.
- El core nativo `libpcsx_rearmed_libretro.so` en `app/src/main/jniLibs/<abi>/` para las ABIs `arm64-v8a`, `armeabi-v7a` y `x86_64`.
- Un archivo BIOS de PS1 obtenido legalmente (no se incluye en el repositorio).

## Compilar

```bash
./gradlew assembleDebug
```

## Estructura del proyecto

- `core/` — gestión del core libretro e importación de BIOS/juegos (`CoreManager`, `BiosImporter`, `GameImporter`).
- `emulator/` — actividad de emulación (`GameActivity`).
- `input/` — mapeo y traducción de botones del mando (`PsxInputTranslator`, `KeyMappingStore`).
- `library/` — modelo y repositorio de la biblioteca de juegos.
- `settings/` — pantallas de configuración general y de mando.
