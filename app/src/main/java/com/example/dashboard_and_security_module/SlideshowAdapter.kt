package com.example.dashboard_and_security_module

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class SlideshowAdapter(private val images: List<Int>) : RecyclerView.Adapter<SlideshowAdapter.SlideshowViewHolder>() {

    class SlideshowViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.slide_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideshowViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_slide, parent, false)
        return SlideshowViewHolder(view)
    }

    override fun onBindViewHolder(holder: SlideshowViewHolder, position: Int) {
        holder.imageView.setImageResource(images[position])
    }

    override fun getItemCount(): Int = images.size
}
    