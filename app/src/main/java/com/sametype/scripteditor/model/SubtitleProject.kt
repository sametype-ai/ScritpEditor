package com.sametype.scripteditor.model

/**
 * Container for the entire subtitle editing session.
 */
data class SubtitleProject(
    val videoUri: String,
    val videoDurationMs: Long,
    val fps: Float,
    val subtitles: MutableList<SubtitleItem> = mutableListOf(),
    val scriptSegments: MutableList<ScriptSegment> = mutableListOf()
) {
    val totalFrames: Long get() = SubtitleItem.msToFrame(videoDurationMs, fps)
}
