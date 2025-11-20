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

    // UI Components
    private lateinit var tvUserName: TextView
    private lateinit var tvUserEmail: TextView
    private lateinit var etUserPhone: EditText
    private lateinit var btnLogout: Button
    private lateinit var btnBack: ImageButton
    private lateinit var btnEditSave: Button
    private lateinit var tv2faStatus: TextView
    private lateinit var btnManage2fa: Button

    // State variables
    private var isEditMode = false
    private var fullPhoneNumber: String = "" // To store the real, unmasked phone number

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Initialize Firebase and UI components
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

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

        // Set listeners
        btnBack.setOnClickListener { finish() }
        btnLogout.setOnClickListener { logoutUser() }
        btnEditSave.setOnClickListener { toggleEditSave() }
    }

    override fun onResume() {
        super.onResume()
        // Reload user to get the latest 2FA and email verification status
        auth.currentUser?.reload()?.addOnSuccessListener {
            update2faStatus()
        }
    }

    // --- START: NEW MASKING FUNCTIONS ---
    /**
     * Masks an email address.
     * Example: "sinag.user@example.com" becomes "s...r@example.com"
     */
    private fun maskEmail(email: String): String {
        val atIndex = email.indexOf('@')
        if (atIndex <= 2) {
            return email // Not enough characters to mask
        }
        val prefix = email.substring(0, 1) // First letter
        val suffix = email.substring(atIndex - 1) // Last letter of username + @domain.com
        return "$prefix****$suffix"
    }

    /**
     * Masks a phone number to show only the last 4 digits.
     * Example: "+639123456789" becomes "*********6789"
     */
    private fun maskPhoneNumber(phone: String): String {
        if (phone.length <= 4) {
            return phone
        }
        val last4 = phone.takeLast(4)
        return "".padStart(phone.length - 4, '*') + last4
    }
    // --- END: NEW MASKING FUNCTIONS ---

    private fun normalizePhoneNumber(number: String): String {
        val digitsOnly = number.filter { it.isDigit() }
        return when {
            digitsOnly.startsWith("09") && digitsOnly.length == 11 -> "+63" + digitsOnly.substring(1)
            digitsOnly.startsWith("9") && digitsOnly.length == 10 -> "+63$digitsOnly"
            number.startsWith("+639") && number.length == 13 -> number
            digitsOnly.startsWith("639") && digitsOnly.length == 12 -> "+$digitsOnly"
            else -> ""
        }
    }

    private fun update2faStatus() {
        // This function's logic remains the same and is correct.
        val user = auth.currentUser ?: return
        val enrolledFactors = user.multiFactor.enrolledFactors

        if (enrolledFactors.isNotEmpty()) {
            tv2faStatus.text = "Two-Factor Authentication is enabled."
            btnManage2fa.text = "Manage 2FA" // Or "Disable"
        } else {
            tv2faStatus.text = "Two-Factor Authentication is disabled."
            btnManage2fa.text = "Enable 2FA"
            btnManage2fa.setOnClickListener {
                if (user.isEmailVerified) {
                    startActivity(Intent(this, EnrollMfaActivity::class.java))
                } else {
                    Toast.makeText(this, "Please verify your email first.", Toast.LENGTH_LONG).show()
                    user.sendEmailVerification()
                }
            }
        }
    }

    private fun toggleEditSave() {
        isEditMode = !isEditMode
        if (isEditMode) {
            btnEditSave.text = "Save"
            etUserPhone.isEnabled = true
            // --- UNMASK for editing ---
            etUserPhone.setText(fullPhoneNumber)
            etUserPhone.requestFocus()
        } else {
            // When saving, the phone number will be re-masked inside saveUserProfile()
            saveUserProfile()
        }
    }

    private fun loadUserProfile() {
        val currentUser = auth.currentUser ?: return
        firestore.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // Load the user's data
                    val name = document.getString("name") ?: "No Name"
                    val email = document.getString("email") ?: "No Email"
                    fullPhoneNumber = document.getString("phone") ?: "" // Store the real number

                    // --- APPLY MASKING TO THE UI ---
                    tvUserName.text = name
                    tvUserEmail.text = maskEmail(email)
                    etUserPhone.setText(maskPhoneNumber(fullPhoneNumber))
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load profile.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveUserProfile() {
        val phoneInput = etUserPhone.text.toString().trim()
        if (phoneInput.isEmpty()) {
            Toast.makeText(this, "Phone number cannot be empty.", Toast.LENGTH_SHORT).show()
            return
        }

        val normalizedPhone = normalizePhoneNumber(phoneInput)

        if (normalizedPhone.isEmpty() || !normalizedPhone.startsWith("+639") || normalizedPhone.length != 13) {
            Toast.makeText(this, "Invalid phone number format. Please use 09...", Toast.LENGTH_LONG).show()
            return
        }

        // Update the stored full phone number
        fullPhoneNumber = normalizedPhone

        val currentUser = auth.currentUser ?: return
        val updatedUser = mapOf("phone" to normalizedPhone)

        firestore.collection("users").document(currentUser.uid).update(updatedUser)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()

                etUserPhone.setText(maskPhoneNumber(fullPhoneNumber))
                // And exit edit mode
                isEditMode = false
                btnEditSave.text = "Edit Profile"
                etUserPhone.isEnabled = false
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
