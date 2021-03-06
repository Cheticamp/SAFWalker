package com.example.safwalker

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns

class SafUtils {

    companion object {
        const val MIME_TYPE_IS_DIRECTORY = "vnd.android.document/directory"
        const val COLUMNS_DISPLAY_NAME = OpenableColumns.DISPLAY_NAME
        const val COLUMNS_MIME_TYPE = "mime_type"

        /**
         * Walk a file tree using the Storage Access Framework.
         *
         * @param contentResolver [ContentResolver] from Context.
         * @param treeUri Top level [Uri] of tree to traverse.
         * @param docId The document whose children will be discovered.
         * @param onNewLevel Function called when the level in the file hierarchy changes.
         *          onNewLevel(Int) where Int == 1 when a level is descended to and -1 when
         *          a level is ascended to.
         * @param onNewFile Function called for each file discovered.
         *          onNewFile(Cursor) : Boolean where [Cursor] is positioned the current file.
         *          The return code can be set to true if the tree traversal should be
         *          halted immediately.
         */
        @JvmStatic
        fun walkSafTree(
            contentResolver: ContentResolver,
            treeUri: Uri,
            docId: String,
            onNewLevel: (Int) -> Unit,
            onNewFile: (Cursor) -> Boolean
        ) {
            onNewLevel(1)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            contentResolver.query(childrenUri, null, null, null)
                ?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(COLUMNS_DISPLAY_NAME)
                    val mimeIndex = cursor.getColumnIndex(COLUMNS_MIME_TYPE)
                    while (cursor.moveToNext()) {
                        if (onNewFile(cursor)) break
                        val mimeType = cursor.getString(mimeIndex)
                        if (mimeType == MIME_TYPE_IS_DIRECTORY) {
                            val displayName = cursor.getString(nameIndex)
                            walkSafTree(
                                contentResolver,
                                treeUri,
                                "$docId/$displayName",
                                onNewLevel,
                                onNewFile
                            )
                        }
                    }
                }
            onNewLevel(-1)
        }
    }
}