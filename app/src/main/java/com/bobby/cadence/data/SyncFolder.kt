package com.bobby.cadence.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/** Reads and writes cadence-sync.json in the folder the user picked (the same kind of folder as the backup folder). */
object SyncFolder {
    sealed interface Read {
        data class Text(val text: String) : Read
        data object Missing : Read
        data object Failed : Read
    }

    private fun findFile(ctx: Context, tree: Uri): Uri? {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        ctx.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == SyncFormat.FILE) return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
            }
        } ?: throw IllegalStateException("folder not readable")
        return null
    }

    fun read(ctx: Context, tree: Uri): Read = try {
        val f = findFile(ctx, tree)
        if (f == null) Read.Missing
        else {
            val text = ctx.contentResolver.openInputStream(f)?.use { it.readBytes().toString(Charsets.UTF_8) }
            if (text == null) Read.Failed else Read.Text(text)
        }
    } catch (e: Exception) {
        Read.Failed
    }

    fun write(ctx: Context, tree: Uri, json: String): Boolean = try {
        val existing = findFile(ctx, tree)
        val target = existing ?: DocumentsContract.createDocument(
            ctx.contentResolver,
            DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)),
            "application/json", SyncFormat.FILE,
        )
        if (target == null) false
        else ctx.contentResolver.openOutputStream(target, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)); true } ?: false
    } catch (e: Exception) {
        false
    }
}
