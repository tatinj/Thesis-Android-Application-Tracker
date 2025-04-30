package com.example.dashboard_and_security_module

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import android.view.Window
import android.view.WindowManager


class MainActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_main)


        // Initialize the Get Started button and set its listener
        val btnGetStarted: Button = findViewById(R.id.btn_get_started)
        btnGetStarted.setOnClickListener {
            // Redirect to the Login Activity when the button is clicked
            val intent = Intent(this, Login::class.java)
            startActivity(intent)
        }
    }
}
