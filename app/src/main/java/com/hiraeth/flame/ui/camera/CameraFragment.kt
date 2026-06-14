package com.hiraeth.flame.ui.camera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.hiraeth.flame.R
import com.hiraeth.flame.databinding.FragmentCameraBinding
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private val container get() = (requireActivity().application as com.hiraeth.flame.HiraethApplication).container

    private val viewModel: CameraViewModel by viewModels {
        CameraViewModel.factory(container.mediaStorage)
    }

    private var imageCapture: ImageCapture? = null
    private lateinit var mainExecutor: Executor

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mainExecutor = ContextCompat.getMainExecutor(requireContext())

        val navController = findNavController()
        val appBarConfig = AppBarConfiguration(
            setOf(R.id.libraryFragment, R.id.cameraFragment, R.id.albumsFragment),
        )
        binding.toolbar.setupWithNavController(navController, appBarConfig)
        binding.toolbar.inflateMenu(R.menu.menu_camera)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_flip_camera) {
                viewModel.toggleLens()
                true
            } else {
                false
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.lensFacing.collect { bindCameraUseCases() }
                }
                launch {
                    viewModel.captureOrientation.collect {
                        bindCameraUseCases()
                    }
                }
                launch {
                    viewModel.lastMessage.collect { binding.cameraMessage.text = it.orEmpty() }
                }
            }
        }

        binding.btnPhoto.setOnClickListener {
            val capture = imageCapture ?: return@setOnClickListener
            val file = viewModel.createPhotoOutputFile()
            val opts = ImageCapture.OutputFileOptions.Builder(file).build()
            capture.takePicture(
                opts,
                mainExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        viewModel.clearMessage()
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val detailsDialog = CaptureDetailsDialogFragment.newInstance()
                        detailsDialog.setListeners(
                            onSaved = { typedTitle, typedDescription ->
                                viewLifecycleOwner.lifecycleScope.launch {
                                    try {
                                        val newMediaId = container.mediaRepository.registerCapturedPhoto(
                                            file,
                                            typedTitle,
                                            typedDescription
                                        )
                                        Toast.makeText(requireContext(), "Saved to Kingdom!", Toast.LENGTH_SHORT).show()

                                        findNavController().navigate(
                                            R.id.mediaDetailFragment,
                                            androidx.core.os.bundleOf(
                                                "mediaId" to newMediaId,
                                                "albumId" to -1L
                                            )
                                        )
                                    } catch (e: Exception) {
                                        Toast.makeText(requireContext(), "Capture failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onCancelled = {
                                if (file.exists()) file.delete()
                                Toast.makeText(requireContext(), "Capture discarded", Toast.LENGTH_SHORT).show()
                            }
                        )
                        detailsDialog.show(childFragmentManager, "capture_details_entry")
                    }
                },
            )
        }
    }

    private fun bindCameraUseCases() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                val b = _binding ?: return@addListener
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = b.previewView.surfaceProvider
                }
                val facing = viewModel.lensFacing.value
                val selector = CameraSelector.Builder().requireLensFacing(facing).build()
                val targetRotation = viewModel.getTargetRotation()

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setTargetRotation(targetRotation)
                    .build()

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        viewLifecycleOwner,
                        selector,
                        preview,
                        capture
                    )
                    imageCapture = capture
                } catch (_: Exception) {
                    imageCapture = null
                }
            },
            mainExecutor,
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        runCatching {
            ProcessCameraProvider.getInstance(requireContext()).get().unbindAll()
        }
        _binding = null
    }
}
