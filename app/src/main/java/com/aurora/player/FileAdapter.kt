package com.aurora.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class FileAdapter(
    private val items: List<File>,
    private val onClick: (File) -> Unit
) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_item_name)
        val tvInfo: TextView = view.findViewById(R.id.tv_item_info)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = items[position]
        val isDir = file.isDirectory
        val typeLabel = if (isDir) "文件夹" else "文件"
        val sizeLabel = if (isDir) "" else "，大小 ${formatSize(file.length())}"

        holder.tvName.text = file.name
        holder.tvInfo.text = if (isDir) "文件夹" else formatSize(file.length())

        // 无障碍描述：类型 + 名称 + 大小
        holder.itemView.contentDescription = "$typeLabel：${file.name}$sizeLabel，双击打开"
        holder.itemView.setOnClickListener { onClick(file) }
    }

    override fun getItemCount() = items.size

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}
