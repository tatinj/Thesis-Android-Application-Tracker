package com.example.dashboard_and_security_module

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.Button
import android.widget.CheckBox
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

        // --- View Initialization ---
        val nameField: TextInputEditText = findViewById(R.id.name)
        val emailField: TextInputEditText = findViewById(R.id.email)
        val passwordField: TextInputEditText = findViewById(R.id.password)
        val confirmPasswordField: TextInputEditText = findViewById(R.id.confirm_password)
        val phoneNumberField: TextInputEditText = findViewById(R.id.phone_num)
        val nameLayout: TextInputLayout = findViewById(R.id.name_layout)
        val emailLayout: TextInputLayout = findViewById(R.id.email_layout)
        val passwordLayout: TextInputLayout = findViewById(R.id.password_layout)
        val confirmPasswordLayout: TextInputLayout = findViewById(R.id.confirm_password_layout)
        val contactNumberLayout: TextInputLayout = findViewById(R.id.contact_number_layout)
        val checkboxTerms: CheckBox = findViewById(R.id.checkbox_terms)
        val tvTermsAndPolicy: TextView = findViewById(R.id.tv_terms_and_policy)
        val btnSignUp: Button = findViewById(R.id.btn_sign_up)
        val tvLogin: TextView = findViewById(R.id.tv_login)

        // --- Setup for Clickable "Terms and Privacy Policy" Text ---
        setupClickableTermsText(tvTermsAndPolicy)

        // --- Input Validation Listeners ---
        setupNameValidation(nameField, nameLayout)
        addTextWatcherToClearError(emailField, emailLayout)
        addTextWatcherToClearError(passwordField, passwordLayout)
        addTextWatcherToClearError(confirmPasswordField, confirmPasswordLayout)
        addTextWatcherToClearError(phoneNumberField, contactNumberLayout)
        setupPhoneNumberValidation(phoneNumberField, contactNumberLayout)
        setupPasswordValidation(passwordField, passwordLayout)

        // --- Button Click Listeners ---
        btnSignUp.setOnClickListener {
            val email = emailField.text.toString().trim()
            val name = nameField.text.toString().trim()
            val password = passwordField.text.toString()
            val confirmPassword = confirmPasswordField.text.toString()
            val phoneNum = phoneNumberField.text.toString().trim()

            // Perform registration
            registerUser(email, name, password, confirmPassword, phoneNum, checkboxTerms.isChecked)
        }

        tvLogin.setOnClickListener {
            // This is the only place that should navigate to the Login screen
            val intent = Intent(this, Login::class.java)
            startActivity(intent)
        }
    }

    // This function will launch the PDF viewer
    private fun openPdfViewer(fileName: String) {
        val intent = Intent(this, PdfViewerActivity::class.java)
        intent.putExtra("PDF_FILE_NAME", fileName)
        startActivity(intent)
    }

    private fun setupClickableTermsText(textView: TextView) {
        val fullText = "I agree to Terms & Conditions and Privacy Policy"
        val termsAndConditions = "Terms & Conditions"
        val privacyPolicy = "Privacy Policy"

        val spannableString = SpannableString(fullText)

        val termsClickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                openPdfViewer("terms_and_conditions.pdf")
            }
            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = true
                ds.textSkewX = -0.25f
            }
        }

        val policyClickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                openPdfViewer("privacy_policy.pdf")
            }
            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = true
                ds.textSkewX = -0.25f
            }
        }

        val termsStartIndex = fullText.indexOf(termsAndConditions)
        if (termsStartIndex != -1) {
            val termsEndIndex = termsStartIndex + termsAndConditions.length
            spannableString.setSpan(termsClickableSpan, termsStartIndex, termsEndIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val policyStartIndex = fullText.indexOf(privacyPolicy)
        if (policyStartIndex != -1) {
            val policyEndIndex = policyStartIndex + privacyPolicy.length
            spannableString.setSpan(policyClickableSpan, policyStartIndex, policyEndIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        textView.text = spannableString
        textView.movementMethod = LinkMovementMethod.getInstance()
        textView.highlightColor = Color.TRANSPARENT
    }

    private fun setupNameValidation(field: TextInputEditText, layout: TextInputLayout) {
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
                if (input.isNotEmpty() && !input.matches("^[a-zA-Z\\s]*$".toRegex())) {
                    layout.error = "Name can only contain letters and spaces."
                } else {
                    layout.error = null
                }
            }
        })
    }

    private fun addTextWatcherToClearError(field: TextInputEditText, layout: TextInputLayout) {
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                layout.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupPhoneNumberValidation(field: TextInputEditText, layout: TextInputLayout) {
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.toString().length != 11) {
                    layout.error = "-Contact number must be 11 digits"
                } else {
                    layout.error = null
                }
            }
        })
    }

    private fun setupPasswordValidation(field: TextInputEditText, layout: TextInputLayout) {
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
                val errors = mutableListOf<String>()

                if (input.length !in 8..12) errors.add("- Must be 8-12 characters")
                if (!input.any { it.isUpperCase() }) errors.add("- At least one uppercase letter")
                if (!input.any { it.isLowerCase() }) errors.add("- At least one lowercase letter")
                if (!input.any { it.isDigit() }) errors.add("- At least one number")
                if (!input.any { it in "@#$%^&+=!" }) errors.add("- At least one special character (@#$%^&+=!)")

                layout.error = if (errors.isNotEmpty()) errors.joinToString("\n") else null
            }
        })
    }

    private fun registerUser(
        email: String, name: String, pass: String, confirmPass: String, phone: String, isTermsChecked: Boolean
    ) {
        val nameLayout: TextInputLayout = findViewById(R.id.name_layout)
        val emailLayout: TextInputLayout = findViewById(R.id.email_layout)
        val passwordLayout: TextInputLayout = findViewById(R.id.password_layout)
        val confirmPasswordLayout: TextInputLayout = findViewById(R.id.confirm_password_layout)
        val contactNumberLayout: TextInputLayout = findViewById(R.id.contact_number_layout)

        var hasError = false
        if (name.isEmpty()) {
            nameLayout.error = "Name is required"
            hasError = true
        } else if (!name.matches("^[a-zA-Z\\s]*$".toRegex())) {
            nameLayout.error = "Name can only contain letters and spaces."
            hasError = true
        }

        if (email.isEmpty()) {
            emailLayout.error = "Email is required"
            hasError = true
        }
        if (pass.isEmpty()) {
            passwordLayout.error = "Password is required"
            hasError = true
        }
        if (confirmPass.isEmpty()) {
            confirmPasswordLayout.error = "Confirm Password is required"
            hasError = true
        }
        if (phone.isEmpty()) {
            contactNumberLayout.error = "Contact Number is required"
            hasError = true
        }

        if (hasError) return

        if (!isTermsChecked) {
            showToast("You must agree to the Terms & Conditions and Privacy Policy")
            return
        }
        if (pass != confirmPass) {
            showToast("Passwords do not match")
            return
        }

        auth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val currentUser = auth.currentUser
                    if (currentUser != null) {
                        saveUserData(currentUser.uid, email, name, phone)
                    } else {
                        showToast("User not authenticated after creation.")
                    }
                } else {
                    showToast("Authentication failed: ${task.exception?.message}")
                }
            }
    }

    private fun saveUserData(userId: String, email: String, name: String, phone: String) {
        val userMap = hashMapOf("email" to email, "name" to name, "phone" to phone)

        db.collection("users").document(userId).set(userMap)
            .addOnSuccessListener {
                saveInviteCode(userId)
            }
            .addOnFailureListener { e ->
                showToast("Failed to save user data: ${e.message}")
            }
    }

    private fun saveInviteCode(userId: String) {
        val inviteCode = generateUniqueCode()

        val inviteRef = db.collection("users").document(userId).collection("meta").document("inviteCode")

        inviteRef.set(mapOf("code" to inviteCode))
            .addOnSuccessListener {
                showToast("Registration successful!\nInvite Code: $inviteCode")
                val intent = Intent(this, Login::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                showToast("Failed to save invite code: ${e.message}")
            }
    }

    private fun generateUniqueCode(): String {
        return List(6) { (('A'..'Z') + ('0'..'9')).random() }.joinToString("")
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
