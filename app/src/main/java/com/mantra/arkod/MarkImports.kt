package com.mantra.arkod

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * MOJE ČESTICE IN AND OUT OF THE PHONE (30.9.2026, v8). A file picked in the app, or sent to it
 * (WhatsApp, e-mail, Files: "open with ARKOD Layer"), is read here and handed to the screen, which
 * makes it a group; a group goes out through the phone's own share sheet as one .arkod.json file.
 */
object MarkImports {

    private val _incoming = MutableStateFlow<MarkFile.Read?>(null)
    val incoming: StateFlow<MarkFile.Read?> = _incoming

    fun done() {
        _incoming.value = null
    }

    /** Read [uri]; the file's own name names the group. False when it is not a Moje čestice file. */
    fun offer(context: Context, uri: Uri): Boolean {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment
        val text = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
        val read = text?.let { MarkFile.decode(it, name) }
        if (read == null) {
            Trail.say("That file has no Moje čestice in it")
            return false
        }
        _incoming.value = read
        return true
    }

    /** The uri an intent carries, if it is one that brings a file: VIEW, or SEND with a stream. */
    fun uriOf(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        else -> null
    }

    /** One group as one file, offered to whatever the phone can send it with. */
    fun share(context: Context, group: MarkFile.Group, marks: List<Parcels.Mark>) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, MarkFile.fileName(group.name))
        file.writeText(MarkFile.encode(group, marks))
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            putExtra(Intent.EXTRA_TEXT, "${group.name.ifBlank { "Moje čestice" }}: ${marks.size} čestica. Otvorite datoteku u aplikaciji ARKOD Layer.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
