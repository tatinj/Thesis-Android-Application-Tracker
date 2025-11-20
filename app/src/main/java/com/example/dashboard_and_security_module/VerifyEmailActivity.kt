package com.example.dashboard_and_security_module

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class VerifyEmailActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_email)

        auth = FirebaseAuth.getInstance()

        val btnContinue: Button = findViewById(R.id.btn_continue_to_app)
        val tvResendEmail: TextView = findViewById(R.id.tv_resend_email)
        val tvPrompt: TextView = findViewById(R.id.tv_verification_prompt)

        // Personalize the prompt message with the user's email
        val user = auth.currentUser
        if (user != null) {
            tvPrompt.text = "A verification link has been sent to ${user.email}. Please click the link to continue."
        }

        btnContinue.setOnClickListener {
            checkVerificationStatus()
        }

        tvResendEmail.setOnClickListener {
            resendVerificationEmail()
        }
    }

    private fun checkVerificationStatus() {
        val user = auth.currentUser
        if (user == null) {
            // This should not happen, but as a safeguard, send them to login.
            startActivity(Intent(this, Login::class.java))
            finish()
            return
        }

        // IMPORTANT: You must reload the user to get the latest status from Firebase servers.
        user.reload().addOnCompleteListener { reloadTask ->
            if (reloadTask.isSuccessful) {
                // After reloading, check the isEmailVerified property again.
                if (user.isEmailVerified) {
                    Toast.makeText(this, "Email verified successfully!", Toast.LENGTH_SHORT).show()
                    // Navigate to the main part of the app.
                    val intent = Intent(this, LocationActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this, "Your email is still not verified. Please check your inbox.", Toast.LENGTH_LONG).show()
                }
            } else {
                Log.e("VerifyEmailActivity", "Failed to reload user.", reloadTask.exception)
                Toast.makeText(this, "Failed to check status. Please check your internet connection and try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resendVerificationEmail() {
        val user = auth.currentUser
        user?.sendEmailVerification()
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "A new verification email has been sent.", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e("VerifyEmailActivity", "Failed to send verification email.", task.exception)
                    Toast.makeText(this, "Failed to send email. Please try again later.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // Handle the back press to prevent users from skipping verification
    override fun onBackPressed() {
        super.onBackPressed()
        // When back is pressed, sign the user out and send them to the login screen.
        auth.signOut()
        startActivity(Intent(this, Login::class.java))
        finish()
    }
}
