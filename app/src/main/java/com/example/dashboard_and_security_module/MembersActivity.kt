package com.example.dashboard_and_security_module

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.telephony.SmsManager
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.os.Bundle
import android.location.Geocoder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale




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

        // 🟢 Handle offline mode
        if (!isInternetAvailable()) {
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

                Log.d("MembersActivity", "✅ Cached friends loaded (${cachedFriends.size})")
            } else {
                Toast.makeText(this, "No internet and no cached data", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // 🟢 Load user code from Firestore (online)
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

    // 🔹 Request permissions if missing
    private fun checkAndRequestPermissions() {
        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    // 🔹 Show option dialog for "Use Internet" or "Offline Tracking"
    private fun showFindOptions(friend: Friend) {
        val options = arrayOf("Use Internet", "Offline Tracking (via SMS)")
        AlertDialog.Builder(this)
            .setTitle("Track ${friend.name}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> fetchLastKnownLocation(friend.code)
                    1 -> sendOfflineSMS(friend)
                }
            }
            .show()
    }

    // 🔹 Send SMS manually (works offline)
    private fun sendOfflineSMS(friend: Friend) {
        val phoneNumber = friend.phone
        if (phoneNumber.isEmpty() || phoneNumber == "No phone") {
            Toast.makeText(this, "No phone number found", Toast.LENGTH_SHORT).show()
            return
        }

        // ✅ Load user code from cache if needed
        val cachedUserCode = loadUserCodeFromCache()
        val activeUserCode = if (userCode.isNotEmpty()) userCode else cachedUserCode

        if (activeUserCode.isEmpty()) {
            Toast.makeText(this, "No user code found (online or cached)", Toast.LENGTH_SHORT).show()
            return
        }

        val message = "LOC_REQ:$activeUserCode"

        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Toast.makeText(this, "📩 Sent request via SMS ($message)", Toast.LENGTH_SHORT).show()
            Log.d("MembersActivity", "SMS sent to $phoneNumber: $message")
        } catch (e: Exception) {
            Toast.makeText(this, "SMS failed: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("MembersActivity", "SMS error: ${e.message}")
        }
    }

    // 🔹 Check if internet is available
    private fun isInternetAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // 🔹 Load friends from Firestore (online)
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
                    friendList.add(Friend(name, code, phone, lat, lon))
                }
                adapter.notifyDataSetChanged()
                saveFriendsToCache(friendList)
                getSharedPreferences(OFFLINE_PREFS, MODE_PRIVATE)
                    .edit().putBoolean("logged_in_before", true).apply()
            }
    }

    // 🔹 Cache friends list
    private fun saveFriendsToCache(friends: List<Friend>) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putString(FRIENDS_KEY, Gson().toJson(friends)).apply()
        Log.d("MembersActivity", "✅ Friends cached (${friends.size})")
        Toast.makeText(this, "Friends list saved to cache (${friends.size})", Toast.LENGTH_SHORT).show()
    }

    // 🔹 Cache user code
    private fun saveUserCodeToCache(code: String) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putString(USER_CODE_KEY, code).apply()
        Log.d("MembersActivity", "✅ User code cached: $code")
    }

    // 🔹 Load cached user code
    private fun loadUserCodeFromCache(): String {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return prefs.getString(USER_CODE_KEY, "") ?: ""
    }

    // 🔹 Load cached friends list
    private fun loadFriendsFromCache(): List<Friend> {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val json = prefs.getString(FRIENDS_KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<Friend>>() {}.type
        return Gson().fromJson(json, type)
    }

    // 🔹 Fetch last known location via Firestore
    private fun fetchLastKnownLocation(friendCode: String) {
        db.collection("users")
            .get()
            .addOnSuccessListener { users ->
                for (u in users) {
                    db.collection("users").document(u.id)
                        .collection("meta").document("inviteCode")
                        .get()
                        .addOnSuccessListener { invite ->
                            if (invite.getString("code") == friendCode) {
                                getFriendLastLocation(u.id)
                            }
                        }
                }
            }
    }

    // 🔹 Get location document and show it as address + map option
    private fun getFriendLastLocation(friendId: String) {
        val history = db.collection("users").document(friendId).collection("history")
        history.orderBy("location.timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.isEmpty) {
                    val doc = snap.documents.first()
                    val map = doc.get("location") as? Map<*, *>
                    val lat = map?.get("latitude") as? Double
                    val lon = map?.get("longitude") as? Double

                    if (lat != null && lon != null) {
                        db.collection("users").document(friendId).get()
                            .addOnSuccessListener { userDoc ->
                                val friendName = userDoc.getString("name") ?: "Friend"

                                // 🟢 Use Geocoder to get exact location name
                                val geocoder = Geocoder(this, Locale.getDefault())
                                var addressName = "Unknown location"
                                try {
                                    val addresses = geocoder.getFromLocation(lat, lon, 1)
                                    if (!addresses.isNullOrEmpty()) {
                                        addressName = addresses[0].getAddressLine(0)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }

                                // 🟢 Create popup dialog with full info
                                AlertDialog.Builder(this)
                                    .setTitle("📍 Location Found")
                                    .setMessage(
                                        "Friend: $friendName\n\n" +
                                                "Latitude: $lat\nLongitude: $lon\n\n" +
                                                "Exact Location: $addressName"
                                    )
                                    .setPositiveButton("Go to Map") { _, _ ->
                                        // ✅ Open map activity with data
                                        val intent = Intent(this, LocationActivity::class.java)
                                        intent.putExtra("friend_lat", lat)
                                        intent.putExtra("friend_lon", lon)
                                        intent.putExtra("friend_name", friendName)
                                        intent.putExtra("friend_address", addressName)
                                        startActivity(intent)
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .show()
                            }
                    } else {
                        Toast.makeText(this, "No valid location found", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "No location found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error fetching location", Toast.LENGTH_SHORT).show()
            }
    }



    // 🔹 Add a friend by their invite code and notify them
    private fun addFriend(friendCode: String) {
        val currentUid = auth.currentUser?.uid ?: return
        val cachedUserCode = loadUserCodeFromCache()
        val activeUserCode = if (userCode.isNotEmpty()) userCode else cachedUserCode

        // 🔸 Check if already in friendList
        if (friendList.any { it.code == friendCode }) {
            Toast.makeText(this, "Friend already added!", Toast.LENGTH_SHORT).show()
            return
        }

        // 🔸 Check Firestore first before adding
        val userFriendsRef = db.collection("users").document(currentUid).collection("friends")
        userFriendsRef.document(friendCode).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    Toast.makeText(this, "Friend already exists in Firestore!", Toast.LENGTH_SHORT).show()
                } else {
                    // 🔹 Continue searching user with this code
                    db.collection("users").get()
                        .addOnSuccessListener { users ->
                            for (user in users) {
                                db.collection("users").document(user.id)
                                    .collection("meta").document("inviteCode")
                                    .get()
                                    .addOnSuccessListener { invite ->
                                        val code = invite.getString("code")
                                        if (code == friendCode) {
                                            val name = user.getString("name") ?: "Unknown"
                                            val phone = user.getString("phone") ?: "No phone"
                                            val friend = Friend(name, code, phone)

                                            // ✅ Save friend to Firestore
                                            userFriendsRef.document(code)
                                                .set(friend)
                                                .addOnSuccessListener {
                                                    friendList.add(friend)
                                                    adapter.notifyItemInserted(friendList.size - 1)
                                                    saveFriendsToCache(friendList)
                                                    Toast.makeText(this, "Friend added!", Toast.LENGTH_SHORT).show()

                                                    // ✅ Notify via SMS
                                                    if (phone.isNotEmpty() && phone != "No phone") {
                                                        try {
                                                            val smsManager = SmsManager.getDefault()
                                                            val message = "CODE:$activeUserCode added you"
                                                            smsManager.sendTextMessage(phone, null, message, null, null)
                                                            Log.d("MembersActivity", "📩 Notified $phone: $message")
                                                            Toast.makeText(this, "Child notified via SMS", Toast.LENGTH_SHORT).show()
                                                        } catch (e: Exception) {
                                                            Toast.makeText(this, "SMS failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                                            Log.e("MembersActivity", "SMS error: ${e.message}")
                                                        }
                                                    } else {
                                                        Toast.makeText(this, "No phone number to notify", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                        }
                                    }
                            }
                        }
                }
            }
    }


    // 🔹 Bottom navigation
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
//10-10-25/7:21