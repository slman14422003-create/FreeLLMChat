package com.freellm.chat.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class BackupFile(val name: String, val id: String, val size: Long)

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
        if (now - local.autoBackupFailedAt < RETRY_BACKOFF) return false
        val ok = withContext(Dispatchers.IO) {
            runCatching { write(Uri.parse(folder), pw) }.isSuccess
        }
        if (ok) {
            local.autoBackupLast = now
            local.autoBackupFailedAt = 0L
        } else {
            local.autoBackupFailedAt = now
        }
        return ok
    }

    private suspend fun write(tree: Uri, pw: String) {
        val resolver = ctx.contentResolver
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
        val name = PREFIX + LocalDate.now() + ".bak"
        val doc = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name)
            ?: throw IllegalStateException("create failed")
        try {
            backup.export(doc, pw.toCharArray())
        } catch (e: kotlinx.coroutines.CancellationException) {
            runCatching { DocumentsContract.deleteDocument(resolver, doc) }
            throw e
        } catch (e: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, doc) }
            throw e
        }
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
        val found = ArrayList<BackupFile>()
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_SIZE
            ),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(1).orEmpty()
                if (n.startsWith(PREFIX)) found.add(BackupFile(n, c.getString(0).orEmpty(), if (c.isNull(2)) -1L else c.getLong(2)))
            }
        }
        selectDrop(found, KEEP).forEach { id ->
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
        private const val RETRY_BACKOFF = 3_600_000L

        fun toDelete(names: List<String>, keep: Int): List<String> = names.sortedDescending().drop(keep)

        fun selectDrop(files: List<BackupFile>, keep: Int): List<String> {
            val empty = files.filter { it.size == 0L }
            val real = files.filter { it.size != 0L }
            val dropNames = toDelete(real.map { it.name }, keep).toSet()
            return empty.map { it.id } + real.filter { it.name in dropNames }.map { it.id }
        }
    }
}
