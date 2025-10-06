package com.guruyuknow.hisabbook.group

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.guruyuknow.hisabbook.R

class PreviewImageAdapter(
    private val items: MutableList<Uri>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<PreviewImageAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.ivThumb)
        val btnRemove: ImageButton = view.findViewById(R.id.btnRemove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_preview_image, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val uri = items[position]
        holder.img.load(uri) { crossfade(true) }
        holder.btnRemove.setOnClickListener {
            onRemove(holder.bindingAdapterPosition)
        }
    }

    override fun getItemCount() = items.size
}
