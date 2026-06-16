package com.hiraeth.flame.ui.editor

import android.graphics.*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.hiraeth.flame.HiraethApplication
import com.hiraeth.flame.databinding.FragmentImageEditorBinding
import kotlinx.coroutines.launch

class ImageEditorFragment : Fragment() {

    private var _binding: FragmentImageEditorBinding? = null
    private val binding get() = _binding!!

    private val mediaId: Long get() = requireArguments().getLong("mediaId")

    private val viewModel: ImageEditorViewModel by viewModels {
        val container = (requireActivity().application as HiraethApplication).container
        ImageEditorViewModel.factory(container.mediaRepository, container.mediaStorage, mediaId)
    }

    private var currentBitmap: Bitmap? = null
    private var rotationDegrees = 0f
    private var brightnessValue = 0f

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentImageEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        binding.brightnessSlider.addOnChangeListener { _, value, _ ->
            brightnessValue = value
            updatePreview()
        }

        binding.btnRotate.setOnClickListener {
            rotationDegrees = (rotationDegrees + 90f) % 360f
            updatePreview()
        }

        binding.btnCrop.setOnClickListener {
            if (binding.cropOverlay.visibility == View.VISIBLE) {
                cropBitmap()?.let { cropped ->
                    currentBitmap = cropped
                    rotationDegrees = 0f
                    // We keep brightnessValue as it is applied via ColorFilter in updatePreview
                    binding.cropOverlay.visibility = View.GONE
                    updatePreview()
                }
            } else {
                binding.cropOverlay.visibility = View.VISIBLE
            }
        }

        binding.btnSave.setOnClickListener {
            getFinalBitmap()?.let { bitmapToSave ->
                viewModel.saveEditedBitmap(bitmapToSave) {
                    Toast.makeText(requireContext(), "Image saved", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.originalBitmap.collect { bitmap ->
                    if (bitmap != null && currentBitmap == null) {
                        currentBitmap = bitmap
                        updatePreview()
                    }
                }
            }
        }
    }

    private fun updatePreview() {
        val bitmap = currentBitmap ?: return
        
        // For preview, we can just use ImageView's rotation and ColorFilter for better performance
        binding.editorImageView.setImageBitmap(bitmap)
        binding.editorImageView.rotation = rotationDegrees
        
        val colorMatrix = ColorMatrix().apply {
            set(
                floatArrayOf(
                    1f, 0f, 0f, 0f, brightnessValue,
                    0f, 1f, 0f, 0f, brightnessValue,
                    0f, 0f, 1f, 0f, brightnessValue,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        }
        binding.editorImageView.colorFilter = ColorMatrixColorFilter(colorMatrix)
    }

    private fun cropBitmap(): Bitmap? {
        val bitmap = currentBitmap ?: return null
        
        // For crop, we first apply rotation because the crop overlay is relative to the VIEW
        // But it's easier to crop the rotated bitmap.
        val rotated = getRotatedBitmap(bitmap, rotationDegrees)
        
        val overlay = binding.cropOverlay
        val rect = overlay.cropRect
        
        val viewWidth = overlay.width.toFloat()
        val viewHeight = overlay.height.toFloat()
        
        val bitmapWidth = rotated.width.toFloat()
        val bitmapHeight = rotated.height.toFloat()
        
        val scale = kotlin.math.min(viewWidth / bitmapWidth, viewHeight / bitmapHeight)
        val dx = (viewWidth - bitmapWidth * scale) / 2f
        val dy = (viewHeight - bitmapHeight * scale) / 2f
        
        val left = (rect.left - dx) / scale
        val top = (rect.top - dy) / scale
        val right = (rect.right - dx) / scale
        val bottom = (rect.bottom - dy) / scale
        
        val cropX = Math.max(0, left.toInt())
        val cropY = Math.max(0, top.toInt())
        val cropW = Math.min(rotated.width - cropX, (right - left).toInt())
        val cropH = Math.min(rotated.height - cropY, (bottom - top).toInt())
        
        return if (cropW > 0 && cropH > 0) {
            Bitmap.createBitmap(rotated, cropX, cropY, cropW, cropH)
        } else {
            null
        }
    }

    private fun getRotatedBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun getFinalBitmap(): Bitmap? {
        val bitmap = currentBitmap ?: return null
        val rotated = getRotatedBitmap(bitmap, rotationDegrees)
        
        val result = Bitmap.createBitmap(rotated.width, rotated.height, rotated.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val colorMatrix = ColorMatrix().apply {
            set(
                floatArrayOf(
                    1f, 0f, 0f, 0f, brightnessValue,
                    0f, 1f, 0f, 0f, brightnessValue,
                    0f, 0f, 1f, 0f, brightnessValue,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        }
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(rotated, 0f, 0f, paint)
        
        return result
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
