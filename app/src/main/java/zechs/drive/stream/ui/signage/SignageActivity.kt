package zechs.drive.stream.ui.signage

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import zechs.drive.stream.databinding.ActivitySignageBinding
import zechs.drive.stream.data.sync.DriveSyncManager
import zechs.drive.stream.data.sync.LocalMediaItem
import zechs.drive.stream.utils.AppSettings
import kotlin.math.min
import javax.inject.Inject

@AndroidEntryPoint
class SignageActivity : AppCompatActivity() {

    companion object {
        const val TAG = "SignageActivity"
        const val EXTRA_FOLDER_ID = "folderId"
        const val EXTRA_FOLDER_NAME = "folderName"
        private const val MAX_BACKOFF_SECONDS = 600L
    }

    @Inject
    lateinit var driveSyncManager: DriveSyncManager

    @Inject
    lateinit var appSettings: AppSettings

    private lateinit var binding: ActivitySignageBinding
    private lateinit var player: ExoPlayer

    private var playlist: List<LocalMediaItem> = emptyList()
    private var currentIndex = 0
    private var imageJob: Job? = null
    private var syncJob: Job? = null

    private var currentMediaId: String? = null
    private var currentMediaPath: String? = null

    private var imageDurationSeconds = AppSettings.DEFAULT_IMAGE_DURATION_SECONDS
    private var updateIntervalSeconds = AppSettings.DEFAULT_UPDATE_INTERVAL_SECONDS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySignageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        player = ExoPlayer.Builder(this).build()
        binding.playerView.player = player

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    advanceToNext()
                }
            }
        })

        val folderId = intent.getStringExtra(EXTRA_FOLDER_ID)
        if (folderId == null) {
            Log.w(TAG, "Missing folder ID")
            finish()
            return
        }

        title = intent.getStringExtra(EXTRA_FOLDER_NAME)
        startSyncLoop(folderId)
    }

    private fun startSyncLoop(folderId: String) {
        syncJob?.cancel()
        syncJob = lifecycleScope.launch {
            var backoffSeconds = updateIntervalSeconds.toLong()
            while (isActive) {
                try {
                    imageDurationSeconds = appSettings.fetchImageDurationSeconds().coerceIn(1, 3600)
                    updateIntervalSeconds = appSettings.fetchUpdateIntervalSeconds().coerceIn(10, 86400)
                    val result = driveSyncManager.syncFolder(folderId)
                    if (result.items.isEmpty()) {
                        showEmptyState()
                    } else if (result.didChange || playlist.isEmpty()) {
                        updatePlaylist(result.items)
                    }
                    backoffSeconds = updateIntervalSeconds.toLong()
                } catch (e: Exception) {
                    Log.w(TAG, "Sync failed: ${e.message}")
                    backoffSeconds = min(backoffSeconds * 2, MAX_BACKOFF_SECONDS)
                }
                delay(backoffSeconds * 1000L)
            }
        }
    }

    private fun updatePlaylist(newItems: List<LocalMediaItem>) {
        val previousId = currentMediaId
        val previousPath = currentMediaPath
        playlist = newItems
        val newIndex = previousId?.let { id ->
            playlist.indexOfFirst { it.id == id }
        } ?: -1
        currentIndex = if (newIndex >= 0) newIndex else 0

        val shouldRestart = newIndex == -1 ||
            previousPath == null ||
            playlist[currentIndex].localPath != previousPath

        if (shouldRestart) {
            playCurrent()
        }
    }

    private fun showEmptyState() {
        imageJob?.cancel()
        binding.emptyView.isVisible = true
        binding.playerView.isVisible = false
        binding.imageView.isVisible = false
        player.stop()
    }

    private fun playCurrent() {
        imageJob?.cancel()
        if (playlist.isEmpty()) {
            showEmptyState()
            return
        }

        val item = playlist[currentIndex]
        currentMediaId = item.id
        currentMediaPath = item.localPath

        if (item.isImage) {
            player.pause()
            binding.playerView.isVisible = false
            binding.imageView.isVisible = true
            binding.emptyView.isVisible = false
            Glide.with(this)
                .load(Uri.fromFile(java.io.File(item.localPath)))
                .into(binding.imageView)
            imageJob = lifecycleScope.launch {
                delay(imageDurationSeconds * 1000L)
                advanceToNext()
            }
        } else {
            binding.imageView.isVisible = false
            binding.playerView.isVisible = true
            binding.emptyView.isVisible = false
            player.repeatMode = if (playlist.size == 1) {
                Player.REPEAT_MODE_ONE
            } else {
                Player.REPEAT_MODE_OFF
            }
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(java.io.File(item.localPath))))
            player.prepare()
            player.play()
        }
    }

    private fun advanceToNext() {
        if (playlist.isEmpty()) {
            showEmptyState()
            return
        }
        currentIndex = (currentIndex + 1) % playlist.size
        playCurrent()
    }

    override fun onResume() {
        super.onResume()
        if (playlist.isNotEmpty()) {
            playCurrent()
        }
    }

    override fun onDestroy() {
        imageJob?.cancel()
        syncJob?.cancel()
        player.release()
        super.onDestroy()
    }
}
