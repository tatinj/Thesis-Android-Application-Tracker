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
        val passwordLayout: TextInputLayout = findViewById(R.id.password_layout)
        val contactNumberLayout: TextInputLayout = findViewById(R.id.contact_number_layout)
        val checkboxTerms: CheckBox = findViewById(R.id.checkbox_terms)
        val tvTermsAndPolicy: TextView = findViewById(R.id.tv_terms_and_policy)
        val btnSignUp: Button = findViewById(R.id.btn_sign_up)
        val tvLogin: TextView = findViewById(R.id.tv_login)

        // --- Setup for Clickable "Terms and Privacy Policy" Text ---
        setupClickableTermsText(tvTermsAndPolicy)

        // --- Input Validation Listeners ---
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
    private fun openPdfViewer() {
        val intent = Intent(this, PdfViewerActivity::class.java)
        intent.putExtra("PDF_FILE_NAME", "terms_and_conditions.pdf")
        startActivity(intent)
    }

    private fun setupClickableTermsText(textView: TextView) {
        // --- STYLING FOR THE ENTIRE TEXT VIEW ---

        // 1. Set the main text color for the whole line
        textView.setTextColor(Color.parseColor("#F8F8FF"))

        // 2. Set the outline for the whole line
        val outlineWidth = 6f
        textView.setShadowLayer(outlineWidth, 0f, 0f, Color.BLACK)

        // 3. Disable hardware acceleration to make the outline sharp
        textView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)


        // --- LOGIC FOR THE CLICKABLE PART ---
        val fullText = "I agree to the Terms and Privacy Policy"
        val clickableText = "Terms and Privacy Policy"

        val spannableString = SpannableString(fullText)
        val startIndex = fullText.indexOf(clickableText)
        val endIndex = startIndex + clickableText.length

        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                // Call the dedicated function to open the PDF
                openPdfViewer()
            }

            override fun updateDrawState(ds: TextPaint) {
                // We only need to control the underline and bold properties here now
                // The color and shadow are already handled by the TextView itself
                super.updateDrawState(ds)

                // --- THIS IS THE FIX ---
                // Force the link to use the same color as the parent TextView
                ds.color = textView.currentTextColor

                ds.isUnderlineText = true
                ds.isFakeBoldText = true
            }
        }

        spannableString.setSpan(clickableSpan, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        textView.text = spannableString
        textView.movementMethod = LinkMovementMethod.getInstance()
    }

    // No changes needed below this line, but it's included for completeness
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
        if (!isTermsChecked) {
            showToast("You must agree to the Terms & Privacy Policy")
            return
        }
        if (email.isEmpty() || name.isEmpty() || pass.isEmpty() || phone.isEmpty() || confirmPass.isEmpty()) {
            showToast("All fields are required")
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
        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        var inviteCode = sharedPref.getString("invite_code", null)

        if (inviteCode == null) {
            inviteCode = generateUniqueCode()
            sharedPref.edit().putString("invite_code", inviteCode).apply()
        }

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
