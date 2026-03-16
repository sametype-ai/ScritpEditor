package com.sametype.scripteditor.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import com.sametype.scripteditor.R
import com.sametype.scripteditor.model.SubtitleItem
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Custom timeline view that displays subtitle blocks.
 *
 * Features:
 * - Shows subtitle blocks as colored rectangles on a time axis
 * - Drag subtitle blocks left/right to change start time
 * - Drag left/right edges to resize (change start/end frames)
 * - Pinch-zoom to change zoom level
 * - Tap to select a subtitle
 * - Shows current playback position as a line
 */
class TimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ---- Listener interface ----
    interface TimelineListener {
        fun onSubtitleSelected(id: String?)
        fun onSubtitleMoved(id: String, newStartFrame: Long)
        fun onSubtitleStartResized(id: String, newStartFrame: Long)
        fun onSubtitleEndResized(id: String, newEndFrame: Long)
        fun onTimelineSeeked(framePosition: Long)
        fun onSubtitleDropRequested(x: Float, y: Float): Boolean
    }

    var listener: TimelineListener? = null

    // ---- Data ----
    private var subtitles: List<SubtitleItem> = emptyList()
    private var totalFrames: Long = 1800L   // 60s * 30fps default
    private var fps: Float = 30f
    private var currentFrame: Long = 0L
    private var selectedId: String? = null

    // ---- Zoom / Scroll ----
    /** How many pixels represent one frame at current zoom */
    private var pixelsPerFrame: Float = 4f
    private var scrollOffsetX: Float = 0f
    private val minPixelsPerFrame = 0.5f
    private val maxPixelsPerFrame = 20f

    // ---- Drag state ----
    private enum class DragMode { NONE, MOVE, RESIZE_START, RESIZE_END }
    private var dragMode = DragMode.NONE
    private var dragId: String? = null
    private var dragStartX: Float = 0f
    private var dragStartFrame: Long = 0L
    private var dragEndFrame: Long = 0L
    private val handleWidthPx = 18f

    // ---- Layout constants ----
    private val trackHeight = 56f.dp
    private val trackTop = 36f.dp
    private val rulerHeight = 32f.dp
    private val textPadding = 6f.dp
    private val cornerRadius = 6f.dp

    // ---- Colors / Paints ----
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BB4E9AF8")
        style = Paint.Style.FILL
    }
    private val subtitleSelectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DD2979FF")
        style = Paint.Style.FILL
    }
    private val subtitleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f.sp
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
    }
    private val rulerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF424242")
        style = Paint.Style.FILL
    }
    private val rulerTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFBDBDBD")
        strokeWidth = 1f
    }
    private val rulerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFE0E0E0")
        textSize = 10f.sp
        textAlign = Paint.Align.CENTER
    }
    private val playheadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFF5252")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val playheadCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFF5252")
        style = Paint.Style.FILL
    }
    private val bgPaint = Paint().apply {
        color = Color.parseColor("#FF212121")
        style = Paint.Style.FILL
    }

    // ---- Gesture detectors ----
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            handleTap(e.x, e.y)
            return true
        }

        override fun onScroll(
            e1: MotionEvent?, e2: MotionEvent,
            distanceX: Float, distanceY: Float
        ): Boolean {
            if (dragMode == DragMode.NONE) {
                scrollOffsetX = (scrollOffsetX - distanceX)
                    .coerceIn(-(totalFrames * pixelsPerFrame - width + 40f), 40f)
                invalidate()
            }
            return true
        }
    })

    private val scaleGestureDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val focusX = detector.focusX
                val oldPpf = pixelsPerFrame
                pixelsPerFrame = (pixelsPerFrame * detector.scaleFactor)
                    .coerceIn(minPixelsPerFrame, maxPixelsPerFrame)
                // Keep the focus point stable during zoom
                val ratio = pixelsPerFrame / oldPpf
                scrollOffsetX = focusX - (focusX - scrollOffsetX) * ratio
                scrollOffsetX = scrollOffsetX.coerceIn(
                    -(totalFrames * pixelsPerFrame - width + 40f), 40f
                )
                invalidate()
                return true
            }
        })

    // ---- Public API ----

    fun setSubtitles(items: List<SubtitleItem>) {
        subtitles = items.sortedBy { it.startFrame }
        invalidate()
    }

    fun setTotalFrames(frames: Long, fps: Float) {
        totalFrames = max(frames, 1L)
        this.fps = fps
        invalidate()
    }

    fun setCurrentFrame(frame: Long) {
        currentFrame = frame
        // Auto-scroll to keep playhead visible
        val playheadX = frameToX(frame)
        if (playheadX < 20f || playheadX > width - 20f) {
            scrollOffsetX = -(frame * pixelsPerFrame - width / 2f)
                .coerceIn(-(totalFrames * pixelsPerFrame - width + 40f), 0f)
        }
        invalidate()
    }

    fun setSelectedId(id: String?) {
        selectedId = id
        invalidate()
    }

    // ---- Drawing ----

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        drawRuler(canvas)
        drawSubtitles(canvas)
        drawPlayhead(canvas)
    }

    private fun drawRuler(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), rulerHeight, rulerPaint)

        // Calculate tick interval (aim for ~80px between major ticks)
        val framesPerPixel = 1f / pixelsPerFrame
        val targetFramesBetweenTicks = (80f * framesPerPixel).toLong().coerceAtLeast(1L)
        val majorTickInterval = roundToNiceInterval(targetFramesBetweenTicks)
        val minorTickInterval = (majorTickInterval / 5).coerceAtLeast(1L)

        val firstVisibleFrame = xToFrame(0f)
        val lastVisibleFrame = xToFrame(width.toFloat())

        // Minor ticks
        var frame = (firstVisibleFrame / minorTickInterval) * minorTickInterval
        while (frame <= lastVisibleFrame) {
            val x = frameToX(frame)
            if (frame % majorTickInterval == 0L) {
                canvas.drawLine(x, rulerHeight - 14f.dp, x, rulerHeight, rulerTickPaint)
                val timeText = formatFrameTime(frame)
                canvas.drawText(timeText, x, rulerHeight - 16f.dp, rulerTextPaint)
            } else {
                canvas.drawLine(x, rulerHeight - 6f.dp, x, rulerHeight, rulerTickPaint)
            }
            frame += minorTickInterval
        }
    }

    private fun drawSubtitles(canvas: Canvas) {
        for (subtitle in subtitles) {
            val left = frameToX(subtitle.startFrame)
            val right = frameToX(subtitle.endFrame)
            val top = trackTop
            val bottom = trackTop + trackHeight
            val rect = RectF(left, top, right, bottom)

            if (right < 0f || left > width.toFloat()) continue

            val isSelected = subtitle.id == selectedId
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius,
                if (isSelected) subtitleSelectedPaint else subtitlePaint)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, subtitleStrokePaint)

            // Draw resize handles
            val handleW = min(handleWidthPx, (right - left) / 3f)
            if (isSelected) {
                // Left handle
                canvas.drawRoundRect(
                    RectF(left, top + 4f.dp, left + handleW, bottom - 4f.dp),
                    4f, 4f, handlePaint
                )
                // Right handle
                canvas.drawRoundRect(
                    RectF(right - handleW, top + 4f.dp, right, bottom - 4f.dp),
                    4f, 4f, handlePaint
                )
            }

            // Clip text to block width
            val textWidth = right - left - textPadding * 2 - (if (isSelected) handleW * 2 else 0f)
            if (textWidth > 10f) {
                canvas.save()
                canvas.clipRect(left + textPadding, top, right - textPadding, bottom)
                val textX = left + textPadding + (if (isSelected) handleW else 0f)
                val textY = (top + bottom) / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(subtitle.text, textX, textY, textPaint)
                canvas.restore()
            }
        }
    }

    private fun drawPlayhead(canvas: Canvas) {
        val x = frameToX(currentFrame)
        canvas.drawLine(x, 0f, x, height.toFloat(), playheadPaint)
        canvas.drawCircle(x, rulerHeight / 2f, 6f, playheadCirclePaint)
    }

    // ---- Touch handling ----

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!scaleGestureDetector.isInProgress) {
                    onDragStart(event.x, event.y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleGestureDetector.isInProgress && dragMode != DragMode.NONE) {
                    onDragMove(event.x)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragMode == DragMode.NONE) {
                    gestureDetector.onTouchEvent(event)
                }
                onDragEnd()
            }
            else -> {}
        }

        if (dragMode == DragMode.NONE) {
            gestureDetector.onTouchEvent(event)
        }
        return true
    }

    private fun onDragStart(x: Float, y: Float) {
        if (y < rulerHeight) {
            // Seek by tapping ruler
            val frame = xToFrame(x).coerceIn(0L, totalFrames)
            listener?.onTimelineSeeked(frame)
            return
        }

        for (subtitle in subtitles.reversed()) {
            val left = frameToX(subtitle.startFrame)
            val right = frameToX(subtitle.endFrame)
            val top = trackTop
            val bottom = trackTop + trackHeight

            if (x < left || x > right || y < top || y > bottom) continue

            val handleW = min(handleWidthPx, (right - left) / 3f)

            dragId = subtitle.id
            dragStartX = x
            dragStartFrame = subtitle.startFrame
            dragEndFrame = subtitle.endFrame

            dragMode = when {
                x <= left + handleW -> DragMode.RESIZE_START
                x >= right - handleW -> DragMode.RESIZE_END
                else -> DragMode.MOVE
            }
            selectedId = subtitle.id
            listener?.onSubtitleSelected(subtitle.id)
            invalidate()
            return
        }
        // Tapped on empty area
        selectedId = null
        listener?.onSubtitleSelected(null)
        invalidate()
    }

    private fun onDragMove(x: Float) {
        val id = dragId ?: return
        val frameDelta = ((x - dragStartX) / pixelsPerFrame).toLong()

        when (dragMode) {
            DragMode.MOVE -> {
                val newStart = (dragStartFrame + frameDelta).coerceIn(0L, totalFrames)
                listener?.onSubtitleMoved(id, newStart)
            }
            DragMode.RESIZE_START -> {
                val newStart = (dragStartFrame + frameDelta).coerceIn(0L, dragEndFrame - 1L)
                listener?.onSubtitleStartResized(id, newStart)
            }
            DragMode.RESIZE_END -> {
                val newEnd = (dragEndFrame + frameDelta).coerceIn(dragStartFrame + 1L, totalFrames)
                listener?.onSubtitleEndResized(id, newEnd)
            }
            else -> {}
        }
        invalidate()
    }

    private fun onDragEnd() {
        dragMode = DragMode.NONE
        dragId = null
    }

    private fun handleTap(x: Float, y: Float) {
        if (y < rulerHeight) {
            val frame = xToFrame(x).coerceIn(0L, totalFrames)
            listener?.onTimelineSeeked(frame)
        }
    }

    // ---- Coordinate helpers ----

    private fun frameToX(frame: Long): Float = scrollOffsetX + frame * pixelsPerFrame

    private fun xToFrame(x: Float): Long = ((x - scrollOffsetX) / pixelsPerFrame).toLong()

    // ---- Utility ----

    private fun roundToNiceInterval(frames: Long): Long {
        val candidates = listOf(1L, 5L, 10L, 15L, 30L, 60L, 90L, 150L, 300L, 450L, 600L, 900L, 1800L)
        return candidates.firstOrNull { it >= frames } ?: candidates.last()
    }

    private fun formatFrameTime(frame: Long): String {
        val totalSec = (frame / fps).toLong()
        val min = totalSec / 60
        val sec = totalSec % 60
        val fr = (frame % fps.toLong())
        return "%02d:%02d.%02d".format(min, sec, fr)
    }

    // ---- Extension helpers ----
    private val Float.dp: Float get() = this * context.resources.displayMetrics.density
    private val Float.sp: Float get() = this * context.resources.displayMetrics.scaledDensity
}
