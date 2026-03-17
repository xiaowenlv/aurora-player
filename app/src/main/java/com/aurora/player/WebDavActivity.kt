package com.aurora.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.concurrent.TimeUnit

class WebDavActivity : AppCompatActivity() {

    private lateinit var etHost: EditText
    private lateinit var etPort: EditText
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var etPath: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnBack: Button
    private lateinit var recyclerView: RecyclerView

    private var baseUrl: String = ""
    private var credential: String = ""
    private val pathStack = ArrayDeque<String>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class WebDavItem(val name: String, val href: String, val isDir: Boolean, val size: Long)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webdav)

        etHost = findViewById(R.id.et_host)
        etPort = findViewById(R.id.et_port)
        etUsername = findViewById(R.id.et_username)
        etPassword = findViewById(R.id.et_password)
        etPath = findViewById(R.id.et_path)
        btnConnect = findViewById(R.id.btn_connect)
        btnBack = findViewById(R.id.btn_back)
        recyclerView = findViewById(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)

        btnConnect.setOnClickListener { connectWebDav() }
        btnBack.setOnClickListener {
            if (pathStack.isNotEmpty()) {
                val prev = pathStack.removeLast()
                browseDir(prev)
            } else {
                finish()
            }
        }
    }

    private fun connectWebDav() {
        val host = etHost.text.toString().trim()
        val port = etPort.text.toString().trim().ifEmpty { "80" }
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val path = etPath.text.toString().trim().let { if (it.isEmpty()) "/" else it }

        if (host.isEmpty()) {
            Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show()
            return
        }

        val scheme = if (port == "443") "https" else "http"
        baseUrl = "$scheme://$host:$port"
        credential = if (username.isNotEmpty()) Credentials.basic(username, password) else ""

        pathStack.clear()
        browseDir(path)
    }

    private fun browseDir(path: String) {
        btnConnect.isEnabled = false
        btnConnect.text = getString(R.string.loading)

        lifecycleScope.launch {
            try {
                val items = withContext(Dispatchers.IO) { propfind("$baseUrl$path") }
                btnConnect.isEnabled = true
                btnConnect.text = getString(R.string.connect)
                showItems(items, path)
            } catch (e: Exception) {
                btnConnect.isEnabled = true
                btnConnect.text = getString(R.string.connect)
                Toast.makeText(
                    this@WebDavActivity,
                    "${getString(R.string.error_webdav_failed)}：${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun propfind(url: String): List<WebDavItem> {
        val body = """<?xml version="1.0" encoding="utf-8"?>
<propfind xmlns="DAV:"><prop><displayname/><getcontentlength/><resourcetype/></prop></propfind>"""

        val requestBody = body.toRequestBody("application/xml".toMediaType())
        val reqBuilder = Request.Builder()
            .url(url)
            .method("PROPFIND", requestBody)
            .header("Depth", "1")
            .header("Content-Type", "application/xml")
        if (credential.isNotEmpty()) reqBuilder.header("Authorization", credential)

        val response = httpClient.newCall(reqBuilder.build()).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        val xml = response.body?.string() ?: throw Exception("空响应")
        return parseWebDavXml(xml, url)
    }

    private fun parseWebDavXml(xml: String, currentUrl: String): List<WebDavItem> {
        val items = mutableListOf<WebDavItem>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        var href = ""; var displayName = ""; var size = 0L; var isDir = false
        var inResponse = false; var tag = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name.lowercase()
                    if (tag == "response") { href = ""; displayName = ""; size = 0L; isDir = false; inResponse = true }
                    if (tag == "collection") isDir = true
                }
                XmlPullParser.TEXT -> {
                    if (!inResponse) { event = parser.next(); continue }
                    when (tag) {
                        "href" -> href = parser.text.trim()
                        "displayname" -> displayName = parser.text.trim()
                        "getcontentlength" -> size = parser.text.trim().toLongOrNull() ?: 0L
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.lowercase() == "response" && inResponse) {
                        inResponse = false
                        val name = displayName.ifEmpty { href.trimEnd('/').substringAfterLast('/') }
                        // 跳过当前目录自身
                        val currentPath = Uri.parse(currentUrl).path ?: ""
                        if (href.trimEnd('/') != currentPath.trimEnd('/')) {
                            items.add(WebDavItem(name, href, isDir, size))
                        }
                    }
                }
            }
            event = parser.next()
        }
        return items.sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
    }

    private fun showItems(items: List<WebDavItem>, currentPath: String) {
        recyclerView.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
                return object : RecyclerView.ViewHolder(view) {}
            }
            override fun getItemCount() = items.size
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val item = items[position]
                val tvName = holder.itemView.findViewById<TextView>(R.id.tv_item_name)
                val tvInfo = holder.itemView.findViewById<TextView>(R.id.tv_item_info)
                val typeLabel = if (item.isDir) "文件夹" else "文件"
                val sizeLabel = if (item.isDir) "" else "，${formatSize(item.size)}"
                tvName.text = item.name
                tvInfo.text = if (item.isDir) "文件夹" else formatSize(item.size)
                holder.itemView.contentDescription = "$typeLabel：${item.name}$sizeLabel，双击打开"
                holder.itemView.setOnClickListener {
                    if (item.isDir) {
                        pathStack.addLast(currentPath)
                        browseDir(item.href)
                    } else {
                        val uri = Uri.parse("$baseUrl${item.href}")
                        startActivity(Intent(this@WebDavActivity, PlayerActivity::class.java).apply {
                            putExtra(PlayerActivity.EXTRA_URI, uri.toString())
                        })
                    }
                }
            }
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }

    override fun onDestroy() {
        super.onDestroy()
        httpClient.dispatcher.executorService.shutdown()
    }
}
