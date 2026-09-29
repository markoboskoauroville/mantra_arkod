package com.mantra.arkod

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

/**
 * THE CHOSEN FOLDER, AS A FILE MANAGER FOR ONE KIND OF FILE.
 *
 * Baba, 15.9.2026: the tracks menu is the folder he picked, showing only GPX, with rename and
 * delete and the folder's own name written at the top so he knows where they are. Export is gone
 * with this, because a track that is already in the folder he chose has nowhere left to go.
 *
 * DocumentFile is slow — every call crosses into another app — so the listing is fetched once and
 * held, never inside a composition (the mistake that made the colour row crawl on 15.9.2026).
 */
object Folder {

    data class Entry(val uri: Uri, val fileName: String, val bytes: Long, val modifiedMs: Long) {
        /** What the list shows: the name he gave it, with the extension after it. */
        val name: String get() = Tracks.displayName(fileName)
        val extension: String get() = fileName.substringAfterLast('.', "").lowercase()
    }

    fun tree(context: Context, store: Store): DocumentFile? {
        val uri = store.exportTreeUri ?: return null
        return DocumentFile.fromTreeUri(context, Uri.parse(uri))
    }

    /** Every GPX in the folder, newest first. Anything else in there is not our business. */
    fun list(context: Context, store: Store): List<Entry> {
        val tree = tree(context, store) ?: return emptyList()
        return tree.listFiles()
            .filter { it.isFile && (it.name ?: "").endsWith(".gpx", ignoreCase = true) }
            .map { Entry(it.uri, it.name ?: "track.gpx", it.length(), it.lastModified()) }
            .sortedByDescending { it.modifiedMs }
    }

    /**
     * Rename one, keeping its extension: he types a name, not a file name. Returns null when it
     * worked, or the reason.
     *
     * THE BUG THIS FIXES, 15.9.2026: it used to go through DocumentFile.fromSingleUri, and what
     * that returns is a SingleDocumentFile, which does not implement renameTo at all. Pressing
     * rename did nothing and said nothing. DocumentsContract.renameDocument is the call that
     * works on a document taken out of a tree, and it answers with the new address.
     */
    fun rename(context: Context, entry: Entry, newName: String): String? {
        val wanted = Tracks.safeFileName(newName)
        if (wanted.equals(entry.fileName, ignoreCase = true)) return null
        return try {
            val moved = DocumentsContract.renameDocument(context.contentResolver, entry.uri, wanted)
            if (moved != null) null else "Mapa ne dopušta preimenovanje"
        } catch (e: UnsupportedOperationException) {
            "Ova mapa ne dopušta preimenovanje. Mapa u memoriji telefona dopušta."
        } catch (e: IllegalStateException) {
            // The provider throws this when the name is taken, which is worth saying plainly.
            "Već postoji trag imena $newName"
        } catch (e: Exception) {
            "Preimenovanje nije uspjelo: ${e.javaClass.simpleName}"
        }
    }

    fun delete(context: Context, entry: Entry): String? = try {
        // The same lesson as rename: go to the provider directly rather than through a wrapper
        // that may not implement what is being asked of it.
        if (DocumentsContract.deleteDocument(context.contentResolver, entry.uri)) {
            null
        } else {
            "Mapa ne dopušta brisanje"
        }
    } catch (e: Exception) {
        "Brisanje nije uspjelo: ${e.javaClass.simpleName}"
    }

    fun read(context: Context, entry: Entry): String? = try {
        context.contentResolver.openInputStream(entry.uri)?.bufferedReader()?.use { it.readText() }
    } catch (e: Exception) {
        null
    }

    /**
     * Put a finished recording in the folder under this name. Returns null when it worked.
     * A name already taken is replaced, because it is the same walk being saved again.
     */
    fun save(context: Context, store: Store, source: java.io.File, name: String): String? {
        val tree = tree(context, store) ?: return "Mapa još nije odabrana"
        val fileName = Tracks.safeFileName(name)
        return try {
            tree.findFile(fileName)?.delete()
            // Some providers refuse a mime type they have never met, so the wider ones follow.
            val target = tree.createFile("application/gpx+xml", fileName)
                ?: tree.createFile("text/xml", fileName)
                ?: tree.createFile("application/octet-stream", fileName)
                ?: return "Mapa ne prima datoteku"
            context.contentResolver.openOutputStream(target.uri)?.use { out ->
                source.inputStream().use { it.copyTo(out) }
            } ?: return "Datoteka se nije mogla zapisati"
            null
        } catch (e: Exception) {
            "Spremanje nije uspjelo: ${e.javaClass.simpleName}"
        }
    }

    /**
     * A TEXT FILE, WHEREVER HE CAN FIND IT (27.9.2026): into the folder he chose for his tracks,
     * or, with none chosen, into the phone's Downloads. Returns where it went, or why not.
     */
    fun saveText(context: Context, store: Store, name: String, text: String): Pair<Boolean, String> {
        val bytes = text.toByteArray(Charsets.UTF_8)
        tree(context, store)?.let { tree ->
            return try {
                tree.findFile(name)?.delete()
                val target = tree.createFile("text/plain", name)
                    ?: return false to "mapa ne prima datoteku"
                context.contentResolver.openOutputStream(target.uri)?.use { it.write(bytes) }
                    ?: return false to "datoteka se nije mogla zapisati"
                true to "spremljeno: $name u ${label(context, store)}"
            } catch (e: Exception) {
                false to "spremanje nije uspjelo: ${e.javaClass.simpleName}"
            }
        }
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return false to "Preuzimanja ne primaju datoteku"
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                true to "spremljeno: $name u Preuzimanja"
            } else {
                val dir = context.getExternalFilesDir(null) ?: context.filesDir
                java.io.File(dir, name).writeBytes(bytes)
                true to "spremljeno: $name u ${dir.absolutePath}"
            }
        } catch (e: Exception) {
            false to "spremanje nije uspjelo: ${e.javaClass.simpleName}"
        }
    }

    /** The folder as somebody would say it: the last part of its path, or its own name. */
    fun label(context: Context, store: Store): String {
        val uri = store.exportTreeUri ?: return "mapa još nije odabrana"
        val fromName = store.exportFolderName
        if (!fromName.isNullOrBlank()) return fromName
        return Uri.parse(uri).lastPathSegment?.substringAfterLast(':')?.ifBlank { null } ?: "odabrana mapa"
    }
}
