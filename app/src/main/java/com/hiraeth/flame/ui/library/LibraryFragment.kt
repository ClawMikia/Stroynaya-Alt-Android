package com.hiraeth.flame.ui.library

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hiraeth.flame.R
import com.hiraeth.flame.databinding.FragmentLibraryBinding
import com.hiraeth.flame.domain.LibrarySort
import com.hiraeth.flame.domain.LibraryViewMode
import com.hiraeth.flame.domain.MediaTypeFilter
import com.hiraeth.flame.ui.util.AppPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private val container get() = (requireActivity().application as com.hiraeth.flame.HiraethApplication).container

    private val viewModel: LibraryViewModel by viewModels {
        LibraryViewModel.factory(container.mediaRepository)
    }

    private lateinit var adapter: MediaLibraryAdapter

    private var targetCombineCount = 0

    private val quickImportLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            viewLifecycleOwner.lifecycleScope.launch {
                Toast.makeText(requireContext(), "Importing ${uris.size} items...", Toast.LENGTH_SHORT).show()
                withContext(Dispatchers.IO) {
                    uris.forEach { uri ->
                        try {
                            val type = requireContext().contentResolver.getType(uri).orEmpty()
                            val isVideo = type.startsWith("video/")
                            val name = uri.lastPathSegment ?: "Imported Media"
                            container.mediaRepository.importFromUri(uri, name, "Imported from device", isVideo)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                Toast.makeText(requireContext(), "Import complete!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = MediaLibraryAdapter(
            container = container,
            gridMode = viewModel.viewModeState.value == LibraryViewMode.Grid,
            onItemClick = { id ->
                val b = Bundle().apply { 
                    putLong("mediaId", id)
                    putLong("albumId", -1L)
                }
                findNavController().navigate(R.id.action_library_to_detail, b)
            }
        )
        binding.recycler.adapter = adapter
        applyLayoutManager()

        val sortLabels = LibrarySort.entries.map { it.name.replace(Regex("([a-z])([A-Z])"), "$1 $2") }
        binding.sortSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, sortLabels)
        binding.sortSpinner.setSelection(LibrarySort.entries.indexOf(LibrarySort.DateNewest))
        binding.sortSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, position: Int, id: Long) {
                viewModel.setSort(LibrarySort.entries[position])
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setQuery(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.tagFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setTagFilter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnGrantPermissions.setOnClickListener {
            (activity as? com.hiraeth.flame.MainActivity)?.requestAppPermissions()
        }

        val navController = findNavController()
        val appBarConfig = AppBarConfiguration(
            setOf(R.id.libraryFragment, R.id.cameraFragment, R.id.albumsFragment),
        )
        binding.toolbar.setupWithNavController(navController, appBarConfig)
        binding.toolbar.inflateMenu(R.menu.menu_library)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_toggle_view -> {
                    viewModel.toggleViewMode()
                    true
                }
                R.id.action_combine -> {
                    showCombineDialog()
                    true
                }
                else -> false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.items.collect { adapter.submitList(it) }
                }
                launch {
                    viewModel.viewModeState.collect { mode ->
                        val grid = mode == LibraryViewMode.Grid
                        adapter.setGridMode(grid)
                        applyLayoutManager()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                updatePermissionUi()
            }
        }
    }

    private fun showCombineDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_combine_images, null)
        val input = dialogView.findViewById<EditText>(R.id.combine_count_input)
        input.hint = "e.g. 3"

        MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setTitle("Combine Images")
            .setMessage("How many images do you want to combine?")
            .setView(dialogView)
            .setPositiveButton("Select") { _, _ ->
                val count = input.text.toString().toIntOrNull() ?: 0
                if (count > 1) {
                    startSelectionMode(count)
                } else {
                    Toast.makeText(requireContext(), "Enter a number > 1", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startSelectionMode(count: Int) {
        targetCombineCount = count
        Toast.makeText(requireContext(), "Select $count images to combine", Toast.LENGTH_LONG).show()
        adapter.enterSelectionMode { selectedCount ->
            if (selectedCount == targetCombineCount) {
                combineSelectedImages()
            }
        }
    }

    private fun combineSelectedImages() {
        val selected = adapter.getSelectedItems()
        adapter.exitSelectionMode()
        
        if (selected.any { it.isVideo }) {
            Toast.makeText(requireContext(), "Only images can be combined", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            Toast.makeText(requireContext(), "Combining images...", Toast.LENGTH_SHORT).show()
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val bitmaps = selected.map { entity ->
                        val file = container.mediaRepository.resolveFile(entity)
                        BitmapFactory.decodeFile(file.absolutePath)
                    }.filterNotNull()

                    if (bitmaps.isEmpty()) return@withContext null

                    val n = bitmaps.size
                    val cellWidth = bitmaps.maxOf { it.width }
                    val cellHeight = bitmaps.maxOf { it.height }
                    
                    val totalWidth = cellWidth * n
                    val totalHeight = cellHeight
                    
                    val result = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(result)
                    canvas.drawColor(android.graphics.Color.BLACK)

                    for (i in bitmaps.indices) {
                        val b = bitmaps[i]
                        val left = i * cellWidth.toFloat()
                        val top = 0f
                        
                        val matrix = android.graphics.Matrix()
                        val scale: Float
                        var dx = 0f
                        var dy = 0f

                        if (b.width * cellHeight > cellWidth * b.height) {
                            scale = cellHeight.toFloat() / b.height.toFloat()
                            dx = (cellWidth - b.width * scale) * 0.5f
                        } else {
                            scale = cellWidth.toFloat() / b.width.toFloat()
                            dy = (cellHeight - b.height * scale) * 0.5f
                        }

                        matrix.setScale(scale, scale)
                        matrix.postTranslate(left + dx, top + dy)
                        
                        canvas.save()
                        canvas.clipRect(left, top, left + cellWidth, top + cellHeight)
                        canvas.drawBitmap(b, matrix, null)
                        canvas.restore()
                    }
                    result
                }

                if (bitmap != null) {
                    container.mediaRepository.saveBitmapAsMedia(
                        bitmap, 
                        "Combined",
                        "Created by combining ${selected.size} images"
                    )
                    Toast.makeText(requireContext(), "Image combined and saved!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Failed to combine images", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyLayoutManager() {
        val grid = viewModel.viewModeState.value == LibraryViewMode.Grid
        binding.recycler.layoutManager = if (grid) {
            GridLayoutManager(requireContext(), 3)
        } else {
            LinearLayoutManager(requireContext())
        }
    }

    private fun hasAllPermissions(): Boolean =
        AppPermissions.requiredPermissions().all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }

    private fun updatePermissionUi() {
        val ok = hasAllPermissions()
        binding.btnGrantPermissions.visibility = if (ok) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUi()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
