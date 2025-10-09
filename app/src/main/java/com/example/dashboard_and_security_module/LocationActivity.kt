package com.example.dashboard_and_security_module

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.view.Window
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class LocationActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var settingsClient: SettingsClient
    private val db = FirebaseFirestore.getInstance()

    private var isLocationFetched = false

    private companion object {
        const val PERMISSION_REQUEST_CODE = 1001
        const val REQUEST_CHECK_SETTINGS = 1002
        const val DEFAULT_ZOOM_LEVEL = 15.0
        const val MANILA_LAT = 14.5995
        const val MANILA_LON = 120.9842
        const val CHANNEL_ID = "sms_listener_channel"
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_location)

        // Initialize osmdroid config
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))

        setupMapView()
        setupLocationServices()

        // Request all necessary permissions at once
        checkAndRequestAllPermissions()

        setupNavigation()

        // 🔹 Check if this activity was opened to show a friend’s location
        val friendLat = intent.getDoubleExtra("friend_lat", Double.NaN)
        val friendLon = intent.getDoubleExtra("friend_lon", Double.NaN)
        val friendName = intent.getStringExtra("friend_name") ?: "Friend"

        if (!friendLat.isNaN() && !friendLon.isNaN()) {
            val friendPoint = GeoPoint(friendLat, friendLon)
            showUserAndFriendLocation(friendPoint, friendName)
        }
    }

    private fun setupMapView() {
        mapView = findViewById(R.id.map)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        setDefaultLocation()
    }

    private fun setupLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        settingsClient = LocationServices.getSettingsClient(this)

        locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }

    /** 🔐 Request All Permissions (Location, SMS, Notifications) */
    private fun checkAndRequestAllPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            checkLocationSettings()
            showWaitingForSmsNotification()
        }
    }

    /** 📡 Create persistent notification that app is waiting for SMS */
    private fun showWaitingForSmsNotification() {
        createNotificationChannel()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Listening for SMS")
            .setContentText("App is waiting for incoming messages.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // keeps it pinned like Messenger
            .setAutoCancel(false)

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "SMS Listener"
            val descriptionText = "Shows when the app is waiting for SMS messages"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun checkLocationSettings() {
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)

        settingsClient.checkLocationSettings(builder.build())
            .addOnSuccessListener {
                if (!isLocationFetched) getCurrentLocation()
            }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        exception.startResolutionForResult(this, REQUEST_CHECK_SETTINGS)
                    } catch (_: IntentSender.SendIntentException) {}
                } else {
                    showDefaultLocationWithMessage("Location services unavailable")
                }
            }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    storeLocationInFirebase(location)
                    centerMapOnLocation(GeoPoint(location.latitude, location.longitude), "Your Location")
                    isLocationFetched = true
                } else requestNewLocation()
            }
            .addOnFailureListener {
                showDefaultLocationWithMessage("Failed to get location")
            }
    }

    @SuppressLint("MissingPermission")
    private fun requestNewLocation() {
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    super.onLocationResult(locationResult)
                    locationResult.lastLocation?.let { location ->
                        storeLocationInFirebase(location)
                        centerMapOnLocation(GeoPoint(location.latitude, location.longitude), "Your Location")
                        fusedLocationClient.removeLocationUpdates(this)
                        isLocationFetched = true
                    }
                }
            },
            Looper.getMainLooper()
        )
    }

    private fun storeLocationInFirebase(location: android.location.Location) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            val timestamp = System.currentTimeMillis()
            val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

            val userLocation = mapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "timestamp" to timestamp,
                "actual_time" to formattedDate
            )

            val loginHistory = mapOf(
                "login_timestamp" to timestamp,
                "login_time" to formattedDate
            )

            val userHistory = mapOf(
                "location" to userLocation,
                "login_history" to loginHistory
            )

            db.collection("users").document(user.uid).collection("history")
                .add(userHistory)
                .addOnSuccessListener {
                    Toast.makeText(this, "Location and login history saved", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to save data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    /** 🟢 Shows your location + friend’s location on the same map */
    private fun showUserAndFriendLocation(friendPoint: GeoPoint, friendName: String) {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val userPoint = GeoPoint(location.latitude, location.longitude)

                    // Clear previous markers
                    mapView.overlays.clear()

                    // 🟢 Marker for YOU
                    val userMarker = Marker(mapView).apply {
                        position = userPoint
                        title = "Your Location"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }

                    // 🔵 Marker for FRIEND
                    val friendMarker = Marker(mapView).apply {
                        position = friendPoint
                        title = "$friendName’s Last Known Location"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }

                    // Add both markers
                    mapView.overlays.add(userMarker)
                    mapView.overlays.add(friendMarker)

                    // Zoom out so both are visible
                    val boundingBox = BoundingBox.fromGeoPoints(listOf(userPoint, friendPoint))
                    mapView.zoomToBoundingBox(boundingBox, true)
                    mapView.invalidate()
                } else {
                    Toast.makeText(this, "Unable to get your current location", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        }
    }

    private fun centerMapOnLocation(point: GeoPoint, title: String) {
        runOnUiThread {
            mapView.controller.animateTo(point, DEFAULT_ZOOM_LEVEL, 1500L)
            mapView.overlays.clear()
            Marker(mapView).apply {
                position = point
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                this.title = title
                mapView.overlays.add(this)
            }
            mapView.invalidate()
        }
    }

    private fun setDefaultLocation() {
        centerMapOnLocation(GeoPoint(MANILA_LAT, MANILA_LON), "Manila, Philippines")
    }

    private fun showDefaultLocationWithMessage(message: String) {
        Toast.makeText(this, "$message. Showing default location.", Toast.LENGTH_LONG).show()
        setDefaultLocation()
    }

    private fun setupNavigation() {
        findViewById<ImageView>(R.id.friend_section).setOnClickListener {
            startActivity(Intent(this, MembersActivity::class.java))
        }
        findViewById<ImageView>(R.id.safety_section).setOnClickListener {
            startActivity(Intent(this, SafetyActivity::class.java))
        }
        findViewById<ImageView>(R.id.profile_section).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
                checkLocationSettings()
                showWaitingForSmsNotification()
            } else {
                Toast.makeText(this, "Some permissions denied. SMS or GPS may not work.", Toast.LENGTH_LONG).show()
                showDefaultLocationWithMessage("Limited functionality")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CHECK_SETTINGS && resultCode == RESULT_OK) {
            getCurrentLocation()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}
