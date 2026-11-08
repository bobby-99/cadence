package com.bobby.cadence.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.time.LocalDate

/** Writes backup files into a folder the user picked with the system folder picker (no storage permission needed). */
object BackupFolder {
    private const val PREFIX = "cadence-backup-"
    private const val SUFFIX = ".json"

    private data class Child(val id: String, val name: String)

    private fun rootDoc(tree: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private fun children(ctx: Context, tree: Uri): List<Child> {
        val out = ArrayList<Child>()
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        ctx.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) out.add(Child(c.getString(0), c.getString(1) ?: ""))
        }
        return out
    }

    private fun delete(ctx: Context, tree: Uri, c: Child) {
        try {
            DocumentsContract.deleteDocument(ctx.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, c.id))
        } catch (e: Exception) {
        }
    }

    fun folderName(ctx: Context, tree: Uri): String = try {
        ctx.contentResolver.query(
            rootDoc(tree), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "Selected folder"
    } catch (e: Exception) {
        "Selected folder"
    }

    /** Saves today's backup (replacing one from earlier today) and keeps only the newest [keep] files. */
    fun write(ctx: Context, tree: Uri, json: String, keep: Int = 5): Boolean {
        return try {
            val name = PREFIX + LocalDate.now() + SUFFIX
            children(ctx, tree).filter { it.name == name }.forEach { delete(ctx, tree, it) }
            val doc = DocumentsContract.createDocument(ctx.contentResolver, rootDoc(tree), "application/json", name) ?: return false
            val written = ctx.contentResolver.openOutputStream(doc, "wt")?.use { it.write(json.toByteArray()); true } ?: false
            if (!written) return false
            children(ctx, tree)
                .filter { it.name.startsWith(PREFIX) && it.name.endsWith(SUFFIX) }
                .sortedByDescending { it.name }
                .drop(keep)
                .forEach { delete(ctx, tree, it) }
            true
        } catch (e: Exception) {
            false
        }
    }
}
