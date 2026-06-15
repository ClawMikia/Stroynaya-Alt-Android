package com.hiraeth.flame.ui.albums

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hiraeth.flame.R
import com.hiraeth.flame.databinding.FragmentAlbumDetailBinding
import com.hiraeth.flame.ui.library.MediaLibraryAdapter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlbumDetailFragment : Fragment() {

    private var _binding: FragmentAlbumDetailBinding? = null
    private val binding get() = _binding!!

    private val container get() = (requireActivity().application as com.hiraeth.flame.HiraethApplication).container
    private val albumId: Long get() = requireArguments().getLong("albumId")

    private val viewModel: AlbumDetailViewModel by viewModels {
        AlbumDetailViewModel.factory(container.albumRepository, container.mediaRepository, albumId)
    }

    private lateinit var adapter: MediaLibraryAdapter

    private val createZipLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            viewModel.exportToZip(requireContext(), uri) { success ->
                if (success) {
                    Toast.makeText(requireContext(), "Album exported successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Export failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAlbumDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setupWithNavController(findNavController())

        adapter = MediaLibraryAdapter(container, gridMode = true) { id ->
            val b = Bundle().apply { 
                putLong("mediaId", id)
                putLong("albumId", albumId)
            }
            findNavController().navigate(R.id.action_albumDetail_to_detail, b)
        }

        binding.recyclerMedia.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.recyclerMedia.adapter = adapter

        binding.btnEditAlbum.setOnClickListener { showEditAlbumDialog() }
        binding.btnFilter.setOnClickListener { showFilterDialog() }
        binding.btnExport.setOnClickListener {
            val albumName = viewModel.albumWithMedia.value?.album?.name ?: "Album"
            createZipLauncher.launch("${albumName.replace(" ", "_")}.zip")
        }
        binding.btnAddDevice.setOnClickListener {
            showAddMediaDialog()
        }
        binding.btnDeleteAlbum.setOnClickListener {
            showDeleteAlbumConfirm()
        }

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
                launch {
                    viewModel.isExporting.collect { exporting ->
                        binding.btnExport.isEnabled = !exporting
                        if (exporting) {
                            Toast.makeText(requireContext(), "Zipping album...", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun showEditAlbumDialog() {
        val awm = viewModel.albumWithMedia.value ?: return
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_new_album, null)

        val inputName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.album_name_input)
        val inputDesc = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.description_input)
        val btnCancel = dialogView.findViewById<View>(R.id.btn_cancel)
        val btnCreate = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_create)

        val titleText = (dialogView as? ViewGroup)?.let { findFirstTextView(it) }
        titleText?.text = "Edit Album"

        inputName.setText(awm.album.name)
        inputDesc.setText(awm.album.description)
        btnCreate.text = "Save"

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setView(dialogView)
            .create()

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
            if (grandParent !is ViewGroup || grandParent.id != dialogView.id) {
                grandParent.visibility = View.GONE
            }
        }

        btnCancel.text = "Clear"
        btnApply.text = "Apply"

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener {
            viewModel.setFilter("")
            dialog.dismiss()
        }

        btnApply.setOnClickListener {
            viewModel.setFilter(inputFilter.text?.toString().orEmpty())
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDeleteAlbumConfirm() {
        MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setTitle("Delete Album")
            .setMessage("Are you sure you want to delete this album? Media files will NOT be deleted.")
            .setPositiveButton("Delete") { _, _ ->
                Toast.makeText(requireContext(), "Deleting album...", Toast.LENGTH_SHORT).show()
                viewModel.deleteAlbum {
                    findNavController().popBackStack()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddMediaDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_to_album, null)
        val recycler = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_selection)
        val btnAdd = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_add)
        val btnCancel = dialogView.findViewById<View>(R.id.btn_cancel)

        val selectionAdapter = MediaLibraryAdapter(container, gridMode = true) { _ -> }
        recycler.layoutManager = GridLayoutManager(requireContext(), 3)
        recycler.adapter = selectionAdapter

        selectionAdapter.enterSelectionMode { count ->
            btnAdd.text = "Add ($count)"
            btnAdd.isEnabled = count > 0
        }
        btnAdd.isEnabled = false

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.Dialog_Neon)
            .setView(dialogView)
            .create()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allMedia.collect { list ->
                selectionAdapter.submitList(list)
            }
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
