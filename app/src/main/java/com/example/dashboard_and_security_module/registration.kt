package com.example.dashboard_and_security_module

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

// Assuming you have other necessary imports for UI components like CheckBox, TextWatcher, etc.

class registration : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

        auth = FirebaseAuth.getInstance()
        val btnSignUp: Button = findViewById(R.id.btn_sign_up)
        // ... all your other findViewById calls for name, email, password, etc.

        btnSignUp.setOnClickListener {
            // ... get all user inputs (email, name, password, etc.)
            val email = findViewById<TextInputEditText>(R.id.email).text.toString().trim()
            val name = findViewById<TextInputEditText>(R.id.name).text.toString().trim()
            val password = findViewById<TextInputEditText>(R.id.password).text.toString()
            val confirmPassword = findViewById<TextInputEditText>(R.id.confirm_password).text.toString()
            val phoneNum = findViewById<TextInputEditText>(R.id.phone_num).text.toString().trim()

            // ... your existing validation logic ...
            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            registerUser(email, name, password, phoneNum)
        }

        // ... other listeners like for tvLogin ...
    }

    private fun registerUser(email: String, name: String, pass: String, phone: String) {
        val normalizedPhone = normalizePhoneNumber(phone)
        if (normalizedPhone.isEmpty()) {
            Toast.makeText(this, "Invalid phone number format. Please use 09...", Toast.LENGTH_LONG).show()
            return
        }

        auth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val currentUser = auth.currentUser
                    if (currentUser != null) {
                        saveUserData(currentUser, email, name, normalizedPhone)
                    }
                } else {
                    Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun saveUserData(user: FirebaseUser, email: String, name: String, phone: String) {
        val userMap = hashMapOf(
            "email" to email,
            "name" to name,
            "phone" to phone
        )

        db.collection("users").document(user.uid).set(userMap)
            .addOnSuccessListener {
                Log.d("Registration", "User data saved successfully.")
                // After saving the main data, generate and save the invite code.
                generateAndSaveInviteCode(user)
            }
            .addOnFailureListener { e ->
                Log.e("Registration", "Failed to save user data.", e)
                Toast.makeText(this, "Failed to save user profile.", Toast.LENGTH_SHORT).show()
            }
    }

    // --- THIS IS THE KEY FUNCTION TO GENERATE AND SAVE THE CODE ---
    private fun generateAndSaveInviteCode(user: FirebaseUser) {
        // Create a random 6-character alphanumeric code.
        val inviteCode = List(6) { (('A'..'Z') + ('0'..'9')).random() }.joinToString("")

        // Define the path to save the code: users/{userId}/meta/inviteCode
        val inviteRef = db.collection("users").document(user.uid)
            .collection("meta").document("inviteCode")

        val codeMap = mapOf("code" to inviteCode)

        inviteRef.set(codeMap)
            .addOnSuccessListener {
                Log.d("Registration", "Invite code '$inviteCode' saved successfully.")
                // After everything is done, send the verification email and proceed.
                sendVerificationEmailAndProceed(user)
            }
            .addOnFailureListener { e ->
                Log.e("Registration", "Failed to save invite code.", e)
                // Still proceed even if code saving fails, as the account is created.
                sendVerificationEmailAndProceed(user)
            }
    }


    private fun sendVerificationEmailAndProceed(user: FirebaseUser) {
        user.sendEmailVerification()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Registration successful. Please check your email.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Registration successful, but failed to send verification email.", Toast.LENGTH_SHORT).show()
                }

                // --- GATE KEEPER FOR EMAIL REGISTRATION ---
                val intent = Intent(this, VerifyEmailActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()

            }
    }

    private fun normalizePhoneNumber(number: String): String {
        val digitsOnly = number.filter { it.isDigit() }
        return when {
            digitsOnly.startsWith("09") && digitsOnly.length == 11 -> "+63" + digitsOnly.substring(1)
            else -> "" // Return empty for invalid formats to fail validation
        }
    }
}
