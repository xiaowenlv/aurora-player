package com.aurora.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thegrizzlylabs.sardineandroid.DavResource

class WebDavAdapter(
    private val items: List<DavResource>,
    private val onClick: (DavResource) -> Unit
) : RecyclerView.Adapter<WebDavAdapter.ViewHolder>() {

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
        val resource = items[position]
        val name = resource.name ?: resource.href.toString().substringAfterLast("/")
        val isDir = resource.isDirectory
        val typeLabel = if (isDir) "文件夹" else "文件"
        val sizeLabel = if (isDir) "" else "，大小 ${resource.contentLength ?: 0} 字节"

        holder.tvName.text = name
        holder.tvInfo.text = if (isDir) "文件夹" else "${resource.contentLength ?: 0} B"

        holder.itemView.contentDescription = "$typeLabel：$name$sizeLabel，双击打开"
        holder.itemView.setOnClickListener { onClick(resource) }
    }

    override fun getItemCount() = items.size
}
