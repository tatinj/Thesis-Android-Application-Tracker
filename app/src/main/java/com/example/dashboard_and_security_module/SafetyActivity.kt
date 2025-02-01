package com.example.dashboard_and_security_module

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge

class SafetyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_safety)

        val friendSection: ImageView = findViewById(R.id.friend_section)
        val locationSection: ImageView = findViewById(R.id.location_section)
        val profileSection: ImageView = findViewById(R.id.profile_section)

        friendSection.setOnClickListener {
            // Handle friend section click
            val intent = Intent(this, MembersActivity::class.java)
            startActivity(intent)
        }

        locationSection.setOnClickListener {
            // Handle location section click
            val intent = Intent(this, LocationActivity::class.java)
            startActivity(intent)
        }

        profileSection.setOnClickListener {
            // Handle profile section click
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }
    }
}
