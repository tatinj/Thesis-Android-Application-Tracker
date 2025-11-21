package com.example.dashboard_and_security_module

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LocationLogAdapter(private val logs: List<LocationLog>) :
    RecyclerView.Adapter<LocationLogAdapter.LogViewHolder>() {

    inner class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvAddress: TextView = itemView.findViewById(R.id.tv_log_address)
        val tvTime: TextView = itemView.findViewById(R.id.tv_log_time)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_location_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val log = logs[position]
        holder.tvAddress.text = log.address
        holder.tvTime.text = log.timeStamp
    }

    override fun getItemCount(): Int = logs.size
}
