package com.sametype.scripteditor.model

import java.util.UUID

/**
 * Represents a single subtitle segment with frame-level timing.
 *
 * @param id Unique identifier for this subtitle
 * @param text The subtitle text to display
 * @param startFrame The frame number where this subtitle begins
 * @param endFrame The frame number where this subtitle ends (exclusive)
 * @param fps Frames per second of the video (used to convert between frames and ms)
 */
data class SubtitleItem(
    val id: String = UUID.randomUUID().toString(),
    var text: String,
    var startFrame: Long,
    var endFrame: Long,
    val fps: Float = 30f
) {
    /** Duration in frames */
    val durationFrames: Long get() = endFrame - startFrame

    /** Start time in milliseconds */
    val startMs: Long get() = (startFrame * 1000L / fps).toLong()

    /** End time in milliseconds */
    val endMs: Long get() = (endFrame * 1000L / fps).toLong()

    /** Duration in milliseconds */
    val durationMs: Long get() = endMs - startMs

    companion object {
        /** Convert milliseconds to frame number */
        fun msToFrame(ms: Long, fps: Float): Long = (ms * fps / 1000L).toLong()

        /** Convert frame number to milliseconds */
        fun frameToMs(frame: Long, fps: Float): Long = (frame * 1000L / fps).toLong()
    }
}
