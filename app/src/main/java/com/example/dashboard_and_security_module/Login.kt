package com.example.dashboard_and_security_module

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
// 2FA IMPORTS
import com.google.firebase.auth.FirebaseAuthMultiFactorException
import com.google.firebase.auth.MultiFactorResolver

class Login : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        val emailField: TextInputEditText = findViewById(R.id.email)


        emailField.post {
            if (auth.currentUser != null) {

                handleSuccessfulLogin()
                return@post
            }
        }

        googleSignInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)!!
                    firebaseAuthWithGoogle(account.idToken!!)
                } catch (e: ApiException) {
                    showToast("Google sign in failed: ${e.message}")
                }
            }
        }

        // Continue with the rest of the setup
        val passwordField: TextInputEditText = findViewById(R.id.password)
        val btnLogin: Button = findViewById(R.id.btn_login)
        val tvRegister: TextView = findViewById(R.id.register)
        val googleSignInButton: ImageButton = findViewById(R.id.google_btn)

        val tvForgotPassword: TextView = findViewById(R.id.tv_forgot_password)


        btnLogin.setOnClickListener {
            val email = emailField.text?.toString()?.trim() ?: ""
            val password = passwordField.text?.toString()?.trim() ?: ""

            if (email.isEmpty() || password.isEmpty()) {
                showToast("Email and password cannot be empty")
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showToast("Invalid email format")
                return@setOnClickListener
            }
            loginUser(email, password)
        }

        tvRegister.setOnClickListener {
            startActivity(Intent(this, registration::class.java))
        }

        googleSignInButton.setOnClickListener {
            signInWithGoogle()
        }

        // ETO ANG FORGOT PASSWORD FUNCTION
        tvForgotPassword.setOnClickListener {

            val intent = Intent(this, ForgotPasswordActivity::class.java)
            startActivity(intent)
        }

    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    //  LOGIN USER FOR WITH 2FA
    private fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("Login", "User signed in successfully (no 2FA).")
                    handleSuccessfulLogin()
                } else {
                    val exception = task.exception
                    if (exception is FirebaseAuthMultiFactorException) {
                        Log.d("Login", "2FA is required. Handing off to MfaActivity.")
                        MfaActivity.mfaResolver = exception.resolver
                        val intent = Intent(this, MfaActivity::class.java)
                        startActivity(intent)
                    } else {
                        showToast("Authentication failed: ${exception?.message}")
                    }
                }
            }
    }

    private fun handleSuccessfulLogin() {
        val user = auth.currentUser ?: return
        val userRef = db.collection("users").document(user.uid)

        userRef.get().addOnSuccessListener { document ->
            val userName = document?.getString("name") ?: user.displayName ?: "User"
            proceedToLocationActivity(userName)
        }.addOnFailureListener {
            Log.e("Login", "Failed to fetch user document.", it)
            proceedToLocationActivity(user.displayName ?: "User")
        }
    }

    private fun signInWithGoogle() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        googleSignInLauncher.launch(googleSignInClient.signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                Log.d("Login", "Google Sign-In successful (no 2FA).")
                handleSuccessfulLogin() // Reuse the same success handler
            } else {
                val exception = task.exception
                if (exception is FirebaseAuthMultiFactorException) {
                    Log.d("Login", "2FA is required for Google user. Starting MFA flow.")
                    MfaActivity.mfaResolver = exception.resolver
                    val intent = Intent(this, MfaActivity::class.java)
                    startActivity(intent)
                } else {
                    Log.e("Login", "Google Auth Error", exception)
                    showToast("Google authentication failed: ${exception?.message}")
                }
            }
        }
    }

    private fun proceedToLocationActivity(userName: String) {
        val intent = Intent(this, LocationActivity::class.java).apply {
            putExtra("user_name", userName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}