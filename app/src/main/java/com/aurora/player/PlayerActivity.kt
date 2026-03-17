package com.aurora.player

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URI = "extra_uri"
        const val EXTRA_PLAYLIST = "extra_playlist"
    }

    private lateinit var playerView: PlayerView
    private lateinit var player: ExoPlayer

    private lateinit var btnPlayPause: Button
    private lateinit var btnPrevious: Button
    private lateinit var btnNext: Button
    private lateinit var btnSeekBack: Button
    private lateinit var btnSeekForward: Button
    private lateinit var btnRepeat: Button
    private lateinit var btnShuffle: Button
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var tvTitle: TextView
    private lateinit var btnBack: Button

    private val updateHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            updateHandler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // 绑定控件
        playerView = findViewById(R.id.player_view)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnPrevious = findViewById(R.id.btn_previous)
        btnNext = findViewById(R.id.btn_next)
        btnSeekBack = findViewById(R.id.btn_seek_back)
        btnSeekForward = findViewById(R.id.btn_seek_forward)
        btnRepeat = findViewById(R.id.btn_repeat)
        btnShuffle = findViewById(R.id.btn_shuffle)
        seekBar = findViewById(R.id.seek_bar)
        tvCurrentTime = findViewById(R.id.tv_current_time)
        tvTotalTime = findViewById(R.id.tv_total_time)
        tvTitle = findViewById(R.id.tv_title)
        btnBack = findViewById(R.id.btn_back)

        // 初始化播放器
        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        // 隐藏 PlayerView 自带控件，用我们自己的无障碍控件
        playerView.useController = false

        // 加载媒体
        val uriStr = intent.getStringExtra(EXTRA_URI)
        val playlistStrs = intent.getStringArrayListExtra(EXTRA_PLAYLIST)

        if (uriStr != null) {
            val mediaItems = if (!playlistStrs.isNullOrEmpty()) {
                playlistStrs.map { MediaItem.fromUri(Uri.parse(it)) }
            } else {
                listOf(MediaItem.fromUri(Uri.parse(uriStr)))
            }
            player.setMediaItems(mediaItems)
            // 定位到当前文件
            val startIndex = playlistStrs?.indexOf(uriStr) ?: 0
            player.seekToDefaultPosition(if (startIndex >= 0) startIndex else 0)
            player.prepare()
            player.play()
        }

        // 播放器状态监听
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayPauseButton(isPlaying)
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateTitle()
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    updateDuration()
                }
            }
        })

        // 按钮点击事件（每个按钮都有 contentDescription，在 layout 里设置）
        btnPlayPause.setOnClickListener {
            if (player.isPlaying) player.pause() else player.play()
        }
        btnPrevious.setOnClickListener {
            if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
            else Toast.makeText(this, "已经是第一个了", Toast.LENGTH_SHORT).show()
        }
        btnNext.setOnClickListener {
            if (player.hasNextMediaItem()) player.seekToNextMediaItem()
            else Toast.makeText(this, "已经是最后一个了", Toast.LENGTH_SHORT).show()
        }
        btnSeekBack.setOnClickListener {
            player.seekTo((player.currentPosition - 10000).coerceAtLeast(0))
        }
        btnSeekForward.setOnClickListener {
            player.seekTo((player.currentPosition + 10000).coerceAtMost(player.duration))
        }
        btnRepeat.setOnClickListener { cycleRepeatMode() }
        btnShuffle.setOnClickListener { toggleShuffle() }
        btnBack.setOnClickListener { finish() }

        // 进度条拖动
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val pos = (progress.toLong() * player.duration / 100).coerceAtLeast(0)
                    tvCurrentTime.text = formatTime(pos)
                    tvCurrentTime.contentDescription = "当前位置 ${formatTime(pos)}"
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {
                val pos = (sb.progress.toLong() * player.duration / 100).coerceAtLeast(0)
                player.seekTo(pos)
            }
        })

        updateHandler.post(updateRunnable)
        updateTitle()
        updateRepeatButton()
        updateShuffleButton()
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        if (isPlaying) {
            btnPlayPause.text = getString(R.string.pause)
            btnPlayPause.contentDescription = getString(R.string.pause)
        } else {
            btnPlayPause.text = getString(R.string.play)
            btnPlayPause.contentDescription = getString(R.string.play)
        }
    }

    private fun updateTitle() {
        val item = player.currentMediaItem
        val title = item?.mediaMetadata?.title?.toString()
            ?: item?.localConfiguration?.uri?.lastPathSegment
            ?: "未知文件"
        tvTitle.text = title
        tvTitle.contentDescription = "正在播放：$title"
    }

    private fun updateDuration() {
        val duration = player.duration
        if (duration > 0) {
            tvTotalTime.text = formatTime(duration)
            tvTotalTime.contentDescription = "总时长 ${formatTime(duration)}"
        }
    }

    private fun updateProgress() {
        val duration = player.duration
        val position = player.currentPosition
        if (duration > 0) {
            seekBar.progress = (position * 100 / duration).toInt()
            tvCurrentTime.text = formatTime(position)
            tvCurrentTime.contentDescription = "当前位置 ${formatTime(position)}"
        }
    }

    private fun cycleRepeatMode() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        updateRepeatButton()
    }

    private fun updateRepeatButton() {
        val label = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> getString(R.string.repeat_one)
            Player.REPEAT_MODE_ALL -> getString(R.string.repeat_all)
            else -> getString(R.string.repeat_off)
        }
        btnRepeat.text = label
        btnRepeat.contentDescription = label
    }

    private fun toggleShuffle() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
        updateShuffleButton()
    }

    private fun updateShuffleButton() {
        val label = if (player.shuffleModeEnabled)
            getString(R.string.shuffle_on) else getString(R.string.shuffle_off)
        btnShuffle.text = label
        btnShuffle.contentDescription = label
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSec = ms / 1000
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    override fun onStop() {
        super.onStop()
        // 不释放播放器，保持后台播放
    }

    override fun onDestroy() {
        super.onDestroy()
        updateHandler.removeCallbacks(updateRunnable)
        player.release()
    }
}
