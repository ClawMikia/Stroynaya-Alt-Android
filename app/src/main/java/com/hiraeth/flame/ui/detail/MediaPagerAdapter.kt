package com.hiraeth.flame.ui.detail

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.load
import com.hiraeth.flame.data.db.MediaEntity
import com.hiraeth.flame.databinding.ItemMediaPagerBinding
import com.hiraeth.flame.di.AppContainer

class MediaPagerAdapter(
    private val container: AppContainer
) : ListAdapter<MediaEntity, MediaPagerAdapter.MediaVH>(DIFF) {

    private var exoPlayer: ExoPlayer? = null

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MediaEntity>() {
            override fun areItemsTheSame(old: MediaEntity, new: MediaEntity) = old.id == new.id
            override fun areContentsTheSame(old: MediaEntity, new: MediaEntity) = old == new
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaVH {
        val binding = ItemMediaPagerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MediaVH(binding)
    }

    override fun onBindViewHolder(holder: MediaVH, position: Int) {
        val item = getItem(position)
        val file = container.mediaStorage.resolveRelative(item.relativePath)

        if (item.isVideo) {
            holder.binding.imageView.visibility = View.GONE
            holder.binding.playerView.visibility = View.VISIBLE
            // Player is assigned dynamically in playVideo() to save resources
        } else {
            holder.binding.playerView.visibility = View.GONE
            holder.binding.playerView.player = null
            holder.binding.imageView.visibility = View.VISIBLE
            holder.binding.imageView.load(file) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
            }
        }
    }

    fun playVideo(position: Int, recyclerView: RecyclerView) {
        val item = getItem(position)
        val holder = recyclerView.findViewHolderForAdapterPosition(position) as? MediaVH ?: return

        releasePlayer()

        if (item.isVideo) {
            val file = container.mediaStorage.resolveRelative(item.relativePath)
            val context = holder.itemView.context
            
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
                prepare()
                playWhenReady = true
                repeatMode = ExoPlayer.REPEAT_MODE_ONE
            }
            holder.binding.playerView.player = exoPlayer
        }
    }

    fun releasePlayer() {
        exoPlayer?.let {
            it.stop()
            it.release()
        }
        exoPlayer = null
    }

    class MediaVH(val binding: ItemMediaPagerBinding) : RecyclerView.ViewHolder(binding.root)
}
