package com.example.dashboard_and_security_module

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import java.util.concurrent.TimeUnit

class MfaActivity : AppCompatActivity() {

    companion object {
        var mfaResolver: MultiFactorResolver? = null
    }

    // UI Components
    private lateinit var smsCodeLayout: TextInputLayout
    private lateinit var smsCodeEditText: TextInputEditText
    private lateinit var verifyButton: Button
    private lateinit var resendCodeTextView: TextView
    private lateinit var resendTimerTextView: TextView
    private lateinit var phoneNumberHintTextView: TextView // For displaying the masked number

    // Firebase and state
    private lateinit var resolver: MultiFactorResolver
    private lateinit var phoneInfo: PhoneMultiFactorInfo
    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var countdownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mfa)

        // --- Initialize UI components ---
        smsCodeLayout = findViewById(R.id.sms_code_layout)
        smsCodeEditText = findViewById(R.id.sms_code_edit_text)
        verifyButton = findViewById(R.id.verify_button)
        resendCodeTextView = findViewById(R.id.tv_resend_code)
        resendTimerTextView = findViewById(R.id.tv_resend_timer)
        phoneNumberHintTextView = findViewById(R.id.tv_phone_number_hint) // Initialize the new TextView

        resolver = mfaResolver
            ?: run {
                Log.e("MfaActivity", "MultiFactorResolver was not set. Finishing activity.")
                finish()
                return
            }

        phoneInfo = resolver.hints.find { it is PhoneMultiFactorInfo } as? PhoneMultiFactorInfo
            ?: run {
                Toast.makeText(this, "No phone number hint found for 2FA.", Toast.LENGTH_LONG).show()
                finish()
                return
            }

        // Display the masked phone number to the user
        phoneNumberHintTextView.text = "Sent to: ${phoneInfo.phoneNumber}"

        // --- Automatically send the initial code ---
        sendVerificationCode(phoneInfo)

        // Set up the resend button listener
        resendCodeTextView.setOnClickListener {
            resendVerificationCode()
        }
    }

    private fun sendVerificationCode(phoneInfo: PhoneMultiFactorInfo, token: PhoneAuthProvider.ForceResendingToken? = null) {
        val optionsBuilder = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
            .setMultiFactorHint(phoneInfo)
            .setMultiFactorSession(resolver.session)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onCodeSent(sentVerificationId: String, forceResendingToken: PhoneAuthProvider.ForceResendingToken) {
                    Log.d("MfaActivity", "Verification code sent successfully.")
                    this@MfaActivity.verificationId = sentVerificationId
                    this@MfaActivity.resendToken = forceResendingToken // Store the token for resending
                    Toast.makeText(applicationContext, "Verification code sent.", Toast.LENGTH_SHORT).show()

                    startResendTimer() // Start the countdown

                    // We set the listener here, after we know the code has been sent.
                    verifyButton.setOnClickListener {
                        val smsCode = smsCodeEditText.text.toString().trim()
                        if (smsCode.length != 6) {
                            smsCodeLayout.error = "The code must be 6 digits."
                            return@setOnClickListener
                        } else {
                            smsCodeLayout.error = null
                        }
                        verifySmsCode(sentVerificationId, smsCode)
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

        // If we have a resend token, add it to the options
        if (token != null) {
            optionsBuilder.setForceResendingToken(token)
        }

        PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
    }

    private fun resendVerificationCode() {
        if (resendToken == null) {
            Toast.makeText(this, "Cannot resend code at this time.", Toast.LENGTH_SHORT).show()
            return
        }
        sendVerificationCode(phoneInfo, resendToken)
        Toast.makeText(this, "Resending verification code...", Toast.LENGTH_SHORT).show()
    }

    private fun startResendTimer() {
        resendCodeTextView.visibility = View.GONE
        resendTimerTextView.visibility = View.VISIBLE

        countdownTimer?.cancel() // Cancel any existing timer

        countdownTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                resendTimerTextView.text = "Resend code in ${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                resendTimerTextView.visibility = View.GONE
                resendCodeTextView.visibility = View.VISIBLE
            }
        }.start()
    }

    private fun verifySmsCode(verificationId: String, smsCode: String) {
        val credential = PhoneAuthProvider.getCredential(verificationId, smsCode)
        val multiFactorAssertion = PhoneMultiFactorGenerator.getAssertion(credential)
        resolveSignIn(resolver, multiFactorAssertion)
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
                    smsCodeLayout.error = "The code you entered is incorrect."
                    Toast.makeText(this, "Verification failed. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        mfaResolver = null
        countdownTimer?.cancel() // Prevent memory leaks
    }
}
