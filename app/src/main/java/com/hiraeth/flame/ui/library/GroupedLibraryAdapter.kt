package com.hiraeth.flame.ui.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.hiraeth.flame.data.db.MediaEntity
import com.hiraeth.flame.databinding.ItemLibraryHeaderBinding
import com.hiraeth.flame.databinding.ItemMediaGridBinding
import com.hiraeth.flame.databinding.ItemMediaListBinding
import com.hiraeth.flame.di.AppContainer

class GroupedLibraryAdapter(
    private val container: AppContainer,
    private var gridMode: Boolean,
    private val onItemClick: (Long) -> Unit,
    private val onItemLongClick: (Long) -> Unit,
    private val onHeaderClick: (String) -> Unit,
) : ListAdapter<LibraryDisplayItem, RecyclerView.ViewHolder>(DIFF) {

    private var selectionMode = false
    private val selectedIds = mutableSetOf<Long>()
    private var onSelectionChanged: ((Int) -> Unit)? = null

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_GRID = 1
        private const val TYPE_LIST = 2

        private val DIFF = object : DiffUtil.ItemCallback<LibraryDisplayItem>() {
            override fun areItemsTheSame(old: LibraryDisplayItem, new: LibraryDisplayItem): Boolean {
                return if (old is LibraryDisplayItem.Header && new is LibraryDisplayItem.Header) {
                    old.id == new.id
                } else if (old is LibraryDisplayItem.Media && new is LibraryDisplayItem.Media) {
                    old.entity.id == new.entity.id
                } else false
            }

            override fun areContentsTheSame(old: LibraryDisplayItem, new: LibraryDisplayItem): Boolean {
                return old == new
            }
        }
    }

    fun setGridMode(grid: Boolean) {
        if (gridMode != grid) {
            gridMode = grid
            notifyDataSetChanged()
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

    fun getSelectedItems(): List<MediaEntity> {
        return currentList.filterIsInstance<LibraryDisplayItem.Media>()
            .map { it.entity }
            .filter { it.id in selectedIds }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is LibraryDisplayItem.Header -> TYPE_HEADER
            is LibraryDisplayItem.Media -> if (gridMode) TYPE_GRID else TYPE_LIST
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderVH(ItemLibraryHeaderBinding.inflate(inflater, parent, false))
            TYPE_GRID -> GridVH(ItemMediaGridBinding.inflate(inflater, parent, false))
            else -> ListVH(ItemMediaListBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (item) {
            is LibraryDisplayItem.Header -> {
                val h = holder as HeaderVH
                h.binding.headerTitle.text = item.title
                h.binding.headerCount.text = "(${item.count})"
                h.itemView.setOnClickListener { onHeaderClick(item.id) }
            }
            is LibraryDisplayItem.Media -> {
                val entity = item.entity
                val file = container.mediaStorage.resolveRelative(entity.relativePath)
                val isSelected = selectedIds.contains(entity.id)

                if (holder is GridVH) {
                    holder.binding.thumbnail.load(file) { crossfade(300) }
                    holder.binding.title.text = entity.displayName
                    holder.binding.subtitle.text = if (entity.isVideo) "VISION" else "STILL"
                    holder.binding.root.alpha = if (selectionMode && !isSelected) 0.5f else 1.0f
                    holder.itemView.setOnClickListener {
                        if (selectionMode) toggleSelection(entity.id) else onItemClick(entity.id)
                    }
                    holder.itemView.setOnLongClickListener {
                        onItemLongClick(entity.id)
                        true
                    }
                } else if (holder is ListVH) {
                    holder.binding.thumbnail.load(file) { crossfade(300) }
                    holder.binding.title.text = entity.displayName
                    val sizeKb = entity.sizeBytes / 1024
                    holder.binding.subtitle.text = if (entity.isVideo) "Vision · $sizeKb KB" else "Still · $sizeKb KB"
                    holder.binding.root.alpha = if (selectionMode && !isSelected) 0.5f else 1.0f
                    holder.itemView.setOnClickListener {
                        if (selectionMode) toggleSelection(entity.id) else onItemClick(entity.id)
                    }
                    holder.itemView.setOnLongClickListener {
                        onItemLongClick(entity.id)
                        true
                    }
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

    class HeaderVH(val binding: ItemLibraryHeaderBinding) : RecyclerView.ViewHolder(binding.root)
    class GridVH(val binding: ItemMediaGridBinding) : RecyclerView.ViewHolder(binding.root)
    class ListVH(val binding: ItemMediaListBinding) : RecyclerView.ViewHolder(binding.root)
}
