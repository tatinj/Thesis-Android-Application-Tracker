package com.example.dashboard_and_security_module

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_main)

        val offlinePrefs = getSharedPreferences("OfflineLogin", MODE_PRIVATE)
        val loggedInBefore = offlinePrefs.getBoolean("logged_in_before", false)

        // ✅ Internet check #1: OnCreate (auto-redirect if logged in before)
        if (loggedInBefore) {
            if (isInternetAvailable()) {
                // User has internet & already logged in → go straight to LocationActivity
                startActivity(Intent(this, LocationActivity::class.java))
            } else {
                // No internet but logged in before → still allow offline access
                startActivity(Intent(this, LocationActivity::class.java))
            }
            finish()
            return
        }

        // ✅ Otherwise show Get Started button (for first-time login)
        val btnGetStarted: Button = findViewById(R.id.btn_get_started)

        btnGetStarted.setOnClickListener {
            // ✅ Internet check #2: Every button click
            if (isInternetAvailable()) {
                // Go to Login if internet is available
                startActivity(Intent(this, Login::class.java))
            } else {
                // Block first-time users without internet
                Toast.makeText(
                    this,
                    "No internet connection. Please connect to log in.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun isInternetAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        return activeNetwork != null && activeNetwork.isConnected
    }
}
