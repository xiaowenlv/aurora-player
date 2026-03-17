package com.aurora.player

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thegrizzlylabs.sardineandroid.DavResource
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import android.content.Intent

class WebDavActivity : AppCompatActivity() {

    private lateinit var etHost: EditText
    private lateinit var etPort: EditText
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var etPath: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnBack: Button
    private lateinit var recyclerView: RecyclerView

    private var sardine: OkHttpSardine? = null
    private var baseUrl: String = ""

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
        btnBack.setOnClickListener { finish() }
    }

    private fun connectWebDav() {
        val host = etHost.text.toString().trim()
        val port = etPort.text.toString().trim().ifEmpty { "80" }
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val path = etPath.text.toString().trim().ifEmpty { "/" }

        if (host.isEmpty()) {
            Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show()
            etHost.requestFocus()
            return
        }

        baseUrl = "http://$host:$port"
        val fullUrl = "$baseUrl$path"

        btnConnect.isEnabled = false
        btnConnect.text = getString(R.string.loading)

        lifecycleScope.launch {
            try {
                val resources = withContext(Dispatchers.IO) {
                    val s = OkHttpSardine()
                    if (username.isNotEmpty()) s.setCredentials(username, password)
                    sardine = s
                    s.list(fullUrl)
                }
                btnConnect.isEnabled = true
                btnConnect.text = getString(R.string.connect)
                showResources(resources, fullUrl)
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

    private fun showResources(resources: List<DavResource>, currentUrl: String) {
        val items = resources.filter { !it.isDirectory || it.href.toString() != Uri.parse(currentUrl).path }
        recyclerView.adapter = WebDavAdapter(items) { resource ->
            if (resource.isDirectory) {
                browseWebDavDir("$baseUrl${resource.href}")
            } else {
                val uri = Uri.parse("$baseUrl${resource.href}")
                val intent = Intent(this, PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_URI, uri.toString())
                }
                startActivity(intent)
            }
        }
    }

    private fun browseWebDavDir(url: String) {
        lifecycleScope.launch {
            try {
                val resources = withContext(Dispatchers.IO) {
                    sardine?.list(url) ?: emptyList()
                }
                showResources(resources, url)
            } catch (e: Exception) {
                Toast.makeText(this@WebDavActivity, "加载失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
