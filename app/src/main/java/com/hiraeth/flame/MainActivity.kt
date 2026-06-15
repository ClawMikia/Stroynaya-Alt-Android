package com.hiraeth.flame

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hiraeth.flame.databinding.ActivityMainBinding
import com.hiraeth.flame.ui.util.AppPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val container get() = (application as HiraethApplication).container

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    private val importFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { handleFolderImport(it) }
    }

    private val importFolderAsAlbumLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { handleFolderAsAlbumImport(it) }
    }

    private val importMultipleLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            handleMultipleFilesImport(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { view, insets ->
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBar.bottom)
            insets
        }

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController
        binding.bottomNav.setupWithNavController(navController)

        val topLevel = setOf(
            R.id.libraryFragment,
            R.id.cameraFragment,
            R.id.albumsFragment,
        )
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isTopLevel = destination.id in topLevel
            binding.bottomNav.isVisible = isTopLevel
            
            // AlbumsFragment has its own multi-FAB setup, so hide activity FAB there
            binding.fabImport.isVisible = isTopLevel && destination.id != R.id.albumsFragment

            if (destination.id == R.id.libraryFragment) {
                binding.fabImport.setOnClickListener {
                    showImportOptions()
                }
            } else {
                binding.fabImport.setOnClickListener {
                    showImportOptions()
                }
            }
        }

        requestAppPermissions()
    }

    private fun showImportOptions() {
        val options = arrayOf("Import Folder (Images Only)", "Select Multiple Files")
        MaterialAlertDialogBuilder(this, R.style.Dialog_Neon)
            .setTitle("Import Media")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> importFolderLauncher.launch(null)
                    1 -> importMultipleLauncher.launch("image/*")
                }
            }
            .show()
    }

    fun showCreateAlbumDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_new_album, null)
        val inputName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.album_name_input)
        val inputDesc = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.description_input)
        val btnCancel = dialogView.findViewById<android.view.View>(R.id.btn_cancel)
        val btnCreate = dialogView.findViewById<android.view.View>(R.id.btn_create)

        val dialog = MaterialAlertDialogBuilder(this, R.style.Dialog_Neon)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnCreate.setOnClickListener {
            val name = inputName.text?.toString().orEmpty().trim()
            val desc = inputDesc.text?.toString().orEmpty().trim()
            if (name.isNotBlank()) {
                lifecycleScope.launch {
                    container.albumRepository.createAlbum(name, desc)
                    Toast.makeText(this@MainActivity, "Album created!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    
                    val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
                    navHost.navController.navigate(R.id.albumsFragment)
                }
            }
        }
        dialog.show()
    }

    fun importFolderAsAlbum() {
        importFolderAsAlbumLauncher.launch(null)
    }

    fun showLoading(text: String = "Importing...") {
        binding.loadingOverlay.isVisible = true
        binding.loadingText.text = text
        val drawable = binding.loadingIcon.drawable
        if (drawable is android.graphics.drawable.Animatable) {
            drawable.start()
        }
    }

    fun hideLoading() {
        binding.loadingOverlay.isVisible = false
        val drawable = binding.loadingIcon.drawable
        if (drawable is android.graphics.drawable.Animatable) {
            drawable.stop()
        }
    }

    private fun handleFolderAsAlbumImport(treeUri: Uri) {
        showLoading("Importing Album...")
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )

        var folderName = "New Album"
        contentResolver.query(documentUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                folderName = cursor.getString(0)
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val albumId = container.albumRepository.createAlbum(folderName, "Imported from folder $folderName")

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )

            val childProjection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            )

            contentResolver.query(childrenUri, childProjection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

                val itemsToImport = mutableListOf<Triple<Uri, String, Boolean>>()

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idIdx)
                    val mime = cursor.getString(mimeIdx)
                    val name = cursor.getString(nameIdx)

                    if (mime != null && (mime.startsWith("image/") || mime.startsWith("video/"))) {
                        val isVideo = mime.startsWith("video/")
                        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        itemsToImport.add(Triple(uri, name, isVideo))
                    }
                }

                if (itemsToImport.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Importing ${itemsToImport.size} items to '$folderName'...", Toast.LENGTH_SHORT).show()
                    }

                    itemsToImport.forEach { (uri, name, isVideo) ->
                        try {
                            val mediaId = container.mediaRepository.importFromUri(
                                uri = uri,
                                suggestedName = name,
                                description = "Imported to album $folderName",
                                isVideo = isVideo,
                            )
                            container.albumRepository.addToAlbum(albumId, mediaId)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    withContext(Dispatchers.Main) {
                        hideLoading()
                        Toast.makeText(this@MainActivity, "Album '$folderName' imported complete!", Toast.LENGTH_SHORT).show()
                        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
                        navHost.navController.navigate(R.id.albumsFragment)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        hideLoading()
                        Toast.makeText(this@MainActivity, "No media found in folder", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun handleFolderImport(treeUri: Uri) {
        showLoading("Importing Images...")
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )

        lifecycleScope.launch(Dispatchers.IO) {
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            )

            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

                val urisToImport = mutableListOf<Pair<Uri, String>>()

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idIdx)
                    val mime = cursor.getString(mimeIdx)
                    val name = cursor.getString(nameIdx)

                    if ((mime != null) && mime.startsWith("image/")) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        urisToImport.add(uri to name)
                    }
                }

                if (urisToImport.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Importing ${urisToImport.size} images...", Toast.LENGTH_SHORT).show()
                    }

                    urisToImport.forEach { (uri, name) ->
                        try {
                            container.mediaRepository.importFromUri(
                                uri = uri,
                                suggestedName = name,
                                description = "Imported from folder",
                                isVideo = false,
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    withContext(Dispatchers.Main) {
                        hideLoading()
                        Toast.makeText(this@MainActivity, "Folder import complete!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        hideLoading()
                        Toast.makeText(this@MainActivity, "No images found in folder", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun handleMultipleFilesImport(uris: List<Uri>) {
        showLoading("Importing Media...")
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Importing ${uris.size} items...", Toast.LENGTH_SHORT).show()
            }
            uris.forEach { uri ->
                try {
                    val name = uri.lastPathSegment ?: "Imported Image"
                    container.mediaRepository.importFromUri(
                        uri = uri,
                        suggestedName = name,
                        description = "Imported multiple",
                        isVideo = false,
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            withContext(Dispatchers.Main) {
                hideLoading()
                Toast.makeText(this@MainActivity, "Import complete!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun requestAppPermissions() {
        permissionLauncher.launch(AppPermissions.requiredPermissions())
    }
}
