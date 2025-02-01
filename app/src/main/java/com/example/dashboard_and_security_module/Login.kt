package com.example.dashboard_and_security_module

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.material.textfield.TextInputEditText
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import android.app.Activity

class Login : AppCompatActivity() {



    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    firebaseAuthWithGoogle(account.idToken!!)
                } catch (e: ApiException) {
                    Toast.makeText(this, "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val emailField: TextInputEditText = findViewById(R.id.email)
        val passwordField: TextInputEditText = findViewById(R.id.password)
        val btnLogin: Button = findViewById(R.id.btn_login)
        val tvForgotPassword: TextView = findViewById(R.id.tv_forgot_password)
        val tvRegister: TextView = findViewById(R.id.register)
        val googleSignInButton: ImageButton = findViewById(R.id.google_btn)

        fun showToast(message: String) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        // Handle normal login (email/password)
        btnLogin.setOnClickListener {
            val email = emailField.text?.toString()?.trim() ?: ""
            val password = passwordField.text?.toString()?.trim() ?: ""

            when {
                email.isEmpty() -> showToast("Email cannot be empty")
                !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> showToast("Invalid email format")
                password.isEmpty() -> showToast("Password cannot be empty")
                password.length < 6 -> showToast("Password must be at least 6 characters")
                else -> {
                    // Check if the email is registered
                    auth.fetchSignInMethodsForEmail(email).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val signInMethods = task.result?.signInMethods
                            if (signInMethods.isNullOrEmpty()) {
                                showToast("Email is not registered")
                            } else {
                                // Proceed with authentication
                                auth.signInWithEmailAndPassword(email, password)
                                    .addOnCompleteListener(this) { authTask ->
                                        if (authTask.isSuccessful) {
                                            val intent = Intent(this, LocationActivity::class.java)
                                            startActivity(intent)
                                        } else {
                                            showToast("Authentication failed: ${authTask.exception?.message}")
                                        }
                                    }
                            }
                        } else {
                            showToast("Error checking email: ${task.exception?.message}")
                        }
                    }
                }
            }
        }

        // Forgot Password functionality
        tvForgotPassword.setOnClickListener {
            // Implement forgot password functionality
            showToast("Forgot Password clicked")
        }

        // Redirect to registration screen
        tvRegister.setOnClickListener {
            val intent = Intent(this, registration::class.java)
            startActivity(intent)
        }

        // Google Sign-In
        googleSignInButton.setOnClickListener {
            signInWithGoogle()
        }
    }

    // Google Sign-In Method
    private fun signInWithGoogle() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))  // Use the actual Google Client ID here
            .requestEmail()
            .build()

        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    // Firebase Authentication with Google
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val user = auth.currentUser  // Retrieve the current user
                Toast.makeText(this, "Signed in as ${user?.displayName}", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, LocationActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}