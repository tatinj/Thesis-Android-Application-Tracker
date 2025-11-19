package com.example.dashboard_and_security_module

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import java.util.concurrent.TimeUnit

class EnrollMfaActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private var verificationId: String? = null

    // UI Components
    private lateinit var phoneNumberField: EditText
    private lateinit var codeField: EditText
    private lateinit var sendCodeButton: Button
    private lateinit var verifyButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enroll_mfa)

        phoneNumberField = findViewById(R.id.phone_number_field)
        codeField = findViewById(R.id.code_field)
        sendCodeButton = findViewById(R.id.send_code_button)
        verifyButton = findViewById(R.id.verify_enroll_button)

        sendCodeButton.setOnClickListener {
            val phoneNumber = phoneNumberField.text.toString().trim()
            if (phoneNumber.isNotBlank()) {
                // --- KEY CHANGE: NORMALIZE THE NUMBER ---
                val normalizedPhone = normalizePhoneNumber(phoneNumber)
                if (normalizedPhone.isEmpty()) {
                    Toast.makeText(this, "Invalid phone number format. Please use 09...", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                // Pass normalized number to start verification
                startPhoneNumberVerification(normalizedPhone)
            } else {
                Toast.makeText(this, "Please enter a phone number.", Toast.LENGTH_SHORT).show()
            }
        }

        verifyButton.setOnClickListener {
            val code = codeField.text.toString().trim()
            if (verificationId != null && code.isNotBlank()) {
                val credential = PhoneAuthProvider.getCredential(verificationId!!, code)
                enrollSecondFactor(credential)
            } else {
                Toast.makeText(this, "Please verify your phone number first.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // KINOCONVERT YUNG PH LOCAL NUMBER INTO +63 YEA YEA
    private fun normalizePhoneNumber(number: String): String {
        val digitsOnly = number.filter { it.isDigit() }
        return when {
            digitsOnly.startsWith("09") && digitsOnly.length == 11 -> "+63" + digitsOnly.substring(1)
            digitsOnly.startsWith("9") && digitsOnly.length == 10 -> "+63$digitsOnly"
            number.startsWith("+639") && number.length == 13 -> number
            digitsOnly.startsWith("639") && digitsOnly.length == 12 -> "+$digitsOnly"
            else -> "" // Return empty for invalid formats
        }
    }

    private fun startPhoneNumberVerification(phoneNumber: String) { // NIRERECEIVE YUNG KINONVERT
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "You must be logged in to enroll.", Toast.LENGTH_SHORT).show()
            return
        }

        user.multiFactor.getSession().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val multiFactorSession = task.result
                val options = PhoneAuthOptions.newBuilder(auth)
                    .setPhoneNumber(phoneNumber)
                    .setTimeout(60L, TimeUnit.SECONDS)
                    .setActivity(this)
                    .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                        override fun onCodeSent(sentVerificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                            this@EnrollMfaActivity.verificationId = sentVerificationId
                            Toast.makeText(applicationContext, "Verification code sent.", Toast.LENGTH_SHORT).show()
                            // Optional: Show the code field now
                            codeField.visibility = View.VISIBLE
                            verifyButton.visibility = View.VISIBLE
                        }

                        override fun onVerificationFailed(e: FirebaseException) {
                            Log.w("EnrollMFA", "Phone auth failed", e)
                            Toast.makeText(applicationContext, "Verification failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }

                        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                            enrollSecondFactor(credential)
                        }
                    })
                    .setMultiFactorSession(multiFactorSession)
                    .build()

                PhoneAuthProvider.verifyPhoneNumber(options)
            } else {
                Log.w("EnrollMFA", "Failed to get MFA session", task.exception)
                Toast.makeText(this, "Failed to prepare for 2FA enrollment: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun enrollSecondFactor(credential: PhoneAuthCredential) {
        val mfaAssertion = PhoneMultiFactorGenerator.getAssertion(credential)

        auth.currentUser?.multiFactor?.enroll(mfaAssertion, "My Phone Number")
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "2FA enabled successfully!", Toast.LENGTH_SHORT).show()
                    finish() // Close the enrollment activity
                } else {
                    Log.w("EnrollMFA", "Failed to enroll 2FA", task.exception)
                    Toast.makeText(this, "Failed to enable 2FA: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }
}
