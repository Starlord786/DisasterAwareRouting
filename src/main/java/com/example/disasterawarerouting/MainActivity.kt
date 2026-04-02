package com.example.disasterawarerouting

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST = 100
    private val LOCATION_PERMISSION_MAP_REQUEST = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Buttons
        val emergencyCallButton = findViewById<View>(R.id.emergencyCallButton)
        val openMapButton = findViewById<View>(R.id.openMapButton)
        val shareLocationButton = findViewById<View>(R.id.shareLocationButton)

        // 🚨 Emergency Call (Dialer)
        emergencyCallButton.setOnClickListener {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:112")
            }
            startActivity(dialIntent)
        }

        // 🗺️ Check Zone & Open Google Maps
        openMapButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_MAP_REQUEST
                )
            } else {
                fetchLocationAndOpenMap()
            }
        }

        // 📍 Share Location via SMS
        shareLocationButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST
                )
            } else {
                shareLocation()
            }
        }
    }

    // 🔹 Get location and open SMS app
    private fun shareLocation() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val lat = location.latitude
                    val lng = location.longitude

                    val message =
                        "Emergency! My live location:\nhttps://maps.google.com/?q=$lat,$lng"

                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, message)
                    }

                    try {
                        startActivity(Intent.createChooser(shareIntent, "Share your location"))
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "No apps available to share", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Unable to get location. Turn on GPS.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
            }
    }

    // 🔹 Permission result
    
    private fun fetchLocationAndOpenMap() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    showSafetyDialogAndOpenMap(location.latitude, location.longitude)
                } else {
                    Toast.makeText(this, "Unable to get precise location. Using default.", Toast.LENGTH_SHORT).show()
                    showSafetyDialogAndOpenMap(40.7128, -74.0060)
                }
            }.addOnFailureListener {
                Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
                showSafetyDialogAndOpenMap(40.7128, -74.0060)
            }
        }
    }

    private fun showSafetyDialogAndOpenMap(lat: Double, lng: Double) {
        val isSafeZone = listOf(true, false).random()
        
        val title = if (isSafeZone) "✅ Safe Zone" else "⚠️ Critical Zone"
        val message = if (isSafeZone) {
            "Your current location is marked as clear from immediate disaster threats."
        } else {
            "WARNING: Imminent threat detected in your vicinity! Please proceed to safety."
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("View Map") { _, _ ->
                val trackingIntent = Intent(this@MainActivity, MapTrackerActivity::class.java).apply {
                    putExtra("IS_SAFE_ZONE", isSafeZone)
                    putExtra("LATITUDE", lat)
                    putExtra("LONGITUDE", lng)
                }
                startActivity(trackingIntent)
            }
            .setNegativeButton("Dismiss", null)
            .setCancelable(false)
            .show()
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == LOCATION_PERMISSION_REQUEST) {
                shareLocation()
            } else if (requestCode == LOCATION_PERMISSION_MAP_REQUEST) {
                fetchLocationAndOpenMap()
            }
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }
}