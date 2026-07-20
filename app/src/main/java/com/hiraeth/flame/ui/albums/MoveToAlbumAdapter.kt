package com.hiraeth.flame.ui.albums

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hiraeth.flame.R
import com.hiraeth.flame.data.db.AlbumWithMedia
import com.hiraeth.flame.databinding.ItemMoveAlbumBinding

class MoveToAlbumAdapter(
    private val onAlbumSelected: (Long?) -> Unit,
) : RecyclerView.Adapter<MoveToAlbumAdapter.VH>() {

    private val items = mutableListOf<AlbumItem>()
    private var selectedPosition: Int = -1

    sealed class AlbumItem {
        data class NoAlbum(val mediaCount: Int) : AlbumItem()
        data class Album(val album: AlbumWithMedia) : AlbumItem()
    }

    fun submitList(albums: List<AlbumWithMedia>, currentAlbumId: Long = -1L) {
        items.clear()
        items.add(AlbumItem.NoAlbum(0))
        albums.filter { it.album.id != currentAlbumId }.forEach { items.add(AlbumItem.Album(it)) }
        selectedPosition = -1
        notifyDataSetChanged()
    }

    fun getSelectedAlbumId(): Long? {
        return when (val item = items.getOrNull(selectedPosition)) {
            is AlbumItem.NoAlbum -> null
            is AlbumItem.Album -> item.album.album.id
            else -> null
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemMoveAlbumBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context

        when (item) {
            is AlbumItem.NoAlbum -> {
                holder.binding.albumName.text = ctx.getString(R.string.no_album)
                holder.binding.albumCount.text = "Unlink from all albums"
                holder.binding.thumbRow.removeAllViews()
            }
            is AlbumItem.Album -> {
                holder.binding.albumName.text = item.album.album.name
                val count = item.album.media.size
                holder.binding.albumCount.text = if (count == 1) "1 item" else "$count items"
            }
        }

        val isSelected = position == selectedPosition
        holder.itemView.setBackgroundColor(
            if (isSelected) ctx.getColor(R.color.bg_elevated) else ctx.getColor(R.color.bg_card)
        )

        holder.itemView.setOnClickListener {
            val old = selectedPosition
            selectedPosition = holder.adapterPosition
            if (old != -1) notifyItemChanged(old)
            notifyItemChanged(selectedPosition)
            onAlbumSelected(getSelectedAlbumId())
        }
    }

    class VH(val binding: ItemMoveAlbumBinding) : RecyclerView.ViewHolder(binding.root)
}
