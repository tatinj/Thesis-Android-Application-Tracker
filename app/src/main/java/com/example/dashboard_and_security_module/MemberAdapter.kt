package com.example.dashboard_and_security_module

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MemberAdapter(
    private val members: List<Friend>,
    private val onCurfewClick: (Friend) -> Unit
) : RecyclerView.Adapter<MemberAdapter.MemberViewHolder>() {

    class MemberViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_member_name)
        val tvCode: TextView = itemView.findViewById(R.id.tv_member_code)
        val btnCurfew: Button = itemView.findViewById(R.id.btn_set_curfew)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_member, parent, false)
        return MemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val member = members[position]
        holder.tvName.text = member.name
        holder.tvCode.text = member.code

        holder.btnCurfew.setOnClickListener {
            onCurfewClick(member)
        }
    }

    override fun getItemCount(): Int = members.size
}
