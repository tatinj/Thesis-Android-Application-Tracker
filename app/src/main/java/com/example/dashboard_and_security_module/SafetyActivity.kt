package com.example.dashboard_and_security_module

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class SafetyActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "MyAppPrefs"
        private const val HOME_KEY = "home_location"
        private const val CURFEW_KEY = "curfew_times"
        private const val PERMISSION_REQUEST_CODE = 101
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var recyclerLocationLogs: RecyclerView
    private lateinit var adapter: MemberAdapter
    private val friendList = mutableListOf<Friend>()
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var tvCurrentAddress: TextView
    private lateinit var btnSetHome: Button
    private lateinit var tvMemberInfo: TextView
    private lateinit var btnSetCurfew: Button
    private lateinit var tvCurfewStatus: TextView
    private var selectedMember: Friend? = null

    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var homeLat = 0.0
    private var homeLon = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_safety)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        tvCurrentAddress = findViewById(R.id.tv_current_address)
        btnSetHome = findViewById(R.id.btn_set_home)
        recyclerView = findViewById(R.id.recycler_view_members)
        recyclerLocationLogs = findViewById(R.id.recycler_location_logs)
        tvMemberInfo = findViewById(R.id.tv_member_info)
        btnSetCurfew = findViewById(R.id.btn_set_curfew)
        tvCurfewStatus = findViewById(R.id.tv_curfew_status)

        adapter = MemberAdapter(friendList) { member -> displayMemberInfo(member) }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        recyclerLocationLogs.layoutManager = LinearLayoutManager(this)

        checkAndRequestPermissions()
        loadCachedHomeLocation()
        setupNavigation()
        loadFriends()

        btnSetHome.setOnClickListener {
            fetchCurrentLocation { lat, lon ->
                homeLat = lat
                homeLon = lon
                tvCurrentAddress.text = getAddressFromLocation(lat, lon)
                saveHomeLocation(lat, lon)
                Toast.makeText(this, "Home location saved!", Toast.LENGTH_SHORT).show()
            }
        }

        btnSetCurfew.setOnClickListener {
            selectedMember?.let { member -> showTimePicker(member) }
                ?: Toast.makeText(this, "Select a member first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun displayMemberInfo(member: Friend) {
        selectedMember = member
        tvMemberInfo.text = "Name: ${member.name}\nCode: ${member.code}\nPhone: ${member.phone}"

        // Load last 5 locations from Firestore
        loadLastFiveLocations(member.code)
    }

    private fun showTimePicker(member: Friend) {
        val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
            .setTitleText("Select Curfew Time")
            .setHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
            .setMinute(Calendar.getInstance().get(Calendar.MINUTE))
            .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_12H)
            .build()

        picker.show(supportFragmentManager, "curfew_time_picker")

        picker.addOnPositiveButtonClickListener {
            val hour = picker.hour
            val minute = picker.minute

            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)
            saveCurfewToCache(member.uid, timeStr)
            tvCurfewStatus.text = "Curfew Running until $timeStr"

            val delayMillis = maxOf(cal.timeInMillis - System.currentTimeMillis(), 0L)

            val data = Data.Builder()
                .putString("friendCode", member.code)
                .putString("friendName", member.name)
                .putDouble("homeLat", homeLat)
                .putDouble("homeLon", homeLon)
                .build()

            val work = OneTimeWorkRequestBuilder<CurfewWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()

            WorkManager.getInstance(this).enqueue(work)

            // Save last location log at curfew time
            fetchCurrentLocation { lat, lon ->
                val address = getAddressFromLocation(lat, lon)
                val timeStamp = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault()).format(Date())
                val log = LocationLog(lat, lon, address, timeStamp)

                saveLocationLog(member.code, log)
                displayLogs(member.code)
            }

            Toast.makeText(this, "Curfew set for $timeStr", Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------- LOCATION LOG FUNCTIONS -------------------------
    private fun saveLocationLog(friendCode: String, log: LocationLog) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val gson = com.google.gson.Gson()
        val existingJson = prefs.getString("logs_$friendCode", "[]")
        val type = object : com.google.gson.reflect.TypeToken<MutableList<LocationLog>>() {}.type
        val logs: MutableList<LocationLog> = gson.fromJson(existingJson, type)
        logs.add(0, log)
        logs.add(0, log)
        if (logs.size > 5) logs.removeAt(logs.lastIndex)

        prefs.edit().putString("logs_$friendCode", gson.toJson(logs)).apply()
    }

    private fun displayLogs(friendCode: String) {
        val logs = loadLocationLogs(friendCode)
        val logAdapter = LocationLogAdapter(logs)
        recyclerLocationLogs.adapter = logAdapter
    }

    private fun loadLocationLogs(friendCode: String): List<LocationLog> {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val gson = com.google.gson.Gson()
        val json = prefs.getString("logs_$friendCode", "[]")
        val type = object : com.google.gson.reflect.TypeToken<List<LocationLog>>() {}.type
        return gson.fromJson(json, type)
    }

    // ------------------------- LOCATION FUNCTIONS -------------------------
    private fun fetchCurrentLocation(callback: (Double, Double) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSION_REQUEST_CODE)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) callback(loc.latitude, loc.longitude)
            else requestNewLocation(callback)
        }
    }

    private fun requestNewLocation(callback: (Double, Double) -> Unit) {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMaxUpdates(1)
            .build()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.requestLocationUpdates(request, object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation
                if (loc != null) callback(loc.latitude, loc.longitude)
                fusedLocationClient.removeLocationUpdates(this)
            }
        }, mainLooper)
    }

    private fun getAddressFromLocation(lat: Double, lon: Double): String {
        val geocoder = Geocoder(this, Locale.getDefault())
        return try {
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) addresses[0].getAddressLine(0)
            else "Unknown Location"
        } catch (e: Exception) {
            "Unknown Location"
        }
    }

    // ------------------------- SHARED PREFS -------------------------
    private fun saveHomeLocation(lat: Double, lon: Double) {
        homeLat = lat
        homeLon = lon
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val map = mapOf("latitude" to lat, "longitude" to lon)
        prefs.edit().putString(HOME_KEY, com.google.gson.Gson().toJson(map)).apply()
    }

    private fun loadCachedHomeLocation() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val json = prefs.getString(HOME_KEY, null) ?: return
        val type = object : com.google.gson.reflect.TypeToken<Map<String, Double>>() {}.type
        val map: Map<String, Double> = com.google.gson.Gson().fromJson(json, type)
        homeLat = map["latitude"] ?: 0.0
        homeLon = map["longitude"] ?: 0.0
        tvCurrentAddress.text = getAddressFromLocation(homeLat, homeLon)
    }

    private fun saveCurfewToCache(memberCode: String, time: String) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val allCurfews = loadAllCurfewsFromCache().toMutableMap()
        allCurfews[memberCode] = time
        prefs.edit().putString(CURFEW_KEY, com.google.gson.Gson().toJson(allCurfews)).apply()
    }

    private fun loadAllCurfewsFromCache(): Map<String, String> {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val json = prefs.getString(CURFEW_KEY, null) ?: return emptyMap()
        val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
        return com.google.gson.Gson().fromJson(json, type)
    }

    // ------------------------- PERMISSIONS -------------------------
    private fun checkAndRequestPermissions() {
        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    // ------------------------- FRIENDS -------------------------
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
                    val memberUid = d.getString("uid") ?: ""
                    friendList.add(Friend(name, code, phone, lat, lon, battery, memberUid))
                }
                adapter.notifyDataSetChanged()
            }
    }

    private fun setupNavigation() {
        findViewById<ImageView>(R.id.location_section).setOnClickListener {
            startActivity(Intent(this, LocationActivity::class.java))
        }
        findViewById<ImageView>(R.id.friend_section).setOnClickListener {
            startActivity(Intent(this, MembersActivity::class.java))
        }
        findViewById<ImageView>(R.id.profile_section).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        findViewById<ImageView>(R.id.safety_section).setOnClickListener {
            Toast.makeText(this, "Already in Safety module", Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------- LAST 5 LOCATIONS -------------------------
    private fun loadLastFiveLocations(friendCode: String) {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).collection("friends")
            .whereEqualTo("code", friendCode)
            .get()
            .addOnSuccessListener { friendsDocs ->
                if (friendsDocs.isEmpty) {
                    Toast.makeText(this, "Friend not found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val friendDoc = friendsDocs.documents.first()
                val friendId = friendDoc.id

                db.collection("users").document(friendId)
                    .collection("history")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(5)
                    .get()
                    .addOnSuccessListener { locationDocs ->
                        val logs = mutableListOf<LocationLog>()

                        for (doc in locationDocs) {
                            val lat = doc.getDouble("latitude")
                            val lon = doc.getDouble("longitude")
                            val timestamp = doc.getLong("timestamp") ?: 0L

                            val geocoder = Geocoder(this, Locale.getDefault())
                            var address = "Unknown location"
                            try {
                                if (lat != null && lon != null) {
                                    val addresses = geocoder.getFromLocation(lat, lon, 1)
                                    if (!addresses.isNullOrEmpty()) address = addresses[0].getAddressLine(0)
                                }
                            } catch (e: Exception) { e.printStackTrace() }

                            val dateTime = SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.getDefault())
                                .format(Date(timestamp))

                            logs.add(LocationLog(lat ?: 0.0, lon ?: 0.0, address, dateTime))
                        }

                        recyclerLocationLogs.adapter = LocationLogAdapter(logs)
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to load locations", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to fetch friend", Toast.LENGTH_SHORT).show()
            }
    }
}

// ------------------------- DATA CLASS FOR LOCATION LOG -------------------------
data class LocationLog(
    val lat: Double,
    val lon: Double,
    val address: String,
    val timeStamp: String
)
