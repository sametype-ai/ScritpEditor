package com.example.videoplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.videoplayer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var videoAdapter: VideoAdapter

    private val requiredPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            loadVideos()
        } else {
            showPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        checkPermissionAndLoad()
    }

    private fun setupRecyclerView() {
        videoAdapter = VideoAdapter { video ->
            val intent = Intent(this, VideoPlayerActivity::class.java).apply {
                putExtra(VideoPlayerActivity.EXTRA_VIDEO_URI, video.uri.toString())
                putExtra(VideoPlayerActivity.EXTRA_VIDEO_NAME, video.name)
            }
            startActivity(intent)
        }
        binding.rvVideos.adapter = videoAdapter
    }

    private fun checkPermissionAndLoad() {
        when {
            ContextCompat.checkSelfPermission(this, requiredPermission) == PackageManager.PERMISSION_GRANTED -> {
                loadVideos()
            }
            shouldShowRequestPermissionRationale(requiredPermission) -> {
                Toast.makeText(
                    this,
                    getString(R.string.permission_rationale),
                    Toast.LENGTH_LONG
                ).show()
                permissionLauncher.launch(requiredPermission)
            }
            else -> {
                permissionLauncher.launch(requiredPermission)
            }
        }
    }

    private fun loadVideos() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        val videos = GalleryVideoLoader.loadVideos(this)

        binding.progressBar.visibility = View.GONE

        if (videos.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvVideos.visibility = View.GONE
        } else {
            binding.rvVideos.visibility = View.VISIBLE
            videoAdapter.submitList(videos)
        }
    }

    private fun showPermissionDenied() {
        binding.progressBar.visibility = View.GONE
        binding.tvEmpty.apply {
            text = getString(R.string.permission_denied)
            visibility = View.VISIBLE
        }
        binding.rvVideos.visibility = View.GONE
    }
}
