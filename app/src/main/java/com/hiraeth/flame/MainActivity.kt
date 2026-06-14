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

/**
 * Single-activity host: [NavHostFragment] + bottom navigation.
 * Edge-to-edge is enabled; system bars handled via WindowInsets.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val container get() = (application as HiraethApplication).container

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* fragments re-check permissions in onResume */ }

    private val importFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { handleFolderImport(it) }
    }

    private val importMultipleLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            handleMultipleFilesImport(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply bottom inset to the BottomNavigationView so it sits above the navigation bar
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
            binding.fabImport.isVisible = isTopLevel
        }

        binding.fabImport.setOnClickListener {
            showImportOptions()
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

    private fun handleFolderImport(treeUri: Uri) {
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
                        Toast.makeText(this@MainActivity, "Folder import complete!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "No images found in folder", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun handleMultipleFilesImport(uris: List<Uri>) {
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Importing ${uris.size} items...", Toast.LENGTH_SHORT).show()
            }
            uris.forEach { uri ->
                try {
                    val name = uri.lastPathSegment ?: "Imported Image"
                    // GetMultipleContents filtered by image/* so isVideo=false
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
                Toast.makeText(this@MainActivity, "Import complete!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun requestAppPermissions() {
        permissionLauncher.launch(AppPermissions.requiredPermissions())
    }
}
