
package com.example.dashboard_and_security_module

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

class Login : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "MyAppPrefs"
        private const val FRIENDS_KEY = "friends_list"
    }

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
            startActivity(Intent(this, registration::class.java))
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
            "timestamps" to currentTime,
            "LoginDate" to formattedDate
        )
    }

    private fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { authTask ->
                if (authTask.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                        var inviteCode = sharedPref.getString("invite_code", null)

                        if (inviteCode == null) {
                            inviteCode = generateUniqueCode()
                            sharedPref.edit().putString("invite_code", inviteCode).apply()

                            val inviteRef = db.collection("users").document(user.uid)
                                .collection("meta").document("inviteCode")
                            inviteRef.set(mapOf("code" to inviteCode))
                        }

                        val userRef = db.collection("users").document(user.uid)
                        userRef.get().addOnSuccessListener { document ->
                            if (document.exists()) {
                                val userName = document.getString("name") ?: "User"

                                // ✅ Save for offline login
                                val offlinePrefs = getSharedPreferences("OfflineLogin", MODE_PRIVATE)
                                offlinePrefs.edit()
                                    .putBoolean("logged_in_before", true)
                                    .putString("user_id", user.uid)
                                    .putString("user_name", userName)
                                    .apply()

                                val intent = Intent(this, LocationActivity::class.java)
                                intent.putExtra("user_name", userName)
                                startActivity(intent)
                                finish()
                            } else {
                                showToast("User data not found")
                            }
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
                    val sharedPref = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                    var inviteCode = sharedPref.getString("invite_code", null)

                    if (inviteCode == null) {
                        inviteCode = generateUniqueCode()
                        sharedPref.edit().putString("invite_code", inviteCode).apply()

                        val inviteRef = db.collection("users").document(user.uid)
                            .collection("meta").document("inviteCode")
                        inviteRef.set(mapOf("code" to inviteCode))
                            .addOnSuccessListener {
                                Log.d("InviteCode", "Invite code saved successfully")
                            }
                            .addOnFailureListener { e ->
                                Log.e("InviteCode", "Failed to save invite code: ${e.message}")
                            }
                    } else {
                        Log.d("Cache", "Using cached invite code: $inviteCode")
                    }

                    val loginHistoryRef = db.collection("users").document(user.uid).collection("history")
                    loginHistoryRef.add(getCurrentDateTime())
                        .addOnSuccessListener {
                            showToast("Google login successful")
                            proceedToLocationActivity()
                        }
                        .addOnFailureListener {
                            showToast("Google login successful (time not recorded)")
                            proceedToLocationActivity()
                        }
                }
            } else {
                showToast("Google authentication failed")
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showToast("Location permission granted")
            } else {
                showToast("Location permission denied - using default location")
            }
            proceedToLocationActivity()
        }
    }

    private fun proceedToLocationActivity() {
        startActivity(Intent(this, LocationActivity::class.java))
        finish()
    }

    private fun generateUniqueCode(): String {
        return List(6) {
            (('A'..'Z') + ('0'..'9')).random()
        }.joinToString("")
    }
}
