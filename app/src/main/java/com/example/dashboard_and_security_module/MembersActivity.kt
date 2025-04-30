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

class MembersActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_members)

        fetchUserUniqueCode()

        val locationSection: ImageView = findViewById(R.id.location_section)
        val safetySection: ImageView = findViewById(R.id.safety_section)
        val profileSection: ImageView = findViewById(R.id.profile_section)



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

    private fun fetchUserUniqueCode(){}

}