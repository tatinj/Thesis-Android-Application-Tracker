package com.example.dashboard_and_security_module

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.view.Window
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class LocationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_location)

        val friendSection: ImageView = findViewById(R.id.friend_section)
        val safetySection: ImageView = findViewById(R.id.safety_section)
        val profileSection: ImageView = findViewById(R.id.profile_section)

        friendSection.setOnClickListener {
            // Handle friend section click
            val intent = Intent(this, MembersActivity::class.java)
            startActivity(intent)
        }


        safetySection.setOnClickListener {
            // Handle safety section click
            val intent = Intent(this, SafetyActivity::class.java)
            startActivity(intent)
        }

        profileSection.setOnClickListener {
            // Handle profile section click
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }
    }
}