package com.example.dashboard_and_security_module

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.telephony.SmsManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.location.Geocoder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date

class MembersActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "MyAppPrefs"
        private const val FRIENDS_KEY = "friends_list"
        private const val USER_CODE_KEY = "user_code"
        private const val OFFLINE_PREFS = "OfflineLogin"
        private const val PERMISSION_REQUEST_CODE = 101
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FriendAdapter
    private val friendList = mutableListOf<Friend>()
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var tvCode: TextView
    private var userCode: String = ""

    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    private var updateHandler: Handler? = null
    private var updateRunnable: Runnable? = null
    private var trackingDialog: AlertDialog? = null
    private var isMapOpen = false
    private var lastTimestamp: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_members)
        setupNavigation()

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        tvCode = findViewById(R.id.tv_unique_code)
        val etFriendCode = findViewById<EditText>(R.id.et_friend_code)
        val btnAdd = findViewById<Button>(R.id.btn_add_friend)
        recyclerView = findViewById(R.id.recycler_view_friends)

        adapter = FriendAdapter(friendList) { friend -> showFindOptions(friend) }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        checkAndRequestPermissions()

        val currentUserUid = auth.currentUser?.uid
        if (currentUserUid == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isInternetAvailable()) {
            loadCachedData()
            return
        }

        // Load user code online
        db.collection("users")
            .document(currentUserUid)
            .collection("meta")
            .document("inviteCode")
            .get()
            .addOnSuccessListener { doc ->
                val code = doc.getString("code")
                if (!code.isNullOrEmpty()) {
                    userCode = code
                    tvCode.text = "Your Code: $code"
                    saveUserCodeToCache(code)
                    loadFriends()
                } else {
                    tvCode.text = "Code not found"
                }
            }
            .addOnFailureListener {
                tvCode.text = "Error loading code"
            }

        btnAdd.setOnClickListener {
            val friendCode = etFriendCode.text.toString().trim()
            if (friendCode.isNotEmpty()) addFriend(friendCode)
            else Toast.makeText(this, "Enter a valid code", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestPermissions() {
        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun loadCachedData() {
        val prefs = getSharedPreferences(OFFLINE_PREFS, MODE_PRIVATE)
        val loggedInBefore = prefs.getBoolean("logged_in_before", false)
        if (loggedInBefore) {
            Toast.makeText(this, "Offline mode: loading cached data", Toast.LENGTH_SHORT).show()
            val cachedFriends = loadFriendsFromCache()
            val cachedCode = loadUserCodeFromCache()

            friendList.clear()
            friendList.addAll(cachedFriends)
            adapter.notifyDataSetChanged()
            tvCode.text = if (cachedCode.isNotEmpty()) "Your Code: $cachedCode" else "Code not found"
            userCode = cachedCode
        } else {
            Toast.makeText(this, "No internet and no cached data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveFriendsToCache(friends: List<Friend>) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putString(FRIENDS_KEY, Gson().toJson(friends)).apply()
    }

    private fun loadFriendsFromCache(): List<Friend> {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val json = prefs.getString(FRIENDS_KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<Friend>>() {}.type
        return Gson().fromJson(json, type)
    }

    private fun saveUserCodeToCache(code: String) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putString(USER_CODE_KEY, code).apply()
    }

    private fun loadUserCodeFromCache(): String {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return prefs.getString(USER_CODE_KEY, "") ?: ""
    }

    private fun isInternetAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun loadFriends() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("friends")
            .get()
            .addOnSuccessListener { docs ->
                friendList.clear()
                for (d in docs) {
                    val name = d.getString("name") ?: "Unknown"
                    val code = d.getString("code") ?: ""
                    val phone = d.getString("phone") ?: "No phone"
                    val lat = d.getDouble("latitude")
                    val lon = d.getDouble("longitude")
                    val battery = d.getLong("batteryPercentage")?.toInt()
                    friendList.add(Friend(name, code, phone, lat, lon, battery))
                }
                adapter.notifyDataSetChanged()
                saveFriendsToCache(friendList)
                getSharedPreferences(OFFLINE_PREFS, MODE_PRIVATE)
                    .edit().putBoolean("logged_in_before", true).apply()
            }
    }

    private fun addFriend(friendCode: String) {
        val currentUserUid = auth.currentUser?.uid ?: return

        db.collection("users")
            .get()
            .addOnSuccessListener { users ->
                var friendIdFound: String? = null
                for (u in users) {
                    val uid = u.id
                    db.collection("users").document(uid)
                        .collection("meta")
                        .document("inviteCode")
                        .get()
                        .addOnSuccessListener { invite ->
                            val code = invite.getString("code")
                            if (code == friendCode) {
                                friendIdFound = uid
                                if (uid == currentUserUid) {
                                    Toast.makeText(this, "❌ You cannot add yourself", Toast.LENGTH_SHORT).show()
                                    return@addOnSuccessListener
                                }

                                db.collection("users")
                                    .document(currentUserUid)
                                    .collection("friends")
                                    .document(uid)
                                    .get()
                                    .addOnSuccessListener { existing ->
                                        if (existing.exists()) {
                                            Toast.makeText(this, "⚠️ Already added!", Toast.LENGTH_SHORT).show()
                                            return@addOnSuccessListener
                                        }

                                        db.collection("users").document(uid)
                                            .get()
                                            .addOnSuccessListener { friendDoc ->
                                                val friendName = friendDoc.getString("name") ?: "Unknown"
                                                val phone = friendDoc.getString("phone") ?: "No phone"

                                                val friendData = hashMapOf(
                                                    "name" to friendName,
                                                    "code" to friendCode,
                                                    "phone" to phone
                                                )

                                                db.collection("users")
                                                    .document(currentUserUid)
                                                    .collection("friends")
                                                    .document(uid)
                                                    .set(friendData)
                                                    .addOnSuccessListener {
                                                        Toast.makeText(this, "✅ Family member added!", Toast.LENGTH_SHORT).show()
                                                        friendList.add(Friend(friendName, friendCode, phone, null, null, null))
                                                        adapter.notifyDataSetChanged()
                                                        saveFriendsToCache(friendList)
                                                    }
                                                    .addOnFailureListener {
                                                        Toast.makeText(this, "❌ Failed to add. Try again.", Toast.LENGTH_SHORT).show()
                                                    }
                                            }
                                    }
                                return@addOnSuccessListener
                            }
                        }
                }

                Handler(Looper.getMainLooper()).postDelayed({
                    if (friendIdFound == null) {
                        Toast.makeText(this, "❌ Invalid code. No user found.", Toast.LENGTH_SHORT).show()
                    }
                }, 800)
            }
    }

    // ---------------- TRACKING ----------------
    private fun showFindOptions(friend: Friend) {
        val options = arrayOf("Use Internet", "Offline Tracking (via SMS)")
        AlertDialog.Builder(this)
            .setTitle("Track ${friend.name}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> startTracking(friend)
                    1 -> sendOfflineSMS(friend)
                }
            }
            .show()
    }

    private fun sendOfflineSMS(friend: Friend) {
        val phoneNumber = friend.phone
        if (phoneNumber.isEmpty() || phoneNumber == "No phone") {
            Toast.makeText(this, "No phone number found", Toast.LENGTH_SHORT).show()
            return
        }
        val activeUserCode = if (userCode.isNotEmpty()) userCode else loadUserCodeFromCache()
        if (activeUserCode.isEmpty()) {
            Toast.makeText(this, "No user code found", Toast.LENGTH_SHORT).show()
            return
        }
        val message = "LOC_REQ:$activeUserCode"
        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Toast.makeText(this, "📩 Sent request via SMS ($message)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "SMS failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startTracking(friend: Friend) {
        db.collection("users")
            .get()
            .addOnSuccessListener { users ->
                for (u in users) {
                    db.collection("users").document(u.id)
                        .collection("meta").document("inviteCode")
                        .get()
                        .addOnSuccessListener { invite ->
                            if (invite.getString("code") == friend.code) {
                                trackFriendLive(u.id)
                                return@addOnSuccessListener
                            }
                        }
                }
            }
    }

    private fun trackFriendLive(friendId: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("📍 Tracking Family")

        val textView = TextView(this)
        textView.setPadding(20, 20, 20, 20)
        builder.setView(textView)

        builder.setNegativeButton("Stop Tracking") { _, _ -> stopTrackingFriend() }
        builder.setPositiveButton("Go to Map") { _, _ ->
            isMapOpen = true
            db.collection("users").document(friendId)
                .collection("history")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener { snap ->
                    if (!snap.isEmpty) {
                        val doc = snap.documents.first()
                        val lat = doc.getDouble("latitude")
                        val lon = doc.getDouble("longitude")
                        db.collection("users").document(friendId)
                            .get()
                            .addOnSuccessListener { userDoc ->
                                val friendName = userDoc.getString("name") ?: "Friend"
                                if (lat != null && lon != null) {
                                    val intent = Intent(this@MembersActivity, LocationActivity::class.java)
                                    intent.putExtra("friend_lat", lat)
                                    intent.putExtra("friend_lon", lon)
                                    intent.putExtra("friend_name", friendName)
                                    startActivity(intent)
                                } else {
                                    Toast.makeText(this@MembersActivity, "Family location not available", Toast.LENGTH_SHORT).show()
                                }
                            }
                    }
                }
        }

        builder.setCancelable(false)
        trackingDialog = builder.show()

        // Live updates with last known logic
        updateHandler = Handler(Looper.getMainLooper())
        var unchangedCounter = 0

        updateRunnable = object : Runnable {
            override fun run() {
                if (!isMapOpen) {
                    db.collection("users").document(friendId).collection("history")
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(1)
                        .get()
                        .addOnSuccessListener { snap ->
                            if (!snap.isEmpty) {
                                val doc = snap.documents.first()
                                val lat = doc.getDouble("latitude")
                                val lon = doc.getDouble("longitude")
                                val timestamp = doc.getLong("timestamp") ?: 0L

                                db.collection("users").document(friendId).get()
                                    .addOnSuccessListener { userDoc ->
                                        val friendName = userDoc.getString("name") ?: "Friend"
                                        val geocoder = Geocoder(this@MembersActivity, Locale.getDefault())
                                        var addressName = "Unknown location"
                                        try {
                                            val addresses = geocoder.getFromLocation(lat ?: 0.0, lon ?: 0.0, 1)
                                            if (!addresses.isNullOrEmpty()) addressName = addresses[0].getAddressLine(0)
                                        } catch (e: Exception) { e.printStackTrace() }

                                        val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                            .format(Date(timestamp))

                                        val message: String
                                        if (timestamp != lastTimestamp) {
                                            lastTimestamp = timestamp
                                            unchangedCounter = 0
                                            message = "Name: $friendName\nLatitude: $lat\nLongitude: $lon\nDate & Time: $dateTime\nExact Address: $addressName"
                                        } else {
                                            unchangedCounter += 5
                                            message = "Name: Last Known Location\nLatitude: $lat\nLongitude: $lon\nDate & Time: $dateTime"
                                        }

                                        textView.text = message
                                    }
                            }
                        }
                }
                updateHandler?.postDelayed(this, 5000) // refresh every 5s
            }
        }
        updateHandler?.post(updateRunnable!!)
    }

    private fun stopTrackingFriend() {
        updateHandler?.removeCallbacks(updateRunnable!!)
        updateRunnable = null
        updateHandler = null
        trackingDialog?.dismiss()
        trackingDialog = null
        Toast.makeText(this, "Stopped tracking", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        isMapOpen = false
    }

    private fun setupNavigation() {
        findViewById<ImageView>(R.id.location_section).setOnClickListener {
            startActivity(Intent(this, LocationActivity::class.java))
        }
        findViewById<ImageView>(R.id.safety_section).setOnClickListener {
            startActivity(Intent(this, SafetyActivity::class.java))
        }
        findViewById<ImageView>(R.id.profile_section).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }
}
