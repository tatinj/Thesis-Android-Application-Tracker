package com.example.dashboard_and_security_module

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import android.location.Geocoder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale

data class Friend(val name: String = "", val code: String = "", val latitude: Double? = null, val longitude: Double? = null)

class MembersActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "MyAppPrefs"
        private const val FRIENDS_KEY = "friends_list"
        private const val OFFLINE_PREFS = "OfflineLogin"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FriendAdapter
    private val friendList = mutableListOf<Friend>()
    private val friendListCache = mutableListOf<Friend>()

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var userCode: String

    private var isFriendsListLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_members)

        setupNavigation()

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val tvCode = findViewById<TextView>(R.id.tv_unique_code)
        val etFriendCode = findViewById<EditText>(R.id.et_friend_code)
        val btnAdd = findViewById<Button>(R.id.btn_add_friend)
        recyclerView = findViewById(R.id.recycler_view_friends)

        adapter = FriendAdapter(friendList) { friend -> fetchLastKnownLocation(friend.code) }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val currentUserUid = auth.currentUser?.uid
        if (currentUserUid == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        // Check internet before fetching invite code
        if (!isInternetAvailable()) {
            // No internet — check if they’ve logged in before
            val offlinePrefs = getSharedPreferences(OFFLINE_PREFS, MODE_PRIVATE)
            val loggedInBefore = offlinePrefs.getBoolean("logged_in_before", false)
            if (loggedInBefore) {
                Toast.makeText(this, "Offline mode: loading saved friends", Toast.LENGTH_SHORT).show()
                val cachedList = loadFriendsFromCache()
                friendList.clear()
                friendList.addAll(cachedList)
                adapter.notifyDataSetChanged()
                return
            } else {
                Toast.makeText(this, "No internet and no saved data", Toast.LENGTH_SHORT).show()
                return
            }
        }

        // Fetch invite code online
        db.collection("users")
            .document(currentUserUid)
            .collection("meta")
            .document("inviteCode")
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val code = document.getString("code")
                    if (!code.isNullOrEmpty()) {
                        userCode = code
                        tvCode.text = "Your Code: $code"
                        if (!isFriendsListLoaded) {
                            loadFriends()
                        }
                    } else {
                        tvCode.text = "Code not found"
                        Toast.makeText(this, "Invite code is empty", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    tvCode.text = "Code not found"
                    Toast.makeText(this, "Invite code document missing", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                tvCode.text = "Error fetching code"
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        btnAdd.setOnClickListener {
            val friendCode = etFriendCode.text.toString().trim()
            if (friendCode.isNotEmpty()) {
                addFriend(friendCode)
            } else {
                Toast.makeText(this, "Enter a valid code", Toast.LENGTH_SHORT).show()
            }
        }

        ensureFriendsCollectionExists()
    }

    private fun isInternetAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun loadFriends() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("friends")
            .get()
            .addOnSuccessListener { docs ->
                friendList.clear()
                for (doc in docs) {
                    val name = doc.getString("name") ?: "Unknown"
                    val code = doc.getString("code") ?: ""
                    val latitude = doc.getDouble("latitude")
                    val longitude = doc.getDouble("longitude")
                    friendList.add(Friend(name, code, latitude, longitude))
                }
                adapter.notifyDataSetChanged()

                // Save friends to cache
                saveFriendsToCache(friendList)

                // ✅ Save offline login flag + user data
                val offlinePrefs = getSharedPreferences(OFFLINE_PREFS, MODE_PRIVATE)
                offlinePrefs.edit()
                    .putBoolean("logged_in_before", true)
                    .putString("user_id", uid)
                    .putString("user_code", userCode)
                    .apply()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load friends", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveFriendsToCache(friends: List<Friend>) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val editor = prefs.edit()
        val json = Gson().toJson(friends)
        editor.putString(FRIENDS_KEY, json)
        editor.apply()
    }

    private fun loadFriendsFromCache(): List<Friend> {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val json = prefs.getString(FRIENDS_KEY, null) ?: return emptyList()
        val type = object : TypeToken<List<Friend>>() {}.type
        return Gson().fromJson(json, type)
    }

    private fun fetchLastKnownLocation(friendCode: String) {
        // Step 1: Find friend's user ID by invite code
        db.collection("users")
            .get()
            .addOnSuccessListener { usersSnapshot ->
                var friendUserId: String? = null

                for (userDoc in usersSnapshot) {
                    val userId = userDoc.id
                    db.collection("users").document(userId)
                        .collection("meta")
                        .document("inviteCode")
                        .get()
                        .addOnSuccessListener { inviteDoc ->
                            val code = inviteDoc.getString("code")
                            if (code == friendCode) {
                                friendUserId = userId

                                // Step 2: Now get friend's last known location from their history
                                getFriendLastLocation(friendUserId!!)
                            }
                        }
                }

                // Optional: handle case if friendUserId is not found after some delay
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to find friend user: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getFriendLastLocation(friendUserId: String) {
        val historyRef = db.collection("users")
            .document(friendUserId)
            .collection("history")

        historyRef.orderBy("location.timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents.first()
                    val locationMap = doc.get("location") as? Map<*, *>

                    val lat = locationMap?.get("latitude") as? Double
                    val lon = locationMap?.get("longitude") as? Double

                    if (lat != null && lon != null) {
                        // Use Geocoder to convert lat/lon to readable address
                        val geocoder = Geocoder(this, Locale.getDefault())
                        try {
                            val addresses = geocoder.getFromLocation(lat, lon, 1)
                            if (addresses != null && addresses.isNotEmpty()) {
                                val address = addresses[0]
                                val locationText = address.getAddressLine(0) ?: "Unknown location"
                                Toast.makeText(this, "Friend's last location: $locationText", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this, "Friend location found, but no address available", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this, "Failed to get address: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "No valid location found for friend", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "No location history available for friend", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error getting friend's location: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun ensureFriendsCollectionExists() {
        val currentUserUid = auth.currentUser?.uid
        if (currentUserUid != null) {
            val friendsCollectionRef = db.collection("users").document(currentUserUid).collection("friends")
            friendsCollectionRef.limit(1).get()
                .addOnSuccessListener { querySnapshot ->
                    if (querySnapshot.isEmpty) {
                        Log.d("MembersActivity", "No friends found, collection is empty.")
                    } else {
                        Log.d("MembersActivity", "Friends collection exists, no default friend needed.")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("MembersActivity", "Failed to check friends collection: ${e.message}")
                }
        }
    }

    private fun addFriend(friendCode: String) {
        val currentUid = auth.currentUser?.uid ?: return

        if (friendCode == userCode) {
            Toast.makeText(this, "You can't add yourself!", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("users").document(currentUid)
            .collection("friends").document(friendCode)
            .get()
            .addOnSuccessListener { existingDoc ->
                if (existingDoc.exists()) {
                    Toast.makeText(this, "Friend already added", Toast.LENGTH_SHORT).show()
                } else {
                    db.collection("users")
                        .get()
                        .addOnSuccessListener { usersSnapshot ->
                            var friendFound = false

                            for (userDoc in usersSnapshot) {
                                val userId = userDoc.id
                                val userName = userDoc.getString("name") ?: "Unknown"
                                val latitude = userDoc.getDouble("latitude")
                                val longitude = userDoc.getDouble("longitude")

                                db.collection("users").document(userId)
                                    .collection("meta").document("inviteCode")
                                    .get()
                                    .addOnSuccessListener { inviteDoc ->
                                        val code = inviteDoc.getString("code")

                                        if (code == friendCode) {
                                            val friendData = hashMapOf(
                                                "name" to userName,
                                                "code" to code,
                                                "latitude" to latitude,
                                                "longitude" to longitude
                                            )

                                            db.collection("users").document(currentUid)
                                                .collection("friends").document(code!!)
                                                .set(friendData)
                                                .addOnSuccessListener {
                                                    Toast.makeText(this, "Friend added!", Toast.LENGTH_SHORT).show()
                                                    // Update cache
                                                    friendListCache.add(Friend(userName, code, latitude, longitude))
                                                    friendList.add(Friend(userName, code, latitude, longitude))
                                                    adapter.notifyItemInserted(friendList.size - 1)
                                                }
                                            friendFound = true
                                        }
                                    }
                            }

                            recyclerView.postDelayed({
                                if (!friendFound) {
                                    Toast.makeText(this, "No user found with code: $friendCode", Toast.LENGTH_SHORT).show()
                                }
                            }, 2000)
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Failed to search users", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error checking existing friends", Toast.LENGTH_SHORT).show()
            }
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
