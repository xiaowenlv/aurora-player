package com.aurora.player

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var btnLocalFiles: Button
    private lateinit var btnWebDav: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvCurrentPath: TextView
    private lateinit var btnBack: Button

    private var currentPath: File = Environment.getExternalStorageDirectory()
    private val pathStack = ArrayDeque<File>()

    private lateinit var controllerFuture: ListenableFuture<MediaController>

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnLocalFiles = findViewById(R.id.btn_local_files)
        btnWebDav = findViewById(R.id.btn_webdav)
        recyclerView = findViewById(R.id.recycler_view)
        tvEmpty = findViewById(R.id.tv_empty)
        tvCurrentPath = findViewById(R.id.tv_current_path)
        btnBack = findViewById(R.id.btn_back)

        recyclerView.layoutManager = LinearLayoutManager(this)

        btnLocalFiles.setOnClickListener {
            checkPermissionAndBrowse()
        }

        btnWebDav.setOnClickListener {
            startActivity(Intent(this, WebDavActivity::class.java))
        }

        btnBack.setOnClickListener {
            navigateUp()
        }

        // 连接后台播放服务
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()

        // 处理外部打开文件的 Intent
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.data ?: return
            openPlayer(uri, null)
        }
    }

    private fun checkPermissionAndBrowse() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            browseDirectory(currentPath)
        } else {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                browseDirectory(currentPath)
            } else {
                Toast.makeText(this, getString(R.string.error_no_permission), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun browseDirectory(dir: File) {
        currentPath = dir
        tvCurrentPath.text = dir.absolutePath
        tvCurrentPath.contentDescription = "当前路径：${dir.absolutePath}"
        btnBack.visibility = if (pathStack.isEmpty()) View.GONE else View.VISIBLE

        val items = dir.listFiles()
            ?.filter { it.isDirectory || it.isMediaFile() }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()

        if (items.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.adapter = FileAdapter(items) { file ->
                if (file.isDirectory) {
                    pathStack.addLast(currentPath)
                    browseDirectory(file)
                } else {
                    openPlayer(Uri.fromFile(file), items.filter { it.isMediaFile() }.map { Uri.fromFile(it) })
                }
            }
        }
    }

    private fun navigateUp() {
        if (pathStack.isNotEmpty()) {
            browseDirectory(pathStack.removeLast())
        }
    }

    private fun File.isMediaFile(): Boolean {
        val ext = extension.lowercase()
        return ext in listOf(
            "mp3", "aac", "flac", "wav", "ogg", "m4a", "opus", "wma",
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp"
        )
    }

    private fun openPlayer(uri: Uri, playlist: List<Uri>?) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra(PlayerActivity.EXTRA_URI, uri.toString())
            if (playlist != null) {
                putStringArrayListExtra(
                    PlayerActivity.EXTRA_PLAYLIST,
                    ArrayList(playlist.map { it.toString() })
                )
            }
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        MediaController.releaseFuture(controllerFuture)
    }
}
