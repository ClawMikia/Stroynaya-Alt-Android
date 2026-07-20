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
import androidx.activity.OnBackPressedCallback
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
import com.hiraeth.flame.ui.albums.MoveToAlbumAdapter
import com.hiraeth.flame.ui.util.AppPermissions
import com.hiraeth.flame.ui.util.ExportFormat
import com.hiraeth.flame.ui.util.ExportHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private val container get() = (requireActivity().application as com.hiraeth.flame.HiraethApplication).container

    private val viewModel: LibraryViewModel by viewModels {
        LibraryViewModel.factory(container.mediaRepository, container.albumRepository)
    }

    private lateinit var adapter: GroupedLibraryAdapter

    private var targetCombineCount = 0
    private var isCombineMode = false

    private val exportSingleLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        uri?.let {
            val entity = pendingExportSingle ?: return@let
            val fmt = pendingExportFormat
            val qual = pendingExportQuality
            viewLifecycleOwner.lifecycleScope.launch {
                Toast.makeText(requireContext(), getString(R.string.exporting), Toast.LENGTH_SHORT).show()
                val ok = ExportHelper.exportSingleImage(requireContext(), entity, container.mediaRepository, fmt, qual, it)
                Toast.makeText(
                    requireContext(),
                    if (ok) getString(R.string.export_success) else getString(R.string.export_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        pendingExportSingle = null
    }

    private val exportZipLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let {
            val entities = pendingExportList
            val fmt = pendingExportFormat
            val qual = pendingExportQuality
            viewLifecycleOwner.lifecycleScope.launch {
                Toast.makeText(requireContext(), getString(R.string.exporting), Toast.LENGTH_SHORT).show()
                val ok = ExportHelper.exportMultipleAsZip(requireContext(), entities, container.mediaRepository, fmt, qual, it)
                Toast.makeText(
                    requireContext(),
                    if (ok) getString(R.string.export_success) else getString(R.string.export_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        pendingExportList = emptyList()
    }

    private var pendingExportSingle: com.hiraeth.flame.data.db.MediaEntity? = null
    private var pendingExportList: List<com.hiraeth.flame.data.db.MediaEntity> = emptyList()
    private var pendingExportFormat = ExportFormat.PNG
    private var pendingExportQuality = 90

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            exitSelectionMode()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        adapter = GroupedLibraryAdapter(
            container = container,
            gridMode = viewModel.viewModeState.value == LibraryViewMode.Grid,
            onItemClick = { id ->
                val b = Bundle().apply {
                    putLong("mediaId", id)
                    putLong("albumId", -1L)
                }
                findNavController().navigate(R.id.action_library_to_detail, b)
            },
            onItemLongClick = { _ ->
                startDeletionSelection()
            },
            onHeaderClick = { headerId ->
                viewModel.toggleHeader(headerId)
            },
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
                    viewModel.groupedItems.collect { adapter.submitList(it) }
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
        isCombineMode = true
        backCallback.isEnabled = true
        Toast.makeText(requireContext(), "Select $count images to combine", Toast.LENGTH_LONG).show()
        setupSelectionToolbar()
        adapter.enterSelectionMode { selectedCount ->
            binding.toolbar.title = getString(R.string.selected_count, selectedCount)
            if (selectedCount == targetCombineCount) {
                combineSelectedImages()
            }
        }
    }

    private fun combineSelectedImages() {
        val selected = adapter.getSelectedItems()
        exitSelectionMode()

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

                    val result = Bitmap.createBitmap(totalWidth, cellHeight, Bitmap.Config.ARGB_8888)
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

                        if ((b.width * cellHeight > cellWidth * b.height)) {
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

    private fun startDeletionSelection() {
        isCombineMode = false
        backCallback.isEnabled = true
        setupSelectionToolbar()
        adapter.enterSelectionMode { count ->
            binding.toolbar.title = getString(R.string.selected_count, count)
        }
    }

    private fun setupSelectionToolbar() {
        binding.toolbar.menu.clear()
        binding.toolbar.inflateMenu(R.menu.menu_selection)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_select_all -> {
                    adapter.selectAll()
                    updateSelectionTitle()
                    true
                }
                R.id.action_deselect_all -> {
                    adapter.deselectAll()
                    updateSelectionTitle()
                    true
                }
                R.id.action_move -> {
                    showMoveToAlbumDialog()
                    true
                }
                R.id.action_export_selected -> {
                    showExportFormatDialog(adapter.getSelectedItems().filter { !it.isVideo }, isSingle = false)
                    true
                }
                R.id.action_delete_selected -> {
                    showMultiDeleteConfirm()
                    true
                }
                R.id.action_cancel_selection -> {
                    exitSelectionMode()
                    true
                }
                else -> false
            }
        }
    }

    private fun updateSelectionTitle() {
        binding.toolbar.title = getString(R.string.selected_count, adapter.getSelectedCount())
    }

    private fun showMultiDeleteConfirm() {
        val selected = adapter.getSelectedItems()
        if (selected.isEmpty()) return

        MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setTitle(getString(R.string.delete_selected_title))
            .setMessage(getString(R.string.delete_selected_message, selected.size))
            .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    selected.forEach { viewModel.delete(it) }
                    exitSelectionMode()
                    Toast.makeText(requireContext(), getString(R.string.delete_selected_success, selected.size), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun showMoveToAlbumDialog() {
        val selected = adapter.getSelectedItems()
        if (selected.isEmpty()) return
        val selectedIds = selected.map { it.id }.toSet()

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_move_to_album, null)
        val recycler = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_albums)
        val btnMove = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_move)
        val btnCancel = dialogView.findViewById<View>(R.id.btn_cancel)

        val moveAdapter = MoveToAlbumAdapter { albumId ->
            btnMove.isEnabled = true
        }
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = moveAdapter

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setView(dialogView)
            .create()

        viewLifecycleOwner.lifecycleScope.launch {
            val albums = container.albumRepository.observeAlbums().first()
            moveAdapter.submitList(albums)
        }

        btnMove.isEnabled = false
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnMove.setOnClickListener {
            val targetAlbumId = moveAdapter.getSelectedAlbumId()
            viewLifecycleOwner.lifecycleScope.launch {
                if (targetAlbumId == null) {
                    container.albumRepository.unlinkFromAllAlbumsBulk(selectedIds.toList())
                } else {
                    val albums = container.albumRepository.observeAlbums().first()
                    for (awm in albums) {
                        val mediaInAlbum = awm.media.filter { it.id in selectedIds }.map { it.id }
                        if (mediaInAlbum.isNotEmpty() && awm.album.id != targetAlbumId) {
                            container.albumRepository.moveToAlbum(awm.album.id, targetAlbumId, mediaInAlbum)
                        }
                    }
                    container.albumRepository.bulkAddToAlbum(targetAlbumId, selectedIds.toList())
                }
                exitSelectionMode()
                Toast.makeText(requireContext(), getString(R.string.move_success, selected.size), Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showExportFormatDialog(images: List<com.hiraeth.flame.data.db.MediaEntity>, isSingle: Boolean) {
        if (images.isEmpty()) {
            Toast.makeText(requireContext(), "No images selected", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_export_format, null)
        val radioPng = dialogView.findViewById<android.widget.RadioButton>(R.id.radio_png)
        val radioJpg = dialogView.findViewById<android.widget.RadioButton>(R.id.radio_jpg)
        val qualityRow = dialogView.findViewById<View>(R.id.quality_row)
        val qualityValue = dialogView.findViewById<android.widget.TextView>(R.id.quality_value)
        val seekbarQuality = dialogView.findViewById<android.widget.SeekBar>(R.id.seekbar_quality)
        val btnCancel = dialogView.findViewById<View>(R.id.btn_cancel)
        val btnExport = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_export)

        seekbarQuality?.progress = 80
        qualityValue?.text = "90%"

        radioPng?.setOnCheckedChangeListener { _, checked ->
            qualityRow?.visibility = if (checked) View.GONE else View.VISIBLE
        }
        radioJpg?.setOnCheckedChangeListener { _, checked ->
            qualityRow?.visibility = if (checked) View.VISIBLE else View.GONE
        }
        seekbarQuality?.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val q = progress + 10
                qualityValue?.text = "$q%"
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnExport.setOnClickListener {
            val format = if (radioJpg?.isChecked == true) ExportFormat.JPG else ExportFormat.PNG
            val quality = (seekbarQuality?.progress ?: 80) + 10
            pendingExportFormat = format
            pendingExportQuality = quality
            if (isSingle) {
                pendingExportSingle = images.first()
                val ext = if (format == ExportFormat.JPG) "jpg" else "png"
                exportSingleLauncher.launch("${images.first().displayName}.$ext")
            } else {
                pendingExportList = images
                exportZipLauncher.launch("export.zip")
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun exitSelectionMode() {
        isCombineMode = false
        backCallback.isEnabled = false
        adapter.exitSelectionMode()
        binding.toolbar.menu.clear()
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
        binding.toolbar.title = getString(R.string.app_name)
    }

    private fun applyLayoutManager() {
        val grid = viewModel.viewModeState.value == LibraryViewMode.Grid
        if (grid) {
            val glm = GridLayoutManager(requireContext(), 3)
            glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (adapter.getItemViewType(position) == 0) 3 else 1
                }
            }
            binding.recycler.layoutManager = glm
        } else {
            binding.recycler.layoutManager = LinearLayoutManager(requireContext())
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
