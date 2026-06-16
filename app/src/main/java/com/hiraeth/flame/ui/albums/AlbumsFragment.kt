package com.hiraeth.flame.ui.albums

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.hiraeth.flame.R
import com.hiraeth.flame.databinding.FragmentAlbumsBinding
import kotlinx.coroutines.launch

class AlbumsFragment : Fragment() {

    private var _binding: FragmentAlbumsBinding? = null
    private val binding get() = _binding!!

    private val container get() =
        (requireActivity().application as com.hiraeth.flame.HiraethApplication).container

    private val viewModel: AlbumsViewModel by viewModels {
        AlbumsViewModel.factory(container.albumRepository)
    }

    private lateinit var adapter: AlbumsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAlbumsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = AlbumsAdapter(
            container = container,
        ) { id ->
            val b = Bundle().apply { putLong("albumId", id) }
            findNavController().navigate(R.id.action_albums_to_albumDetail, b)
        }
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter

        val navController = findNavController()
        val appBarConfig = AppBarConfiguration(
            setOf(R.id.libraryFragment, R.id.cameraFragment, R.id.albumsFragment),
        )
        binding.toolbar.setupWithNavController(navController, appBarConfig)

        binding.fabNewAlbum.setOnClickListener { 
            (activity as? com.hiraeth.flame.MainActivity)?.showCreateAlbumDialog()
        }

        binding.fabImportFolder.setOnClickListener {
            (activity as? com.hiraeth.flame.MainActivity)?.importFolderAsAlbum()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.albums.collect { adapter.submitList(it) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
