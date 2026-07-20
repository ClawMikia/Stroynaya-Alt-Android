package com.hiraeth.flame.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.hiraeth.flame.R
import com.hiraeth.flame.data.db.MediaEntity
import com.hiraeth.flame.databinding.ItemMediaGridBinding
import com.hiraeth.flame.databinding.ItemMediaListBinding
import com.hiraeth.flame.di.AppContainer

class MediaLibraryAdapter(
    private val container: AppContainer,
    private var gridMode: Boolean,
    private val onItemClick: (Long) -> Unit,
    private val onItemLongClick: ((Long) -> Unit)? = null,
) : ListAdapter<MediaEntity, RecyclerView.ViewHolder>(DIFF) {

    private var selectionMode = false
    private val selectedIds = mutableSetOf<Long>()
    private var onSelectionChanged: ((Int) -> Unit)? = null

    companion object {
        private const val TYPE_GRID = 0
        private const val TYPE_LIST = 1

        private val DIFF = object : DiffUtil.ItemCallback<MediaEntity>() {
            override fun areItemsTheSame(old: MediaEntity, new: MediaEntity) = old.id == new.id
            override fun areContentsTheSame(old: MediaEntity, new: MediaEntity) = old == new
        }
    }

    fun enterSelectionMode(listener: (Int) -> Unit) {
        selectionMode = true
        selectedIds.clear()
        onSelectionChanged = listener
        notifyDataSetChanged()
    }

    fun exitSelectionMode() {
        selectionMode = false
        selectedIds.clear()
        onSelectionChanged = null
        notifyDataSetChanged()
    }

    fun selectAll() {
        val mediaIds = currentList.map { it.id }
        selectedIds.addAll(mediaIds)
        onSelectionChanged?.invoke(selectedIds.size)
        notifyDataSetChanged()
    }

    fun deselectAll() {
        selectedIds.clear()
        onSelectionChanged?.invoke(0)
        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<MediaEntity> {
        return currentList.filter { it.id in selectedIds }
    }

    fun getSelectedCount(): Int = selectedIds.size

    fun isInSelectionMode(): Boolean = selectionMode

    override fun getItemViewType(position: Int): Int =
        if (gridMode) TYPE_GRID else TYPE_LIST

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_GRID) {
            GridVH(ItemMediaGridBinding.inflate(inflater, parent, false))
        } else {
            ListVH(ItemMediaListBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        val file = container.mediaStorage.resolveRelative(item.relativePath)
        val isSelected = selectedIds.contains(item.id)

        when (holder) {
            is GridVH -> {
                holder.binding.thumbnail.load(file) { crossfade(300) }
                holder.binding.title.text = item.displayName
                holder.binding.subtitle.text = if (item.isVideo) "VISION" else "STILL"
                holder.binding.root.alpha = if (selectionMode && !isSelected) 0.5f else 1.0f
                if (selectionMode) {
                    holder.binding.checkbox.visibility = View.VISIBLE
                    holder.binding.checkbox.setImageResource(
                        if (isSelected) R.drawable.ic_checkbox_selected else R.drawable.ic_checkbox_unselected
                    )
                } else {
                    holder.binding.checkbox.visibility = View.GONE
                }
                holder.itemView.setOnClickListener {
                    if (selectionMode) {
                        toggleSelection(item.id)
                    } else {
                        onItemClick(item.id)
                    }
                }
                holder.itemView.setOnLongClickListener {
                    onItemLongClick?.invoke(item.id)
                    true
                }
            }
            is ListVH -> {
                holder.binding.thumbnail.load(file) { crossfade(300) }
                holder.binding.title.text = item.displayName
                val sizeKb = item.sizeBytes / 1024
                holder.binding.subtitle.text =
                    if (item.isVideo) "Vision · $sizeKb KB" else "Still · $sizeKb KB"
                holder.binding.root.alpha = if (selectionMode && !isSelected) 0.5f else 1.0f
                if (selectionMode) {
                    holder.binding.checkbox.visibility = View.VISIBLE
                    holder.binding.checkbox.setImageResource(
                        if (isSelected) R.drawable.ic_checkbox_selected else R.drawable.ic_checkbox_unselected
                    )
                } else {
                    holder.binding.checkbox.visibility = View.GONE
                }
                holder.itemView.setOnClickListener {
                    if (selectionMode) {
                        toggleSelection(item.id)
                    } else {
                        onItemClick(item.id)
                    }
                }
                holder.itemView.setOnLongClickListener {
                    onItemLongClick?.invoke(item.id)
                    true
                }
            }
        }
    }

    private fun toggleSelection(id: Long) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
        onSelectionChanged?.invoke(selectedIds.size)
        notifyDataSetChanged()
    }

    class GridVH(val binding: ItemMediaGridBinding) : RecyclerView.ViewHolder(binding.root)
    class ListVH(val binding: ItemMediaListBinding) : RecyclerView.ViewHolder(binding.root)
}
