package com.sametype.scripteditor.model

import java.util.UUID

/**
 * A segment of the full script that has been split by the user.
 * Before it's placed on the timeline, it has no frame timing.
 */
data class ScriptSegment(
    val id: String = UUID.randomUUID().toString(),
    var text: String,
    var isPlaced: Boolean = false
)
