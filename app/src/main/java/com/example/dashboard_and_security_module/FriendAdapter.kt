package com.example.dashboard_and_security_module

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FriendAdapter(
    private val friendList: List<Friend>,
    private val onFindClick: (Friend) -> Unit
) : RecyclerView.Adapter<FriendAdapter.FriendViewHolder>() {

    class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.tv_friend_name)
        val numberText: TextView = itemView.findViewById(R.id.tv_friend_number)
        val codeText: TextView = itemView.findViewById(R.id.tv_friend_code)
        val findButton: Button = itemView.findViewById(R.id.btn_find_friend)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = friendList[position]
        holder.nameText.text = friend.name
        holder.numberText.text = "Phone: ${friend.phone}"
        holder.codeText.text = "Code: ${friend.code}"

        holder.findButton.setOnClickListener {
            onFindClick(friend)
        }
    }

    override fun getItemCount(): Int = friendList.size
}
