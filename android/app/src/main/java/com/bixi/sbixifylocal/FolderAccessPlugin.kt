package com.bixi.sbixifylocal

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin

private val AUDIO_EXTENSIONS = setOf(
    "mp3", "m4a", "flac", "wav", "ogg", "opus", "aac", "wma"
)

@CapacitorPlugin(name = "FolderAccess")
class FolderAccessPlugin : Plugin() {

    // Opens the system folder picker (Storage Access Framework).
    // Result is handled in pickFolderResult below.
    @PluginMethod
    fun pickFolder(call: PluginCall) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
        startActivityForResult(call, intent, "pickFolderResult")
    }

    @ActivityCallback
    private fun pickFolderResult(call: PluginCall, result: androidx.activity.result.ActivityResult) {
        if (result.resultCode != android.app.Activity.RESULT_OK || result.data?.data == null) {
            call.reject("Nessuna cartella selezionata")
            return
        }
        val treeUri: Uri = result.data!!.data!!

        // Persist read access across app restarts / reboots.
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        val ret = buildFolderResult(treeUri)
        if (ret == null) {
            call.reject("Impossibile leggere la cartella selezionata")
        } else {
            call.resolve(ret)
        }
    }

    // Re-scans a folder we already have persisted permission for.
    // Used to refresh a playlist (pick up new files) without re-prompting the user.
    @PluginMethod
    fun listFolder(call: PluginCall) {
        val uriString = call.getString("uri")
        if (uriString == null) {
            call.reject("Parametro 'uri' mancante")
            return
        }
        val treeUri = Uri.parse(uriString)

        val hasPermission = context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission
        }
        if (!hasPermission) {
            call.reject("Permesso non pi\u00f9 valido per questa cartella, va riselezionata")
            return
        }

        val ret = buildFolderResult(treeUri)
        if (ret == null) {
            call.reject("Impossibile leggere la cartella")
        } else {
            call.resolve(ret)
        }
    }

    // Copia il file (via ContentResolver, streaming) nella cache dell'app e ritorna il
    // percorso reale sul filesystem. Usato per la riproduzione: evita di far transitare
    // l'intero file audio come stringa base64 dentro JavaScript (costoso in CPU sul
    // thread principale, causa di scatti durante le animazioni), lasciando che sia il
    // tag <audio> a leggerlo direttamente tramite Capacitor.convertFileSrc().
    @PluginMethod
    fun copyToCache(call: PluginCall) {
        val uriString = call.getString("uri")
        if (uriString == null) {
            call.reject("Parametro 'uri' mancante")
            return
        }
        val uri = Uri.parse(uriString)
        try {
            val cacheDir = java.io.File(context.cacheDir, "audio_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            // Nome file stabile e univoco per uri (hash), così una seconda richiesta per
            // lo stesso brano riusa il file già copiato invece di ricopiarlo.
            val fileName = "t" + uriString.hashCode() + guessExtensionFromName(uriString)
            val outFile = java.io.File(cacheDir, fileName)
            if (!outFile.exists() || outFile.length() == 0L) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: run {
                    call.reject("File non leggibile")
                    return
                }
            }
            val ret = JSObject()
            ret.put("path", outFile.absolutePath)
            pruneAudioCache(cacheDir)
            call.resolve(ret)
        } catch (e: Exception) {
            call.reject("Errore copia file: ${e.message}")
        }
    }

    private fun guessExtensionFromName(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return if (ext.length in 2..4) ".$ext" else ""
    }

    // Tiene la cache dei file audio copiati entro un numero ragionevole di elementi
    // (gli ultimi ascoltati/vicini), invece di lasciarla crescere per sempre man mano
    // che si ascoltano brani diversi nel tempo.
    private fun pruneAudioCache(cacheDir: java.io.File) {
        try {
            val files = cacheDir.listFiles() ?: return
            if (files.size <= 8) return
            files.sortedBy { it.lastModified() }
                .dropLast(8)
                .forEach { it.delete() }
        } catch (e: Exception) { /* pulizia best-effort, non critica */ }
    }

    // Reads a file's bytes given its content:// URI and returns them base64-encoded,
    // so the web layer can build a Blob and play/seek it locally (no network semantics needed).
    // maxBytes (opzionale): legge solo il PREFISSO del file - usato per leggere i tag
    // ID3 di copertina/titolo senza dover caricare in memoria file interi anche da 30-40MB
    // (i tag e la copertina incorporata stanno sempre nei primi KB/MB del file).
    @PluginMethod
    fun readFile(call: PluginCall) {
        val uriString = call.getString("uri")
        if (uriString == null) {
            call.reject("Parametro 'uri' mancante")
            return
        }
        val uri = Uri.parse(uriString)
        val maxBytes = call.getInt("maxBytes")

        try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: guessMimeFromName(uri.lastPathSegment ?: "")
            val bytes = resolver.openInputStream(uri)?.use { stream ->
                if (maxBytes != null && maxBytes > 0) {
                    val buffer = ByteArray(maxBytes)
                    var total = 0
                    while (total < maxBytes) {
                        val read = stream.read(buffer, total, maxBytes - total)
                        if (read <= 0) break
                        total += read
                    }
                    if (total == buffer.size) buffer else buffer.copyOf(total)
                } else {
                    stream.readBytes()
                }
            }
            if (bytes == null) {
                call.reject("File non leggibile")
                return
            }
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val ret = JSObject()
            ret.put("data", encoded)
            ret.put("mimeType", mimeType)
            call.resolve(ret)
        } catch (e: Exception) {
            call.reject("Errore lettura file: ${e.message}")
        }
    }

    // Lists every folder we currently hold a persisted permission for,
    // useful for cleanup / sanity-checking saved playlists on app start.
    @PluginMethod
    fun listPersistedFolders(call: PluginCall) {
        val arr = JSArray()
        context.contentResolver.persistedUriPermissions.forEach {
            if (it.isReadPermission) arr.put(it.uri.toString())
        }
        val ret = JSObject()
        ret.put("folders", arr)
        call.resolve(ret)
    }

    // Drops a previously persisted permission (removing a playlist's native access).
    @PluginMethod
    fun releaseFolder(call: PluginCall) {
        val uriString = call.getString("uri")
        if (uriString == null) {
            call.reject("Parametro 'uri' mancante")
            return
        }
        try {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(uriString),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            call.resolve()
        } catch (e: Exception) {
            call.reject("Errore rilascio permesso: ${e.message}")
        }
    }

    private fun buildFolderResult(treeUri: Uri): JSObject? {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val files = JSArray()
        collectAudioFiles(treeUri, rootDocumentId, root.name ?: "", files)

        val ret = JSObject()
        ret.put("uri", treeUri.toString())
        ret.put("name", root.name ?: "Cartella")
        ret.put("files", files)
        return ret
    }

    // Cammina l'albero della cartella interrogando direttamente il ContentResolver
    // (DocumentsContract) invece di usare DocumentFile.listFiles(): quest'ultimo, su
    // alcuni dispositivi/provider (in particolare per cartelle sotto Download, che
    // spesso sono servite tramite MediaProvider), può restituire una lista vuota pur
    // avendo un permesso valido e leggendo correttamente il nome della cartella
    // stessa. La query diretta è il modo più a basso livello, e quindi più affidabile,
    // per elencare i figli di un document tree su Android.
    private fun collectAudioFiles(treeUri: Uri, parentDocumentId: String, relativePath: String, out: JSArray, depth: Int = 0) {
        if (depth > 8) return // guard against pathological folder nesting

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )

        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idCol) ?: continue
                    val name = cursor.getString(nameCol) ?: continue
                    val mime = cursor.getString(mimeCol)
                    val size = cursor.getLong(sizeCol)

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        collectAudioFiles(treeUri, docId, "$relativePath/$name", out, depth + 1)
                    } else if (isAudioFile(name)) {
                        val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        val entry = JSObject()
                        entry.put("uri", childUri.toString())
                        entry.put("name", name)
                        entry.put("path", "$relativePath/$name")
                        entry.put("size", size)
                        out.put(entry)
                    }
                }
            }
        } catch (e: Exception) {
            // Sottocartella non leggibile per qualche motivo: si salta, non si blocca
            // tutta la scansione della cartella principale per questo.
        }
    }

    private fun isAudioFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in AUDIO_EXTENSIONS
    }

    private fun guessMimeFromName(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/mp4"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "ogg", "opus" -> "audio/ogg"
            "wma" -> "audio/x-ms-wma"
            else -> "application/octet-stream"
        }
    }
}
