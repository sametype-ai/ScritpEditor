package com.sametype.scripteditor.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.sametype.scripteditor.model.ScriptSegment
import com.sametype.scripteditor.model.SubtitleItem
import com.sametype.scripteditor.model.SubtitleProject

class SubtitleEditorViewModel : ViewModel() {

    private val _project = MutableLiveData<SubtitleProject?>()
    val project: LiveData<SubtitleProject?> = _project

    private val _subtitles = MutableLiveData<List<SubtitleItem>>(emptyList())
    val subtitles: LiveData<List<SubtitleItem>> = _subtitles

    private val _scriptSegments = MutableLiveData<List<ScriptSegment>>(emptyList())
    val scriptSegments: LiveData<List<ScriptSegment>> = _scriptSegments

    private val _selectedSubtitleId = MutableLiveData<String?>(null)
    val selectedSubtitleId: LiveData<String?> = _selectedSubtitleId

    private val _currentPositionMs = MutableLiveData<Long>(0L)
    val currentPositionMs: LiveData<Long> = _currentPositionMs

    // Undo/Redo history
    private val undoStack = ArrayDeque<List<SubtitleItem>>()
    private val redoStack = ArrayDeque<List<SubtitleItem>>()

    fun initProject(videoUri: String, durationMs: Long, fps: Float) {
        val proj = SubtitleProject(
            videoUri = videoUri,
            videoDurationMs = durationMs,
            fps = fps
        )
        _project.value = proj
        _subtitles.value = emptyList()
        _scriptSegments.value = emptyList()
    }

    fun updateCurrentPosition(posMs: Long) {
        _currentPositionMs.value = posMs
    }

    // ---- Subtitle management ----

    fun addSubtitle(subtitle: SubtitleItem) {
        saveUndoSnapshot()
        val current = _subtitles.value.orEmpty().toMutableList()
        current.add(subtitle)
        current.sortBy { it.startFrame }
        _subtitles.value = current
        syncToProject()
    }

    fun updateSubtitle(updated: SubtitleItem) {
        saveUndoSnapshot()
        val current = _subtitles.value.orEmpty().toMutableList()
        val idx = current.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            current[idx] = updated
            current.sortBy { it.startFrame }
            _subtitles.value = current
            syncToProject()
        }
    }

    fun deleteSubtitle(id: String) {
        saveUndoSnapshot()
        val current = _subtitles.value.orEmpty().toMutableList()
        current.removeAll { it.id == id }
        _subtitles.value = current
        if (_selectedSubtitleId.value == id) _selectedSubtitleId.value = null
        syncToProject()
    }

    fun selectSubtitle(id: String?) {
        _selectedSubtitleId.value = id
    }

    /**
     * Adjusts the start frame of a subtitle by [frameDelta] frames,
     * keeping a minimum duration of 1 frame.
     */
    fun adjustStartFrame(id: String, frameDelta: Long) {
        val subtitle = _subtitles.value?.find { it.id == id } ?: return
        val newStart = (subtitle.startFrame + frameDelta).coerceAtLeast(0L)
        if (newStart >= subtitle.endFrame - 1) return
        updateSubtitle(subtitle.copy(startFrame = newStart))
    }

    /**
     * Adjusts the end frame of a subtitle by [frameDelta] frames,
     * keeping a minimum duration of 1 frame.
     */
    fun adjustEndFrame(id: String, frameDelta: Long, totalFrames: Long) {
        val subtitle = _subtitles.value?.find { it.id == id } ?: return
        val newEnd = (subtitle.endFrame + frameDelta).coerceIn(subtitle.startFrame + 1, totalFrames)
        updateSubtitle(subtitle.copy(endFrame = newEnd))
    }

    /**
     * Moves a subtitle block without changing its duration (drag).
     */
    fun moveSubtitle(id: String, newStartFrame: Long, totalFrames: Long) {
        val subtitle = _subtitles.value?.find { it.id == id } ?: return
        val duration = subtitle.durationFrames
        val clampedStart = newStartFrame.coerceIn(0L, totalFrames - duration)
        updateSubtitle(subtitle.copy(
            startFrame = clampedStart,
            endFrame = clampedStart + duration
        ))
    }

    // ---- Script segment management ----

    fun setScriptSegments(segments: List<ScriptSegment>) {
        _scriptSegments.value = segments
        syncToProject()
    }

    /**
     * Places a script segment onto the timeline at the given frame.
     * Converts it to a SubtitleItem and marks the segment as placed.
     */
    fun placeSegmentOnTimeline(segmentId: String, startFrame: Long, defaultDurationFrames: Long = 90L) {
        val segment = _scriptSegments.value?.find { it.id == segmentId } ?: return
        val fps = _project.value?.fps ?: 30f
        val totalFrames = _project.value?.totalFrames ?: Long.MAX_VALUE
        val endFrame = (startFrame + defaultDurationFrames).coerceAtMost(totalFrames)

        val subtitle = SubtitleItem(
            text = segment.text,
            startFrame = startFrame,
            endFrame = endFrame,
            fps = fps
        )
        addSubtitle(subtitle)

        // Mark segment as placed
        val segments = _scriptSegments.value.orEmpty().toMutableList()
        val idx = segments.indexOfFirst { it.id == segmentId }
        if (idx >= 0) {
            segments[idx] = segments[idx].copy(isPlaced = true)
            _scriptSegments.value = segments
        }
    }

    fun getSelectedSubtitle(): SubtitleItem? {
        val id = _selectedSubtitleId.value ?: return null
        return _subtitles.value?.find { it.id == id }
    }

    fun getSubtitleAtPosition(posMs: Long): SubtitleItem? {
        return _subtitles.value?.find { it.startMs <= posMs && posMs < it.endMs }
    }

    // ---- Undo/Redo ----

    private fun saveUndoSnapshot() {
        undoStack.addLast(_subtitles.value.orEmpty().map { it.copy() })
        if (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(_subtitles.value.orEmpty().map { it.copy() })
        _subtitles.value = undoStack.removeLast()
        syncToProject()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(_subtitles.value.orEmpty().map { it.copy() })
        _subtitles.value = redoStack.removeLast()
        syncToProject()
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    private fun syncToProject() {
        _project.value?.let { proj ->
            proj.subtitles.clear()
            proj.subtitles.addAll(_subtitles.value.orEmpty())
            proj.scriptSegments.clear()
            proj.scriptSegments.addAll(_scriptSegments.value.orEmpty())
        }
    }
}
