package com.example.dashboard_and_security_module

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.*

@Suppress("DEPRECATION")
class LocationActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var settingsClient: SettingsClient

    private var lastSavedTime: Long = 0

    private val db = FirebaseFirestore.getInstance()

    private lateinit var locationHandler: Handler
    private lateinit var locationRunnable: Runnable
    private val refreshRate: Long = 5000 // 5-second Firebase update

    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null
    private var isFirstLocationUpdate = true
    private var mapLocked = false

    private lateinit var friendInfoTv: TextView
    private var userMarker: Marker? = null
    private var userTrail: Polyline? = null

    private companion object {
        const val PERMISSION_REQUEST_CODE = 1001
        const val REQUEST_CHECK_SETTINGS = 1002
        const val DEFAULT_ZOOM_LEVEL = 19.0
        const val MANILA_LAT = 14.5995
        const val MANILA_LON = 120.9842
        const val CHANNEL_ID = "sms_listener_channel"
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_location)

        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
        friendInfoTv = findViewById(R.id.friend_info_tv)

        setupMapView()
        setupTrail() // initialize the trail
        setupLocationServices()
        checkAndRequestPermissions()
        setupNavigation()

        val friendLat = intent.getDoubleExtra("friend_lat", Double.NaN)
        val friendLon = intent.getDoubleExtra("friend_lon", Double.NaN)
        val friendName = intent.getStringExtra("friend_name") ?: "Friend"

        if (!friendLat.isNaN() && !friendLon.isNaN()) {
            mapLocked = true
            val friendPoint = GeoPoint(friendLat, friendLon)
            mapView.controller.setZoom(DEFAULT_ZOOM_LEVEL)
            mapView.controller.animateTo(friendPoint)
            addFriendMarker(friendPoint, friendName)
            updateFriendInfo(friendName, friendLat, friendLon)
            startUserLocationUpdates()
        } else {
            startUserLocationUpdates()
        }
    }

    private fun setupMapView() {
        mapView = findViewById(R.id.map)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
    }

    private fun setupTrail() {
        userTrail = Polyline().apply {
            width = 8f
            color = 0xAAFF0000.toInt() // semi-transparent red
        }
        mapView.overlays.add(userTrail)
    }

    private fun setupLocationServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        settingsClient = LocationServices.getSettingsClient(this)
        locationRequest = LocationRequest.create().apply {
            interval = refreshRate
            fastestInterval = refreshRate
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            checkLocationSettings()
            startLocationRefresh()
            showWaitingForSmsNotification()
        }
    }

    private fun showWaitingForSmsNotification() {
        createNotificationChannel()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Listening for SMS")
            .setContentText("App is waiting for incoming messages.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setAutoCancel(false)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "SMS Listener", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun checkLocationSettings() {
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        settingsClient.checkLocationSettings(builder.build())
            .addOnSuccessListener { startUserLocationUpdates() }
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
    private fun startUserLocationUpdates() {
        if (!checkLocationPermission()) return

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    currentLatitude = loc.latitude
                    currentLongitude = loc.longitude

                    updateUserMarker(loc.latitude, loc.longitude)
                    saveLocationToDatabase() // save location every 5 seconds
                }
            },
            Looper.getMainLooper()
        )
    }

    private fun updateUserMarker(latitude: Double, longitude: Double) {
        val point = GeoPoint(latitude, longitude)

        if (userMarker == null) {
            userMarker = Marker(mapView).apply {
                position = point
                title = "Your Location"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(userMarker)
        } else {
            userMarker?.position = point
        }

        // Add point to trail
        userTrail?.let { polyline ->
            val points = polyline.actualPoints
            points.add(point)
            polyline.setPoints(points)
        }

        if (!mapLocked && isFirstLocationUpdate) {
            mapView.controller.animateTo(point, DEFAULT_ZOOM_LEVEL, 500)
            isFirstLocationUpdate = false
        }

        mapView.invalidate()
    }

    private fun saveLocationToDatabase() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val uid = user.uid
        val lat = currentLatitude ?: return
        val lon = currentLongitude ?: return

        val now = System.currentTimeMillis()

        // Only save 1 location per 5 seconds
        if (now - lastSavedTime < 5000) return
        lastSavedTime = now

        val data = mapOf(
            "latitude" to lat,
            "longitude" to lon,
            "timestamp" to now
        )

        db.collection("users").document(uid).collection("history")
            .add(data)
            .addOnSuccessListener {
                cleanupOldLocations(uid) // keep cleanup logic
            }
    }

    private fun cleanupOldLocations(uid: String) {
        db.collection("users").document(uid).collection("history")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snaps ->
                if (snaps.size() > 30) {
                    val toDelete = snaps.documents.drop(30)
                    toDelete.forEach { it.reference.delete() }
                }
            }
    }

    private fun startLocationRefresh() {
        locationHandler = Handler(Looper.getMainLooper())
        locationRunnable = object : Runnable {
            override fun run() {
                refreshFriendInfo()
                locationHandler.postDelayed(this, refreshRate)
            }
        }
        locationHandler.post(locationRunnable)
    }

    private fun refreshFriendInfo() {
        val friendLat = intent.getDoubleExtra("friend_lat", Double.NaN)
        val friendLon = intent.getDoubleExtra("friend_lon", Double.NaN)
        val friendName = intent.getStringExtra("friend_name") ?: "Friend"
        if (!friendLat.isNaN() && !friendLon.isNaN()) {
            updateFriendInfo(friendName, friendLat, friendLon)
        }
    }

    private fun updateFriendInfo(name: String, lat: Double, lon: Double, timestamp: Long? = null) {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val timeText = if (timestamp != null) sdf.format(Date(timestamp)) else sdf.format(Date())

        var addressText = "Unknown location"
        try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                addressText = buildString {
                    if (!addr.thoroughfare.isNullOrEmpty()) append("${addr.thoroughfare}, ")
                    if (!addr.subLocality.isNullOrEmpty()) append("${addr.subLocality}, ")
                    if (!addr.locality.isNullOrEmpty()) append("${addr.locality}, ")
                    if (!addr.adminArea.isNullOrEmpty()) append("${addr.adminArea}, ")
                    if (!addr.countryName.isNullOrEmpty()) append("${addr.countryName}")
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        val infoText = "Family Member: $name | Address: $addressText | Time: $timeText"
        runOnUiThread { friendInfoTv.text = infoText }
    }

    private fun checkLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED && coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun addFriendMarker(point: GeoPoint, name: String) {
        val marker = Marker(mapView).apply {
            position = point
            title = name
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        mapView.overlays.add(marker)
        mapView.invalidate()
    }

    private fun showDefaultLocationWithMessage(message: String) {
        Toast.makeText(this, "$message. Showing default location.", Toast.LENGTH_LONG).show()
        val manila = GeoPoint(MANILA_LAT, MANILA_LON)
        mapView.controller.setZoom(DEFAULT_ZOOM_LEVEL)
        mapView.controller.animateTo(manila)
    }

    private fun setupNavigation() {
        findViewById<ImageView>(R.id.friend_section).setOnClickListener {
            mapLocked = false
            isFirstLocationUpdate = true
            startActivity(Intent(this, MembersActivity::class.java))
        }
        findViewById<ImageView>(R.id.safety_section).setOnClickListener {
            mapLocked = false
            isFirstLocationUpdate = true
            startActivity(Intent(this, SafetyActivity::class.java))
        }
        findViewById<ImageView>(R.id.profile_section).setOnClickListener {
            mapLocked = false
            isFirstLocationUpdate = true
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }

    override fun onDestroy() {
        if (::locationHandler.isInitialized) locationHandler.removeCallbacks(locationRunnable)
        fusedLocationClient.removeLocationUpdates(object : LocationCallback() {})
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                checkLocationSettings()
                startLocationRefresh()
                showWaitingForSmsNotification()
            } else showDefaultLocationWithMessage("Limited functionality")
        }
    }
}
