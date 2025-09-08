package com.example.dashboard_and_security_module

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.Window
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class LocationActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var settingsClient: SettingsClient
    private val db = FirebaseFirestore.getInstance()

    // Flag to track if location is already fetched
    private var isLocationFetched = false

    private companion object {
        const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        const val REQUEST_CHECK_SETTINGS = 1002
        const val DEFAULT_ZOOM_LEVEL = 15.0
        const val MANILA_LAT = 14.5995
        const val MANILA_LON = 120.9842
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_location)

        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))

        setupMapView()
        setupLocationServices()
        checkLocationAvailability()
        setupNavigation()
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
            interval = 10000 // 10 seconds
            fastestInterval = 5000 // 5 seconds
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }

    private fun checkLocationAvailability() {
        when {
            hasLocationPermission() -> checkLocationSettings()
            else -> requestLocationPermission()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun checkLocationSettings() {
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        settingsClient.checkLocationSettings(builder.build())
            .addOnSuccessListener {
                if (!isLocationFetched) {
                    getCurrentLocation()
                }
            }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        exception.startResolutionForResult(this, REQUEST_CHECK_SETTINGS)
                    } catch (sendEx: IntentSender.SendIntentException) {
                    }
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
                    centerMapOnLocation(
                        GeoPoint(location.latitude, location.longitude),
                        "Your Location"
                    )
                    isLocationFetched = true
                } else {
                    requestNewLocation()
                }
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
                        centerMapOnLocation(
                            GeoPoint(location.latitude, location.longitude),
                            "Your Location"
                        )
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
            // Get the current timestamp
            val timestamp = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val date = Date(timestamp)
            val formattedDate = dateFormat.format(date) // Format the timestamp to actual time

            // Prepare location data
            val userLocation = mapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "timestamp" to timestamp,  // Store timestamp in milliseconds
                "actual_time" to formattedDate // Store actual formatted time
            )

            // Prepare login history data
            val loginHistory = mapOf(
                "login_timestamp" to timestamp,
                "login_time" to formattedDate // Store formatted login time
            )

            // Combine both location and login history in a single document
            val userHistory = mapOf(
                "location" to userLocation,
                "login_history" to loginHistory
            )

            db.collection("users").document(user.uid).collection("history")
                .add(userHistory)
                .addOnSuccessListener {
                    Toast.makeText(this, "Location and login history saved successfully", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to save data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
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
        centerMapOnLocation(
            GeoPoint(MANILA_LAT, MANILA_LON),
            "Manila, Philippines"
        )
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
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkLocationSettings()
                } else {
                    showDefaultLocationWithMessage("Location permission denied")
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CHECK_SETTINGS -> {
                if (resultCode == RESULT_OK) {
                    getCurrentLocation()
                } else {
                    showDefaultLocationWithMessage("Location services disabled")
                }
            }
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
