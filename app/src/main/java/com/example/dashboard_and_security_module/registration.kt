package com.example.dashboard_and_security_module

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class registration : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

        auth = FirebaseAuth.getInstance()

        val emailField: EditText = findViewById(R.id.email)
        val nameField: EditText = findViewById(R.id.name)
        val passwordField: EditText = findViewById(R.id.password)
        val confirmPasswordField: EditText = findViewById(R.id.confirm_password)
        val btnSignUp: Button = findViewById(R.id.btn_sign_up)
        val tvLogin: TextView = findViewById(R.id.tv_login)
        val phoneNumberField: TextInputEditText = findViewById(R.id.phone_num)
        val contactNumberLayout: TextInputLayout = findViewById(R.id.contact_number_layout)
        val passwordLayout: TextInputLayout = findViewById(R.id.password_layout)
        val checkboxTerms: CheckBox = findViewById(R.id.checkbox_terms)

        fun showToast(message: String) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        phoneNumberField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
                if (input.length != 11) {
                    contactNumberLayout.error = "-Contact number must be 11 digits"
                } else {
                    contactNumberLayout.error = null
                }
            }
        })

        passwordField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
                val errors = mutableListOf<String>()

                if (input.length < 8 || input.length > 12) {
                    errors.add("- Password must be 8-12 characters long")
                }
                if (!input.any { it.isUpperCase() }) {
                    errors.add("- Include at least one uppercase letter")
                }
                if (!input.any { it.isLowerCase() }) {
                    errors.add("- Include at least one lowercase letter")
                }
                if (!input.any { it.isDigit() }) {
                    errors.add("- Include at least one number")
                }
                if (!input.any { it in "@#\$%^&+=!" }) {
                    errors.add("- Include at least one special character (@#\$%^&+=!)")
                }

                passwordLayout.error = if (errors.isNotEmpty()) errors.joinToString("\n") else null
            }
        })

        btnSignUp.setOnClickListener {
            val email = emailField.text.toString().trim()
            val name = nameField.text.toString().trim()
            val password = passwordField.text.toString()
            val confirmPassword = confirmPasswordField.text.toString()
            val phoneNum = phoneNumberField.text.toString().trim()

            if (!checkboxTerms.isChecked) {
                showToast("You must agree to the Terms & Privacy Policy")
                return@setOnClickListener
            }

            if (email.isEmpty() || name.isEmpty() || password.isEmpty() || phoneNum.isEmpty()) {
                showToast("All fields are required")
            } else if (confirmPassword.isEmpty()) {
                showToast("Please confirm your password")
            } else if (password != confirmPassword) {
                showToast("Passwords do not match")
            } else {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            val currentUser = auth.currentUser
                            if (currentUser != null) {
                                val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                                var inviteCode = sharedPref.getString("invite_code", null)

                                if (inviteCode == null) {
                                    inviteCode = generateUniqueCode()
                                    sharedPref.edit().putString("invite_code", inviteCode).apply()
                                }

                                val userMap = hashMapOf(
                                    "email" to email,
                                    "name" to name,
                                    "phone" to phoneNum,

                                )

                                db.collection("users").document(currentUser.uid).set(userMap)
                                    .addOnSuccessListener {
                                        showToast("User data saved")
                                    }
                                    .addOnFailureListener { e ->
                                        showToast("Failed to register: ${e.message}")
                                    }

// Always save invite code to Firestore under meta
                                val inviteRef = db.collection("users")
                                    .document(currentUser.uid)
                                    .collection("meta")
                                    .document("inviteCode")

                                inviteRef.set(mapOf("code" to inviteCode))
                                    .addOnSuccessListener {
                                        showToast("Registration successful\nInvite Code: $inviteCode")
                                        val intent = Intent(this, LocationActivity::class.java)
                                        startActivity(intent)
                                        finish()
                                    }
                                    .addOnFailureListener { e ->
                                        showToast("Failed to save invite code: ${e.message}")
                                    }
                            } else {
                                showToast("User not authenticated")
                            }
                        } else {
                            showToast("Authentication failed: ${task.exception?.message}")
                        }
                    }
            }
        }

        tvLogin.setOnClickListener {
            val intent = Intent(this, Login::class.java)
            startActivity(intent)
        }
    }

    private fun generateUniqueCode(): String {
        return List(6) {
            (('A'..'Z') + ('0'..'9')).random()
        }.joinToString("")
    }
}
