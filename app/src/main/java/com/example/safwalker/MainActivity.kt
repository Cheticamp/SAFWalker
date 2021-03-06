package com.example.safwalker

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import android.provider.DocumentsContract.EXTRA_INITIAL_URI
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private val handleIntentActivityResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                val directoryUri = it.data?.data ?: return@registerForActivityResult
                mContentResolver.takePersistableUriPermission(
                    directoryUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                displaySafTree(mAndroidUri, mAndroidDataDocId)
            }
        }

    private val mAndroidDataDocId = "$ANDROID_DOCID/data"

    private val mAndroidUri: Uri = DocumentsContract.buildTreeDocumentUri(
        EXTERNAL_STORAGE_PROVIDER_AUTHORITY,
        ANDROID_DOCID
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        val openDirectoryButton = findViewById<FloatingActionButton>(R.id.fab_open_directory)
        openDirectoryButton.setOnClickListener {
            openDirectory()
        }
    }

    /*
    * To walk all of /storage/emulated/0/data, values are initially:
    *
    * treeUri = content://com.android.externalstorage.documents/tree/primary%3AAndroid
    * docId = primary:Android/data
    * childrenUri = content://com.android.externalstorage.documents/tree/primary%3AAndroid/document/primary%3AAndroid%2Fdata/children
    */
    private val mContentResolver: ContentResolver by lazy { this.contentResolver }

    private fun displaySafTree(treeUri: Uri, docId: String) {
        val testDisplay: TextView by lazy { findViewById(R.id.directoryView) }
        val sb = java.lang.StringBuilder()
        var currentLevel = -1
        var fileCount = 0

        GlobalScope.launch(Dispatchers.IO) {
            SafUtils.walkSafTree(mContentResolver, treeUri, docId,
                { levelChange ->
                    if (levelChange < 0) {
                        currentLevel--
                    } else {
                        currentLevel++
                        GlobalScope.launch(Dispatchers.Main) {
                            testDisplay.text = getString(R.string.file_progress, fileCount)
                        }
                    }
                },
                { cursor ->
                    fileCount++
                    val nameIndex = cursor.getColumnIndex(SafUtils.COLUMNS_DISPLAY_NAME)
                    val mimeIndex = cursor.getColumnIndex(SafUtils.COLUMNS_MIME_TYPE)
                    val displayName = cursor.getString(nameIndex)
                    val mimeType = cursor.getString(mimeIndex)
                    sb.append("> ${" ".repeat(currentLevel * INDENT)}$displayName (${mimeType})\n")
                    false
                })
            GlobalScope.launch(Dispatchers.Main) {
                testDisplay.text = sb.toString()
            }
        }
    }

    private fun openDirectory() {
        val uri = DocumentsContract.buildDocumentUri(
            EXTERNAL_STORAGE_PROVIDER_AUTHORITY,
            ANDROID_DOCID
        )
        val treeUri = DocumentsContract.buildTreeDocumentUri(
            EXTERNAL_STORAGE_PROVIDER_AUTHORITY,
            ANDROID_DOCID
        )

        mContentResolver.persistedUriPermissions.find {
            it.uri.equals(treeUri) && it.isReadPermission
        }?.run {
            displaySafTree(mAndroidUri, mAndroidDataDocId)
        } ?: run {
            val intent =
                getPrimaryVolume().createOpenDocumentTreeIntent()
                    .putExtra(EXTRA_INITIAL_URI, uri)
            handleIntentActivityResult.launch(intent)
        }
    }

    private fun getPrimaryVolume(): StorageVolume {
        val sm = getSystemService(STORAGE_SERVICE) as StorageManager
        return sm.primaryStorageVolume
    }

    companion object {
        const val INDENT = 4
        const val EXTERNAL_STORAGE_PROVIDER_AUTHORITY = "com.android.externalstorage.documents"
        const val ANDROID_DOCID = "primary:Android"
    }
}