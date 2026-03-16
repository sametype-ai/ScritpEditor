package com.sametype.scripteditor.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sametype.scripteditor.adapter.ScriptSegmentAdapter
import com.sametype.scripteditor.databinding.DialogScriptInputBinding
import com.sametype.scripteditor.model.ScriptSegment
import com.sametype.scripteditor.viewmodel.SubtitleEditorViewModel

/**
 * Bottom sheet dialog for:
 * 1. Entering a full script text
 * 2. Splitting it into segments by line, sentence, or custom delimiter
 * 3. Reordering segments via drag-and-drop
 * 4. Placing segments onto the timeline at the current playback position
 */
class ScriptInputDialog : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "ScriptInputDialog"
        fun newInstance() = ScriptInputDialog()
    }

    private var _binding: DialogScriptInputBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SubtitleEditorViewModel by activityViewModels()
    private lateinit var segmentAdapter: ScriptSegmentAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogScriptInputBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Expand bottom sheet fully
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        setupSegmentList()
        setupButtons()
        loadExistingSegments()
    }

    private fun setupSegmentList() {
        segmentAdapter = ScriptSegmentAdapter(
            onSegmentEdit = { segment -> showEditSegmentDialog(segment) },
            onSegmentDelete = { segment ->
                val segments = segmentAdapter.currentSegments.toMutableList()
                segments.remove(segment)
                segmentAdapter.submitList(segments)
            },
            onPlaceOnTimeline = { segment ->
                viewModel.placeSegmentOnTimeline(segment.id, getCurrentFrame())
                Toast.makeText(requireContext(), "'${segment.text}' 타임라인에 추가됨", Toast.LENGTH_SHORT).show()
            }
        )

        binding.rvSegments.apply {
            adapter = segmentAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        // Drag-to-reorder
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                val segments = segmentAdapter.currentSegments.toMutableList()
                val item = segments.removeAt(from)
                segments.add(to, item)
                segmentAdapter.submitList(segments)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        touchHelper.attachToRecyclerView(binding.rvSegments)
    }

    private fun setupButtons() {
        binding.btnSplitByLine.setOnClickListener { splitScript("\n") }
        binding.btnSplitBySentence.setOnClickListener { splitScript(". ", keepDelimiter = true) }
        binding.btnSplitByCustom.setOnClickListener {
            val delimiter = binding.etCustomDelimiter.text.toString()
            if (delimiter.isBlank()) {
                Toast.makeText(requireContext(), "구분자를 입력하세요.", Toast.LENGTH_SHORT).show()
            } else {
                splitScript(delimiter)
            }
        }
        binding.btnSaveSegments.setOnClickListener {
            viewModel.setScriptSegments(segmentAdapter.currentSegments)
            dismiss()
        }
        binding.btnAddSegment.setOnClickListener {
            showEditSegmentDialog(null)
        }
    }

    private fun splitScript(delimiter: String, keepDelimiter: Boolean = false) {
        val fullText = binding.etFullScript.text.toString().trim()
        if (fullText.isBlank()) {
            Toast.makeText(requireContext(), "스크립트를 먼저 입력하세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val parts = if (keepDelimiter) {
            // Split by ". " but keep the period
            fullText.split(delimiter).filter { it.isNotBlank() }.map { it.trim() + "." }
        } else {
            fullText.split(delimiter).filter { it.isNotBlank() }.map { it.trim() }
        }

        val segments = parts.map { ScriptSegment(text = it) }
        segmentAdapter.submitList(segments)
        binding.tvSegmentCount.text = "세그먼트: ${segments.size}개"
    }

    private fun loadExistingSegments() {
        val existing = viewModel.scriptSegments.value.orEmpty()
        if (existing.isNotEmpty()) {
            segmentAdapter.submitList(existing)
            binding.tvSegmentCount.text = "세그먼트: ${existing.size}개"
        }
    }

    private fun showEditSegmentDialog(segment: ScriptSegment?) {
        val input = android.widget.EditText(requireContext()).apply {
            setText(segment?.text ?: "")
            hint = "세그먼트 텍스트"
            setPadding(32, 16, 32, 16)
        }

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(if (segment == null) "세그먼트 추가" else "세그먼트 수정")
            .setView(input)
            .setPositiveButton("확인") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    val segments = segmentAdapter.currentSegments.toMutableList()
                    if (segment == null) {
                        segments.add(ScriptSegment(text = text))
                    } else {
                        val idx = segments.indexOfFirst { it.id == segment.id }
                        if (idx >= 0) segments[idx] = segment.copy(text = text)
                    }
                    segmentAdapter.submitList(segments)
                    binding.tvSegmentCount.text = "세그먼트: ${segments.size}개"
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun getCurrentFrame(): Long {
        val posMs = viewModel.currentPositionMs.value ?: 0L
        val fps = viewModel.project.value?.fps ?: 30f
        return SubtitleEditorViewModel.run {
            com.sametype.scripteditor.model.SubtitleItem.msToFrame(posMs, fps)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
