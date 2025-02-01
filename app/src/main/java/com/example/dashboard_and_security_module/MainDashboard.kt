package com.example.dashboard_and_security_module

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainDashboard : AppCompatActivity() {
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_dashboard)

        val friendSection: ImageView = findViewById(R.id.friend_section)
        val locationSection: ImageView = findViewById(R.id.location_section)
        val safetySection: ImageView = findViewById(R.id.safety_section)
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