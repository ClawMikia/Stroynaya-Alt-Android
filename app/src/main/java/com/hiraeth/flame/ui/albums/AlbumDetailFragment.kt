package com.hiraeth.flame.ui.albums

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hiraeth.flame.R
import com.hiraeth.flame.databinding.FragmentAlbumDetailBinding
import com.hiraeth.flame.ui.library.MediaLibraryAdapter
import com.hiraeth.flame.ui.util.ExportFormat
import com.hiraeth.flame.ui.util.ExportHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AlbumDetailFragment : Fragment() {

    private var _binding: FragmentAlbumDetailBinding? = null
    private val binding get() = _binding!!

    private val container get() = (requireActivity().application as com.hiraeth.flame.HiraethApplication).container
    private val albumId: Long get() = requireArguments().getLong("albumId")

    private val viewModel: AlbumDetailViewModel by viewModels {
        AlbumDetailViewModel.factory(container.albumRepository, container.mediaRepository, albumId)
    }

    private lateinit var adapter: MediaLibraryAdapter
    private var inSelectionMode = false

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            exitSelectionMode()
        }
    }

    private val createZipLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let {
            viewModel.exportToZip(requireContext(), it) { success ->
                Toast.makeText(requireContext(), if (success) "Album exported successfully!" else "Export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val exportSingleLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        uri?.let {
            val entity = pendingExportSingle ?: return@let
            val fmt = pendingExportFormat
            val qual = pendingExportQuality
            viewLifecycleOwner.lifecycleScope.launch {
                Toast.makeText(requireContext(), getString(R.string.exporting), Toast.LENGTH_SHORT).show()
                val ok = ExportHelper.exportSingleImage(requireContext(), entity, container.mediaRepository, fmt, qual, it)
                Toast.makeText(requireContext(), if (ok) getString(R.string.export_success) else getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(requireContext(), if (ok) getString(R.string.export_success) else getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
            }
        }
        pendingExportList = emptyList()
    }

    private var pendingExportSingle: com.hiraeth.flame.data.db.MediaEntity? = null
    private var pendingExportList: List<com.hiraeth.flame.data.db.MediaEntity> = emptyList()
    private var pendingExportFormat = ExportFormat.PNG
    private var pendingExportQuality = 90

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAlbumDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        binding.toolbar.setupWithNavController(findNavController())

        adapter = MediaLibraryAdapter(
            container = container,
            gridMode = true,
            onItemClick = { id ->
                if (inSelectionMode) return@MediaLibraryAdapter
                val b = Bundle().apply {
                    putLong("mediaId", id)
                    putLong("albumId", albumId)
                }
                findNavController().navigate(R.id.action_albumDetail_to_detail, b)
            },
            onItemLongClick = { _ ->
                if (!inSelectionMode) enterSelectionMode()
            },
        )

        binding.recyclerMedia.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerMedia.adapter = adapter

        binding.toolbar.inflateMenu(R.menu.menu_album_detail)
        setupNormalMenuListeners()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.albumWithMedia.collect { awm ->
                        if (awm == null) return@collect
                        binding.albumTitle.text = awm.album.name
                        binding.albumDescription.text = awm.album.description
                    }
                }
                launch {
                    viewModel.filteredMedia.collect { adapter.submitList(it) }
                }
            }
        }
    }

    private fun setupNormalMenuListeners() {
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit_album -> { showEditAlbumDialog(); true }
                R.id.action_filter -> { showFilterDialog(); true }
                R.id.action_export_album -> {
                    val albumName = viewModel.albumWithMedia.value?.album?.name ?: "Album"
                    createZipLauncher.launch("${albumName.replace(" ", "_")}.zip")
                    true
                }
                R.id.action_add_media -> { showAddMediaDialog(); true }
                R.id.action_delete_album -> { showDeleteAlbumConfirm(); true }
                else -> false
            }
        }
    }

    private fun enterSelectionMode() {
        inSelectionMode = true
        backCallback.isEnabled = true
        binding.toolbar.menu.clear()
        binding.toolbar.inflateMenu(R.menu.menu_selection)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_select_all -> { adapter.selectAll(); updateSelectionTitle(); true }
                R.id.action_deselect_all -> { adapter.deselectAll(); updateSelectionTitle(); true }
                R.id.action_move -> { showMoveToAlbumDialog(); true }
                R.id.action_export_selected -> {
                    val images = adapter.getSelectedItems().filter { !it.isVideo }
                    showExportFormatDialog(images, false)
                    true
                }
                R.id.action_delete_selected -> { showMultiDeleteConfirm(); true }
                R.id.action_cancel_selection -> { exitSelectionMode(); true }
                else -> false
            }
        }
        adapter.enterSelectionMode { count ->
            binding.toolbar.title = getString(R.string.selected_count, count)
        }
    }

    private fun updateSelectionTitle() {
        binding.toolbar.title = getString(R.string.selected_count, adapter.getSelectedCount())
    }

    private fun exitSelectionMode() {
        inSelectionMode = false
        backCallback.isEnabled = false
        adapter.exitSelectionMode()
        binding.toolbar.menu.clear()
        binding.toolbar.inflateMenu(R.menu.menu_album_detail)
        setupNormalMenuListeners()
        binding.toolbar.title = "Album Details"
    }

    private fun showMultiDeleteConfirm() {
        val selected = adapter.getSelectedItems()
        if (selected.isEmpty()) return
        MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setTitle(getString(R.string.delete_selected_title))
            .setMessage("Delete ${selected.size} items from this album and the library?")
            .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    selected.forEach { entity ->
                        container.albumRepository.removeFromAlbum(albumId, entity.id)
                        container.mediaRepository.delete(entity)
                    }
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

        val moveAdapter = MoveToAlbumAdapter { btnMove.isEnabled = true }
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = moveAdapter

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setView(dialogView).create()

        viewLifecycleOwner.lifecycleScope.launch {
            val albums = container.albumRepository.observeAlbums().first()
            moveAdapter.submitList(albums, currentAlbumId = albumId)
        }

        btnMove.isEnabled = false
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnMove.setOnClickListener {
            val targetAlbumId = moveAdapter.getSelectedAlbumId()
            viewLifecycleOwner.lifecycleScope.launch {
                if (targetAlbumId == null) {
                    container.albumRepository.moveToNoAlbum(albumId, selectedIds.toList())
                } else {
                    container.albumRepository.moveToAlbum(albumId, targetAlbumId, selectedIds.toList())
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
        radioPng?.setOnCheckedChangeListener { _, checked -> qualityRow?.visibility = if (checked) View.GONE else View.VISIBLE }
        radioJpg?.setOnCheckedChangeListener { _, checked -> qualityRow?.visibility = if (checked) View.VISIBLE else View.GONE }
        seekbarQuality?.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) { qualityValue?.text = "${progress + 10}%" }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon).setView(dialogView).create()
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

    private fun showEditAlbumDialog() {
        val awm = viewModel.albumWithMedia.value ?: return
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_new_album, null)
        val inputName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.album_name_input)
        val inputDesc = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.description_input)
        val btnCancel = dialogView.findViewById<View>(R.id.btn_cancel)
        val btnCreate = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_create)

        val titleText = (dialogView as? ViewGroup)?.let { findFirstTextView(it) }
        titleText?.text = getString(R.string.edit_album)
        inputName.setText(awm.album.name)
        inputDesc.setText(awm.album.description)
        btnCreate.text = "Save"

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon).setView(dialogView).create()
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnCreate.setOnClickListener {
            val name = inputName.text?.toString().orEmpty().trim()
            val desc = inputDesc.text?.toString().orEmpty().trim()
            if (name.isNotBlank()) {
                viewModel.updateAlbum(name, desc)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showFilterDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_new_album, null)
        val inputFilter = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.album_name_input)
        val inputDesc = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.description_input)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel)
        val btnApply = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_create)

        val titleText = (dialogView as? ViewGroup)?.let { findFirstTextView(it) }
        titleText?.text = "Filter Media"
        inputFilter.hint = null
        inputFilter.setText(viewModel.filter.value)
        (inputFilter.parent?.parent as? com.google.android.material.textfield.TextInputLayout)?.hint = "Anything from the album"
        (inputFilter.parent as? com.google.android.material.textfield.TextInputLayout)?.hint = "Anything from the album"
        inputDesc?.visibility = View.GONE
        (inputDesc?.parent as? View)?.visibility = View.GONE
        (inputDesc?.parent?.parent as? View)?.let { grandParent ->
            if (grandParent !is ViewGroup || (grandParent.id != dialogView.id)) {
                grandParent.visibility = View.GONE
            }
        }
        btnCancel.text = "Clear"
        btnApply.text = "Apply"

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon).setView(dialogView).create()
        btnCancel.setOnClickListener { viewModel.setFilter(""); dialog.dismiss() }
        btnApply.setOnClickListener { viewModel.setFilter(inputFilter.text?.toString().orEmpty()); dialog.dismiss() }
        dialog.show()
    }

    private fun showDeleteAlbumConfirm() {
        MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setTitle("Delete Album")
            .setMessage("Are you sure you want to delete this album? Media files will NOT be deleted.")
            .setPositiveButton("Delete") { _, _ ->
                Toast.makeText(requireContext(), "Deleting album...", Toast.LENGTH_SHORT).show()
                viewModel.deleteAlbum { findNavController().popBackStack() }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddMediaDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_to_album, null)
        val recycler = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_selection)
        val btnAdd = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_add)
        val btnCancel = dialogView.findViewById<View>(R.id.btn_cancel)

        val selectionAdapter = MediaLibraryAdapter(container, gridMode = true, onItemClick = { _ -> })
        recycler.layoutManager = GridLayoutManager(requireContext(), 3)
        recycler.adapter = selectionAdapter
        selectionAdapter.enterSelectionMode { count ->
            btnAdd.text = getString(R.string.add_count_format, count)
            btnAdd.isEnabled = count > 0
        }
        btnAdd.isEnabled = false

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon).setView(dialogView).create()
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allMedia.collect { list -> selectionAdapter.submitList(list) }
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnAdd.setOnClickListener {
            val selected = selectionAdapter.getSelectedItems().map { it.id }
            viewModel.addToAlbum(selected)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun findFirstTextView(viewGroup: ViewGroup): TextView? {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is TextView && child !is com.google.android.material.button.MaterialButton && child !is com.google.android.material.textfield.TextInputEditText) {
                return child
            } else if (child is ViewGroup) {
                val found = findFirstTextView(child)
                if (found != null) return found
            }
        }
        return null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
