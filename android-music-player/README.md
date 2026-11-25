# NardiTune

Lettore musicale Android (Kotlin + Jetpack Compose + Media3/ExoPlayer + Room).

- **Tutti i formati**: MP3, AAC/M4A, FLAC, OGG/Opus, WAV, MIDI, AMR... nativi; WMA, APE, WavPack, DTS, TTA, ecc. tramite il decoder FFmpeg.
- **Locandina piccola, pulsanti grandi** (play 128dp, prev/next 88dp, shuffle/-10s/+10s/ripeti 72dp).
- **Database locale (Room/SQLite)** con tracce e albero cartelle precalcolato: la navigazione e' istantanea.
- **Aggiornabile**: scansione incrementale a ogni avvio + pulsante di refresh (aggiunge nuovi/modificati, rimuove i cancellati).
- **Analizzatore di spettro** in tempo reale (FFT a 40 bande) nel player: tocca o trascina per leggere frequenza e livello.
- Riproduzione in background con notifica, lockscreen e cuffie.

## Build
- Android Studio: apri la cartella `android-music-player` e premi Run.
- Da riga di comando (serve Android SDK): `./gradlew assembleRelease` -> `app/build/outputs/apk/release/`
- GitHub Actions: il workflow `Build NardiTune APK` produce l'APK scaricabile come artifact.
