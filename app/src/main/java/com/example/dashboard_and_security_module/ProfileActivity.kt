package com.example.dashboard_and_security_module



import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.view.Window
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_profile)

        // Retrieve the user_name from the Intent
        val userName = intent.getStringExtra("user_name") ?: "User"

        // Set the username to the TextView
        val tvProfileName: TextView = findViewById(R.id.tv_profile_name)
        tvProfileName.text = userName  // Set the username here

        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val friendSection: ImageView = findViewById(R.id.friend_section)
        val locationSection: ImageView = findViewById(R.id.location_section)
        val safetySection: ImageView = findViewById(R.id.safety_section)

        friendSection.setOnClickListener {
            val intent = Intent(this, MembersActivity::class.java)
            startActivity(intent)
        }

        locationSection.setOnClickListener {
            val intent = Intent(this, LocationActivity::class.java)
            startActivity(intent)
        }

        safetySection.setOnClickListener {
            val intent = Intent(this, SafetyActivity::class.java)
            startActivity(intent)
        }

        val btnLogout: Button = findViewById(R.id.btn_log_out)
        btnLogout.setOnClickListener {
            signOut()
        }

        // Set date and time
        val tvDate: TextView = findViewById(R.id.tv_date)
        val tvTime: TextView = findViewById(R.id.tv_time)

        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        val currentDate = Date()
        tvDate.text = dateFormat.format(currentDate)
        tvTime.text = timeFormat.format(currentDate)
    }

    private fun signOut() {
        // 1. Firebase sign out
        auth.signOut()

        // 2. Google sign out
        googleSignInClient.signOut().addOnCompleteListener(this) {
            // 3. Clear offline login cache
            val offlinePrefs = getSharedPreferences("OfflineLogin", MODE_PRIVATE)
            offlinePrefs.edit().clear().apply()

            Toast.makeText(this, "Signed out successfully", Toast.LENGTH_SHORT).show()

            // 4. Redirect back to Login screen
            val intent = Intent(this, Login::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

}
