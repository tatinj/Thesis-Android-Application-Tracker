package com.example.dashboard_and_security_module

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.MultiFactorInfo
import com.google.firebase.firestore.FirebaseFirestore

class SettingsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var tvUserName: TextView
    private lateinit var tvUserEmail: TextView
    private lateinit var etUserPhone: EditText
    private lateinit var btnLogout: Button
    private lateinit var btnBack: ImageButton
    private lateinit var btnEditSave: Button
    private lateinit var tv2faStatus: TextView
    private lateinit var btnManage2fa: Button

    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Initialize all views
        tvUserName = findViewById(R.id.tv_user_name)
        tvUserEmail = findViewById(R.id.tv_user_email)
        etUserPhone = findViewById(R.id.et_user_phone)
        btnLogout = findViewById(R.id.btn_logout)
        btnBack = findViewById(R.id.btn_back)
        btnEditSave = findViewById(R.id.btn_edit_save)
        tv2faStatus = findViewById(R.id.tv_2fa_status)
        btnManage2fa = findViewById(R.id.btn_manage_2fa)

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

    override fun onResume() {
        super.onResume()
        // Reload user data to get the latest 2FA and email verification status
        auth.currentUser?.reload()?.addOnSuccessListener {
            update2faStatus()
        }
    }

    /**
     * Converts a local Philippine phone number (e.g., 09...) to E.164 format (+639...).
     * This ensures data consistency.
     */
    private fun normalizePhoneNumber(number: String): String {
        // First, remove any non-digit characters
        val digitsOnly = number.filter { it.isDigit() }
        return when {
            // If it starts with "09" and has 11 digits
            digitsOnly.startsWith("09") && digitsOnly.length == 11 -> "+63" + digitsOnly.substring(1)
            // If it starts with "9" and has 10 digits
            digitsOnly.startsWith("9") && digitsOnly.length == 10 -> "+63$digitsOnly"
            // If it's already in "+639" format with 13 characters
            number.startsWith("+639") && number.length == 13 -> number
            // If it's in "639" format
            digitsOnly.startsWith("639") && digitsOnly.length == 12 -> "+$digitsOnly"
            // Otherwise, return the original input for further validation
            else -> number
        }
    }

    private fun update2faStatus() {
        val user = auth.currentUser ?: return
        val enrolledFactors = user.multiFactor.enrolledFactors

        if (enrolledFactors.isNotEmpty()) {
            tv2faStatus.text = "Two-Factor Authentication is enabled."
            btnManage2fa.text = "Manage 2FA" // Or "Disable"
            // You can add logic here to un-enroll if needed
        } else {
            tv2faStatus.text = "Two-Factor Authentication is disabled."
            btnManage2fa.text = "Enable 2FA"
            btnManage2fa.setOnClickListener {
                if (user.isEmailVerified) {
                    val intent = Intent(this, EnrollMfaActivity::class.java)
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Please verify your email address first.", Toast.LENGTH_LONG).show()
                    user.sendEmailVerification().addOnSuccessListener {
                        Toast.makeText(this, "A new verification email has been sent.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun toggleEditSave() {
        isEditMode = !isEditMode
        if (isEditMode) {
            btnEditSave.text = "Save"
            etUserPhone.isEnabled = true
            etUserPhone.requestFocus()
        } else {
            // When saving, the saveUserProfile function will handle UI updates
            saveUserProfile()
            btnEditSave.text = "Edit Profile"
            etUserPhone.isEnabled = false
        }
    }

    private fun loadUserProfile() {
        val currentUser = auth.currentUser ?: return
        firestore.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    tvUserName.text = document.getString("name") ?: "No Name"
                    tvUserEmail.text = document.getString("email") ?: "No Email"
                    // Display the phone number directly from the database
                    etUserPhone.setText(document.getString("phone") ?: "")
                }
            }
    }

    private fun saveUserProfile() {
        val phoneInput = etUserPhone.text.toString().trim()
        if (phoneInput.isEmpty()) {
            Toast.makeText(this, "Phone number cannot be empty.", Toast.LENGTH_SHORT).show()
            return
        }

        // --- KEY CHANGE ---
        // Always normalize the number before validation and saving.
        val normalizedPhone = normalizePhoneNumber(phoneInput)

        // Basic validation for the normalized number
        if (!normalizedPhone.startsWith("+639") || normalizedPhone.length != 13) {
            Toast.makeText(this, "Invalid phone number format. Please use 09... format.", Toast.LENGTH_LONG).show()
            return
        }

        // Update the UI to show the clean, normalized number immediately.
        etUserPhone.setText(normalizedPhone)

        val currentUser = auth.currentUser ?: return
        val updatedUser = mapOf("phone" to normalizedPhone)

        firestore.collection("users").document(currentUser.uid).update(updatedUser)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun logoutUser() {
        auth.signOut()
        val intent = Intent(this, Login::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
