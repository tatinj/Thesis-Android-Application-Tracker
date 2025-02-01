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

class registration : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

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
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }
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
            val password = passwordField.text.toString()
            val confirmPassword = confirmPasswordField.text.toString()
            val phoneNum = phoneNumberField.text.toString()

            if (password.isEmpty() && phoneNum.isEmpty()) {
                showToast(getString(R.string.password))
                showToast("Phone number must contain only digits")
            } else if (confirmPassword.isEmpty()) {
                showToast(getString(R.string.confirm_password))
            } else if (password != confirmPassword) {
                showToast(getString(R.string.passwordMismatch))
            } else {
                val intent = Intent(this, LocationActivity::class.java)
                startActivity(intent)
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