package com.example.dashboard_and_security_module

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.firebase.firestore.DocumentSnapshot

class Login : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>
    private val db = FirebaseFirestore.getInstance()
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

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

        btnLogin.setOnClickListener {
            val email = emailField.text?.toString()?.trim() ?: ""
            val password = passwordField.text?.toString()?.trim() ?: ""

            when {
                email.isEmpty() -> showToast("Email cannot be empty")
                !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> showToast("Invalid email format")
                password.isEmpty() -> showToast("Password cannot be empty")
                password.length < 6 -> showToast("Password must be at least 6 characters")
                else -> loginUser(email, password)
            }
        }

        tvRegister.setOnClickListener {
            val intent = Intent(this, registration::class.java)
            startActivity(intent)
        }

        googleSignInButton.setOnClickListener {
            signInWithGoogle()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun getCurrentDateTime(): Map<String, Any> {
        val currentTime = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(currentTime))
        return mapOf(
            "timestamps " to currentTime,
            "LoginDate" to formattedDate
        )
    }

    private fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { authTask ->
                if (authTask.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        // Fetch user's name from Firestore
                        val userRef = db.collection("users").document(user.uid)

                        // Retrieve the user's name from Firestore
                        userRef.get().addOnSuccessListener { document ->
                            if (document.exists()) {
                                val userName = document.getString("name") ?: "User"

                                // Pass the user's name to the ProfileActivity
                                val intent = Intent(this, ProfileActivity::class.java)
                                intent.putExtra("user_name", userName)  // Add user name to intent
                                startActivity(intent)
                                finish()  // Close the login activity after successful login
                            } else {
                                Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show()
                            }
                        }.addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to retrieve user data: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    showToast("Authentication failed: ${authTask.exception?.message}")
                }
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
                val user = auth.currentUser
                if (user != null) {
                    val loginHistoryRef = db.collection("users").document(user.uid).collection("history")
                    loginHistoryRef.add(getCurrentDateTime())
                        .addOnSuccessListener {
                            showToast("Google login successful")
                            proceedToLocationActivity()
                        }
                        .addOnFailureListener { e ->
                            showToast("Google login successful (time not recorded)")
                            proceedToLocationActivity()
                        }
                }
            } else {
                showToast("Google authentication failed")
            }
        }
    }

    private fun checkLocationPermissionAndProceed() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            proceedToLocationActivity()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    showToast("Location permission granted")
                } else {
                    showToast("Location permission denied - using default location")
                }
                proceedToLocationActivity()
            }
        }
    }

    private fun proceedToLocationActivity() {
        startActivity(Intent(this, LocationActivity::class.java))
        finish()
    }
}