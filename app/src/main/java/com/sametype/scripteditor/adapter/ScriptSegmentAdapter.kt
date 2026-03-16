package com.sametype.scripteditor.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sametype.scripteditor.databinding.ItemScriptSegmentBinding
import com.sametype.scripteditor.model.ScriptSegment

class ScriptSegmentAdapter(
    private val onSegmentEdit: (ScriptSegment) -> Unit,
    private val onSegmentDelete: (ScriptSegment) -> Unit,
    private val onPlaceOnTimeline: (ScriptSegment) -> Unit
) : ListAdapter<ScriptSegment, ScriptSegmentAdapter.ViewHolder>(DIFF_CALLBACK) {

    val currentSegments: List<ScriptSegment> get() = currentList

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScriptSegmentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemScriptSegmentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(segment: ScriptSegment) {
            binding.tvSegmentNumber.text = (adapterPosition + 1).toString()
            binding.tvSegmentText.text = segment.text

            if (segment.isPlaced) {
                binding.root.alpha = 0.5f
                binding.tvPlacedBadge.visibility = android.view.View.VISIBLE
            } else {
                binding.root.alpha = 1.0f
                binding.tvPlacedBadge.visibility = android.view.View.GONE
            }

            binding.btnEdit.setOnClickListener { onSegmentEdit(segment) }
            binding.btnDelete.setOnClickListener { onSegmentDelete(segment) }
            binding.btnPlace.setOnClickListener { onPlaceOnTimeline(segment) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ScriptSegment>() {
            override fun areItemsTheSame(old: ScriptSegment, new: ScriptSegment) = old.id == new.id
            override fun areContentsTheSame(old: ScriptSegment, new: ScriptSegment) = old == new
        }
    }
}
