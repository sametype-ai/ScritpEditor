package com.sametype.scripteditor.ui

import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.sametype.scripteditor.R
import com.sametype.scripteditor.databinding.ActivitySubtitleEditorBinding
import com.sametype.scripteditor.model.SubtitleItem
import com.sametype.scripteditor.ui.dialog.ScriptInputDialog
import com.sametype.scripteditor.viewmodel.SubtitleEditorViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SubtitleEditorActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        private const val DEFAULT_FPS = 30f
        private const val PLAYHEAD_UPDATE_INTERVAL_MS = 33L // ~30fps
    }

    private lateinit var binding: ActivitySubtitleEditorBinding
    private val viewModel: SubtitleEditorViewModel by viewModels()
    private var player: ExoPlayer? = null

    private var playheadJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private var fps = DEFAULT_FPS
    private var totalFrames = 1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubtitleEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val videoUriString = intent.getStringExtra(EXTRA_VIDEO_URI)
        if (videoUriString == null) {
            Toast.makeText(this, "동영상을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupPlayer(Uri.parse(videoUriString))
        setupTimelineView()
        setupButtons()
        observeViewModel()
    }

    // ---- Player setup ----

    private fun setupPlayer(videoUri: Uri) {
        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            binding.playerView.player = exoPlayer
            binding.playerView.useController = true

            val mediaItem = MediaItem.fromUri(videoUri)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()

            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        val durationMs = exoPlayer.duration.coerceAtLeast(1L)
                        fps = DEFAULT_FPS
                        totalFrames = SubtitleItem.msToFrame(durationMs, fps)

                        viewModel.initProject(videoUri.toString(), durationMs, fps)
                        binding.timelineView.setTotalFrames(totalFrames, fps)
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) startPlayheadUpdater() else stopPlayheadUpdater()
                }
            })
        }
    }

    private fun startPlayheadUpdater() {
        playheadJob?.cancel()
        playheadJob = coroutineScope.launch {
            while (true) {
                val posMs = player?.currentPosition ?: 0L
                val frame = SubtitleItem.msToFrame(posMs, fps)
                viewModel.updateCurrentPosition(posMs)
                binding.timelineView.setCurrentFrame(frame)
                updateSubtitleOverlay(posMs)
                delay(PLAYHEAD_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopPlayheadUpdater() {
        playheadJob?.cancel()
    }

    private fun updateSubtitleOverlay(posMs: Long) {
        val subtitle = viewModel.getSubtitleAtPosition(posMs)
        if (subtitle != null) {
            binding.tvSubtitleOverlay.text = subtitle.text
            binding.tvSubtitleOverlay.visibility = View.VISIBLE
        } else {
            binding.tvSubtitleOverlay.visibility = View.GONE
        }
    }

    // ---- Timeline setup ----

    private fun setupTimelineView() {
        binding.timelineView.listener = object : TimelineView.TimelineListener {
            override fun onSubtitleSelected(id: String?) {
                viewModel.selectSubtitle(id)
                updateSubtitleDetailPanel(id)
            }

            override fun onSubtitleMoved(id: String, newStartFrame: Long) {
                viewModel.moveSubtitle(id, newStartFrame, totalFrames)
            }

            override fun onSubtitleStartResized(id: String, newStartFrame: Long) {
                val subtitle = viewModel.subtitles.value?.find { it.id == id } ?: return
                viewModel.updateSubtitle(subtitle.copy(startFrame = newStartFrame))
            }

            override fun onSubtitleEndResized(id: String, newEndFrame: Long) {
                val subtitle = viewModel.subtitles.value?.find { it.id == id } ?: return
                viewModel.updateSubtitle(subtitle.copy(endFrame = newEndFrame))
            }

            override fun onTimelineSeeked(framePosition: Long) {
                val ms = SubtitleItem.frameToMs(framePosition, fps)
                player?.seekTo(ms)
                viewModel.updateCurrentPosition(ms)
                binding.timelineView.setCurrentFrame(framePosition)
                updateSubtitleOverlay(ms)
            }

            override fun onSubtitleDropRequested(x: Float, y: Float): Boolean = false
        }
    }

    // ---- Buttons ----

    private fun setupButtons() {
        // Frame-step buttons for selected subtitle start
        binding.btnStartFrameMinus.setOnClickListener {
            viewModel.selectedSubtitleId.value?.let { id ->
                viewModel.adjustStartFrame(id, -1L)
            }
        }
        binding.btnStartFramePlus.setOnClickListener {
            viewModel.selectedSubtitleId.value?.let { id ->
                viewModel.adjustStartFrame(id, +1L)
            }
        }

        // Frame-step buttons for selected subtitle end
        binding.btnEndFrameMinus.setOnClickListener {
            viewModel.selectedSubtitleId.value?.let { id ->
                viewModel.adjustEndFrame(id, -1L, totalFrames)
            }
        }
        binding.btnEndFramePlus.setOnClickListener {
            viewModel.selectedSubtitleId.value?.let { id ->
                viewModel.adjustEndFrame(id, +1L, totalFrames)
            }
        }

        // Add subtitle at current position
        binding.btnAddSubtitle.setOnClickListener {
            showAddSubtitleDialog()
        }

        // Delete selected subtitle
        binding.btnDeleteSubtitle.setOnClickListener {
            viewModel.selectedSubtitleId.value?.let { id ->
                viewModel.deleteSubtitle(id)
                updateSubtitleDetailPanel(null)
            }
        }

        // Open script manager
        binding.btnScript.setOnClickListener {
            showScriptDialog()
        }
    }

    private fun showAddSubtitleDialog() {
        val posMs = player?.currentPosition ?: 0L
        val startFrame = SubtitleItem.msToFrame(posMs, fps)
        val endFrame = (startFrame + 90L).coerceAtMost(totalFrames)

        val input = android.widget.EditText(this).apply {
            hint = "자막 텍스트를 입력하세요"
            setPadding(32, 16, 32, 16)
        }

        AlertDialog.Builder(this)
            .setTitle("자막 추가")
            .setView(input)
            .setPositiveButton("추가") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    val subtitle = SubtitleItem(
                        text = text,
                        startFrame = startFrame,
                        endFrame = endFrame,
                        fps = fps
                    )
                    viewModel.addSubtitle(subtitle)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showScriptDialog() {
        ScriptInputDialog.newInstance().show(supportFragmentManager, ScriptInputDialog.TAG)
    }

    // ---- Observe ViewModel ----

    private fun observeViewModel() {
        viewModel.subtitles.observe(this) { subtitles ->
            binding.timelineView.setSubtitles(subtitles)
            val posMs = player?.currentPosition ?: 0L
            updateSubtitleOverlay(posMs)
        }

        viewModel.selectedSubtitleId.observe(this) { id ->
            binding.timelineView.setSelectedId(id)
            updateSubtitleDetailPanel(id)
        }

        viewModel.currentPositionMs.observe(this) { posMs ->
            updateSubtitleOverlay(posMs)
        }
    }

    private fun updateSubtitleDetailPanel(id: String?) {
        val subtitle = id?.let { viewModel.subtitles.value?.find { s -> s.id == it } }
        if (subtitle != null) {
            binding.groupSubtitleDetail.visibility = View.VISIBLE
            binding.tvDetailText.text = subtitle.text
            binding.tvDetailStartFrame.text = "시작: ${subtitle.startFrame}f (${formatMs(subtitle.startMs)})"
            binding.tvDetailEndFrame.text = "끝: ${subtitle.endFrame}f (${formatMs(subtitle.endMs)})"
            binding.tvDetailDuration.text = "길이: ${subtitle.durationFrames}f (${formatMs(subtitle.durationMs)})"
        } else {
            binding.groupSubtitleDetail.visibility = View.GONE
        }
    }

    // ---- Menu ----

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_undo -> { viewModel.undo(); true }
            R.id.action_redo -> { viewModel.redo(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ---- Lifecycle ----

    override fun onResume() {
        super.onResume()
        if (player?.isPlaying == true) startPlayheadUpdater()
    }

    override fun onPause() {
        super.onPause()
        stopPlayheadUpdater()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayheadUpdater()
        player?.release()
        player = null
    }

    // ---- Helpers ----

    private fun formatMs(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        val millis = ms % 1000
        return "%02d:%02d.%03d".format(min, sec, millis)
    }
}
