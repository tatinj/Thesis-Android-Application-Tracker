package com.example.dashboard_and_security_module


import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import android.text.Editable
import android.text.TextWatcher
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

class registration : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()  // Firestore instance
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

        // Utility function to show a toast notification
        fun showToast(message: String) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        // TextWatcher for contact number
        phoneNumberField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
                if (input.length != 11) {
                    contactNumberLayout.error = "-Contact number must be 11 digits"
                } else {
                    contactNumberLayout.error = null // Clear the error
                }
            }
        })

        passwordField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
                val errors = mutableListOf<String>()

                // password length (8-12 characters)
                if (input.length < 8 || input.length > 12) {
                    errors.add("- Password must be 8-12 characters long")
                }

                // at least one uppercase letter
                if (!input.any { it.isUpperCase() }) {
                    errors.add("- Include at least one uppercase letter")
                }

                // at least one lowercase letter
                if (!input.any { it.isLowerCase() }) {
                    errors.add("- Include at least one lowercase letter")
                }

                // at least one number
                if (!input.any { it.isDigit() }) {
                    errors.add("- Include at least one number")
                }

                // at least one special character
                if (!input.any { it in "@#\$%^&+=!" }) {
                    errors.add("- Include at least one special character (@#\$%^&+=!)")
                }

                // Set all errors at the same time
                passwordLayout.error = if (errors.isNotEmpty()) errors.joinToString("\n") else null
            }
        })

        // Set click listener for Sign Up button
        btnSignUp.setOnClickListener {
            val email = emailField.text.toString()
            val name = nameField.text.toString()
            val password = passwordField.text.toString()
            val confirmPassword = confirmPasswordField.text.toString()
            val phoneNum = phoneNumberField.text.toString()

            if (email.isEmpty() || name.isEmpty() || password.isEmpty() || phoneNum.isEmpty()) {
                showToast("All fields are required")
            } else if (confirmPassword.isEmpty()) {
                showToast(getString(R.string.confirm_password))
            } else if (password != confirmPassword) {
                showToast(getString(R.string.passwordMismatch))
            } else {
                // Create a new user with email and password
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            val currentUser = auth.currentUser
                            if (currentUser != null) {
                                // Store user data in Firestore
                                val userMap = hashMapOf(
                                    "email" to email,
                                    "name" to name,
                                    "phone" to phoneNum,
                                    "password" to password
                                )
                                db.collection("users").document(currentUser.uid).set(userMap)
                                    .addOnSuccessListener {
                                        showToast("Registration successful")
                                        val intent = Intent(this, LocationActivity::class.java)
                                        startActivity(intent)
                                    }
                                    .addOnFailureListener { e ->
                                        showToast("Failed to register: ${e.message}")
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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvLogin.setOnClickListener {
            val intent = Intent(this, Login::class.java)
            startActivity(intent)
        }
    }
}