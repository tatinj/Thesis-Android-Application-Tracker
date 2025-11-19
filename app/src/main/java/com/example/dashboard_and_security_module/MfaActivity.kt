package com.example.dashboard_and_security_module

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import java.util.concurrent.TimeUnit

class MfaActivity : AppCompatActivity() {

    companion object {
        var mfaResolver: MultiFactorResolver? = null
    }

    private lateinit var resolver: MultiFactorResolver
    private lateinit var smsCodeEditText: EditText
    private lateinit var verifyButton: Button
    private var verificationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mfa)

        smsCodeEditText = findViewById(R.id.sms_code_edit_text)
        verifyButton = findViewById(R.id.verify_button)

        resolver = mfaResolver
            ?: run {
                Log.e("MfaActivity", "MultiFactorResolver was not set. Finishing activity.")
                finish()
                return
            }

        val phoneHint = resolver.hints.find { it is PhoneMultiFactorInfo } as? PhoneMultiFactorInfo
            ?: run {
                Toast.makeText(this, "No phone number hint found for 2FA.", Toast.LENGTH_LONG).show()
                finish()
                return
            }

        // --- KEY LOGIC: AUTOMATICALLY SEND THE CODE ---
        sendVerificationCode(phoneHint)
    }

    private fun sendVerificationCode(phoneInfo: PhoneMultiFactorInfo) {
        val options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
            // For a second-factor sign-in, the hint is required.
            .setMultiFactorHint(phoneInfo)
            .setMultiFactorSession(resolver.session)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onCodeSent(sentVerificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    Log.d("MfaActivity", "Verification code sent successfully.")
                    this@MfaActivity.verificationId = sentVerificationId
                    Toast.makeText(applicationContext, "Verification code sent.", Toast.LENGTH_SHORT).show()

                    // Now that the code is sent, set up the verify button listener
                    verifyButton.setOnClickListener {
                        val smsCode = smsCodeEditText.text.toString().trim()
                        if (smsCode.isNotEmpty()) {
                            val credential = PhoneAuthProvider.getCredential(sentVerificationId, smsCode)
                            val assertion = PhoneMultiFactorGenerator.getAssertion(credential)
                            resolveSignIn(resolver, assertion)
                        } else {
                            Toast.makeText(this@MfaActivity, "Please enter the SMS code.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Log.e("MfaActivity", "Phone auth verification failed", e)
                    Toast.makeText(applicationContext, "Verification failed: ${e.message}", Toast.LENGTH_LONG).show()
                }

                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    Log.d("MfaActivity", "Phone auth completed automatically.")
                    val multiFactorAssertion = PhoneMultiFactorGenerator.getAssertion(credential)
                    resolveSignIn(resolver, multiFactorAssertion)
                }
            })
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun resolveSignIn(resolver: MultiFactorResolver, assertion: MultiFactorAssertion) {
        resolver.resolveSignIn(assertion)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("MfaActivity", "2FA sign-in successful.")
                    val intent = Intent(this, LocationActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    Log.w("MfaActivity", "2FA sign-in resolution failed.", task.exception)
                    Toast.makeText(this, "Verification failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        mfaResolver = null
    }
}
