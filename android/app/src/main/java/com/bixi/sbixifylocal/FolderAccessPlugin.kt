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

    // Reads a file's bytes given its content:// URI and returns them base64-encoded,
    // so the web layer can build a Blob and play/seek it locally (no network semantics needed).
    @PluginMethod
    fun readFile(call: PluginCall) {
        val uriString = call.getString("uri")
        if (uriString == null) {
            call.reject("Parametro 'uri' mancante")
            return
        }
        val uri = Uri.parse(uriString)

        try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: guessMimeFromName(uri.lastPathSegment ?: "")
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
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
        val files = JSArray()
        collectAudioFiles(root, root.name ?: "", files)

        val ret = JSObject()
        ret.put("uri", treeUri.toString())
        ret.put("name", root.name ?: "Cartella")
        ret.put("files", files)
        return ret
    }

    // Recursively walks the folder tree (SAF only exposes children via repeated queries,
    // there is no direct path-based traversal) and collects audio files.
    private fun collectAudioFiles(dir: DocumentFile, relativePath: String, out: JSArray, depth: Int = 0) {
        if (depth > 8) return // guard against pathological folder nesting
        val children = dir.listFiles()
        for (child in children) {
            val name = child.name ?: continue
            if (child.isDirectory) {
                collectAudioFiles(child, "$relativePath/$name", out, depth + 1)
            } else if (isAudioFile(name)) {
                val entry = JSObject()
                entry.put("uri", child.uri.toString())
                entry.put("name", name)
                entry.put("path", "$relativePath/$name")
                entry.put("size", child.length())
                out.put(entry)
            }
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
