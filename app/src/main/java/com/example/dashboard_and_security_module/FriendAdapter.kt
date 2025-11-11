package com.example.dashboard_and_security_module

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FriendAdapter(
    private val friendList: MutableList<Friend>,
    private val onFindClicked: (Friend) -> Unit
) : RecyclerView.Adapter<FriendAdapter.FriendViewHolder>() {

    inner class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_friend_name)
        val tvNumber: TextView = itemView.findViewById(R.id.tv_friend_number)
        val tvCode: TextView = itemView.findViewById(R.id.tv_friend_code)
        val btnFind: Button = itemView.findViewById(R.id.btn_find_friend)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete_friend)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = friendList[position]
        val context = holder.itemView.context

        holder.tvName.text = friend.name
        holder.tvNumber.text = "Phone: ${friend.phone}"
        holder.tvCode.text = "Code: ${friend.code}"

        holder.btnFind.setOnClickListener { onFindClicked(friend) }
        holder.btnDelete.setOnClickListener { showDeleteConfirmation(context, friend, position) }
    }

    override fun getItemCount(): Int = friendList.size

    private fun showDeleteConfirmation(context: Context, friend: Friend, position: Int) {
        AlertDialog.Builder(context)
            .setTitle("Delete Friend")
            .setMessage("Are you sure you want to delete ${friend.name}?")
            .setPositiveButton("Yes") { _, _ -> deleteFriendFromFirestore(context, friend, position) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteFriendFromFirestore(context: Context, friend: Friend, position: Int) {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val currentUid = auth.currentUser?.uid ?: return

        db.collection("users")
            .document(currentUid)
            .collection("friends")
            .document(friend.code)
            .delete()
            .addOnSuccessListener {
                friendList.removeAt(position)
                notifyItemRemoved(position)
                Toast.makeText(context, "Friend deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
