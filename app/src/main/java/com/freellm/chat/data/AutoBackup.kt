package com.freellm.chat.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Writes an encrypted backup into a user-chosen folder when the chosen interval has passed. */
class AutoBackup(
    private val ctx: Context,
    private val backup: BackupManager,
    private val secrets: SecretStore,
    private val local: LocalPrefs
) {
    suspend fun runIfDue(now: Long = System.currentTimeMillis()): Boolean {
        if (!local.autoBackupOn) return false
        val folder = local.autoBackupFolder ?: return false
        val pw = secrets.extra(PW_KEY)?.takeIf { it.isNotEmpty() } ?: return false
        if (now - local.autoBackupLast < local.autoBackupDays * DAY) return false
        val ok = withContext(Dispatchers.IO) {
            runCatching { write(Uri.parse(folder), pw) }.isSuccess
        }
        if (ok) local.autoBackupLast = now
        return ok
    }

    private suspend fun write(tree: Uri, pw: String) {
        val resolver = ctx.contentResolver
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
        val name = PREFIX + LocalDate.now() + ".bak"
        val doc = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name)
            ?: throw IllegalStateException("create failed")
        backup.export(doc, pw.toCharArray())
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
        val found = ArrayList<Pair<String, String>>()
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(1).orEmpty()
                if (n.startsWith(PREFIX)) found.add(n to c.getString(0).orEmpty())
            }
        }
        val drop = toDelete(found.map { it.first }, KEEP).toSet()
        found.filter { it.first in drop }.forEach { (_, id) ->
            runCatching {
                DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id))
            }
        }
    }

    companion object {
        const val PW_KEY = "auto_backup_pw"
        private const val PREFIX = "freellm-auto-"
        private const val KEEP = 5
        private const val DAY = 86_400_000L

        fun toDelete(names: List<String>, keep: Int): List<String> = names.sortedDescending().drop(keep)
    }
}
