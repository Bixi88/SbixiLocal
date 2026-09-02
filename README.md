# SbixiLocal

App Android nativa (via Capacitor), offline, per riprodurre musica dai file
locali del telefono. Nessuna funzione online: niente ricerca, niente
download, niente testi.

## Cosa contiene questo pacchetto

- `www/` — interfaccia web (placeholder, in attesa dell'UI reale di Sbixify)
- `android/` — progetto Android nativo generato da Capacitor
  - `FolderAccessPlugin.kt` — plugin nativo per scegliere una cartella
    (Storage Access Framework) e tenerne accesso persistente, senza dover
    richiedere permessi di sistema classici
- `.github/workflows/build-apk.yml` — compila l'APK firmato ad ogni push
- `keystore/` — la chiave di firma generata per te (**non è nel repo Git,
  vedi sotto**)

## Passo 1 — Crea il repository

Crea un repository nuovo su GitHub chiamato `SbixiLocal`, poi carica dentro
tutto il contenuto di questo pacchetto **tranne la cartella `keystore/`**
(quella non va mai messa su GitHub, nemmeno privato).

## Passo 2 — Configura i secret

Nel repository: **Settings → Secrets and variables → Actions → New repository secret**.
Crea questi quattro secret, usando i valori nel file `keystore/CREDENZIALI_FIRMA.txt`:

| Nome secret | Valore |
|---|---|
| `SBIXILOCAL_KEYSTORE_BASE64` | contenuto del file `keystore/sbixilocal-release.jks.base64` (incolla tutto il testo) |
| `SBIXILOCAL_KEYSTORE_PASSWORD` | la password indicata nel file credenziali |
| `SBIXILOCAL_KEY_ALIAS` | `sbixilocal` |
| `SBIXILOCAL_KEY_PASSWORD` | la stessa password (store e key coincidono) |

Poi metti al sicuro (es. gestore password) sia `CREDENZIALI_FIRMA.txt` sia
`sbixilocal-release.jks`: se li perdi non potrai più aggiornare l'app senza
disinstallarla.

## Passo 3 — Primo build

Con i secret impostati, ogni push sul branch `main` fa partire il workflow
automaticamente (visibile nella tab **Actions** del repo). A fine build trovi
l'APK firmato in due posti:
- come **artifact** allegato alla run (in fondo alla pagina della run)
- come **Release** del repository (tag `build-N`), più comodo da scaricare
  da telefono

## Passo 4 — Installazione sul telefono

Scarica `SbixiLocal-latest.apk` dalla Release più recente direttamente dal
browser del telefono, apri il file scaricato e installa (la prima volta
Android chiederà di abilitare l'installazione da questa sorgente).

## Prossimo passo

Il contenuto di `www/` è ancora un placeholder: appena hai i file sorgente
attuali di Sbixify (index.html/css/js), li integro qui rimuovendo tutte le
funzioni online e collegando l'interfaccia al plugin `FolderAccess` per la
selezione delle cartelle.
