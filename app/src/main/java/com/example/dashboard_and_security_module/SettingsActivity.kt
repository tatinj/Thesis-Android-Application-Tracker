package com.example.dashboard_and_security_module

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SettingsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var tvUserName: TextView
    private lateinit var etUserEmail: EditText
    private lateinit var etUserPhone: EditText
    private lateinit var btnLogout: Button
    private lateinit var btnBack: ImageButton
    private lateinit var btnEditSave: Button

    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        tvUserName = findViewById(R.id.tv_user_name)
        etUserEmail = findViewById(R.id.et_user_email)
        etUserPhone = findViewById(R.id.et_user_phone)
        btnLogout = findViewById(R.id.btn_logout)
        btnBack = findViewById(R.id.btn_back)
        btnEditSave = findViewById(R.id.btn_edit_save)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        loadUserProfile()

        btnBack.setOnClickListener { finish() }
        btnLogout.setOnClickListener { logoutUser() }
        btnEditSave.setOnClickListener { toggleEditSave() }
    }

    private fun toggleEditSave() {
        if (isEditMode) {
            if (saveUserProfile()) {
                isEditMode = false
                btnEditSave.text = "Edit Profile"
                etUserEmail.isEnabled = false
                etUserPhone.isEnabled = false
            }
        } else {
            isEditMode = true
            btnEditSave.text = "Save"
            etUserEmail.isEnabled = true
            etUserPhone.isEnabled = true
        }
    }

    private fun loadUserProfile() {
        val currentUser = auth.currentUser ?: return
        firestore.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    tvUserName.text = document.getString("name") ?: "No Name"
                    etUserEmail.setText(document.getString("email") ?: "No Email")
                    etUserPhone.setText(document.getString("phone") ?: "No Phone Number")
                } else {
                    tvUserName.text = currentUser.displayName ?: "No Name"
                    etUserEmail.setText(currentUser.email ?: "No Email")
                    etUserPhone.setText(currentUser.phoneNumber ?: "No Phone Number")
                }
            }
    }

    private fun saveUserProfile(): Boolean {
        val phone = etUserPhone.text.toString()
        if (phone.length != 11 || !phone.all { it.isDigit() }) {
            Toast.makeText(this, "Phone number must be 11 digits and contain no letters or special characters.", Toast.LENGTH_LONG).show()
            return false
        }

        val currentUser = auth.currentUser ?: return false
        val uid = currentUser.uid

        val updatedUser = mapOf(
            "email" to etUserEmail.text.toString(),
            "phone" to phone
        )

        firestore.collection("users").document(uid).update(updatedUser)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        return true
    }

    private fun logoutUser() {
        auth.signOut()
        val intent = Intent(this, Login::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
