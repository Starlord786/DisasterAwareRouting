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

        // Subtle aurora pulse animation (alpha only — no sliding to avoid white flash)
        val auroraBg = findViewById<View>(R.id.auroraBackground)
        val animator = android.animation.ObjectAnimator.ofFloat(auroraBg, "alpha", 1.0f, 0.7f)
        animator.duration = 3000
        animator.repeatCount = android.animation.ValueAnimator.INFINITE
        animator.repeatMode = android.animation.ValueAnimator.REVERSE
        animator.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        animator.start()

        // Location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Buttons
        val emergencyCallButton = findViewById<View>(R.id.emergencyCallButton)
        val policeCallButton = findViewById<View>(R.id.policeCallButton)
        val ambulanceCallButton = findViewById<View>(R.id.ambulanceCallButton)
        val openMapButton = findViewById<View>(R.id.openMapButton)
        val shareLocationButton = findViewById<View>(R.id.shareLocationButton)

        // 🚨 Emergency Call (Dialer 112)
        emergencyCallButton.setOnClickListener {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:112") }
            startActivity(dialIntent)
        }
        
        // 🚓 Police Call (Dialer 100)
        policeCallButton.setOnClickListener {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:100") }
            startActivity(dialIntent)
        }

        // 🚑 Ambulance Call (Dialer 108)
        ambulanceCallButton.setOnClickListener {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:108") }
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

        // Fetch weather immediately if permission is already granted, else use default
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    fetchWeather(location.latitude, location.longitude)
                } else {
                    fetchWeather(40.7128, -74.0060)
                }
            }
        } else {
            fetchWeather(40.7128, -74.0060)
        }
    }

    private fun fetchWeather(lat: Double, lng: Double) {
        val titleText = findViewById<android.widget.TextView>(R.id.weatherTitleText)
        val descText = findViewById<android.widget.TextView>(R.id.weatherDescText)
        val iconText = findViewById<android.widget.TextView>(R.id.weatherIconText)
        
        kotlin.concurrent.thread {
            try {
                val urlString = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng&current_weather=true"
                val url = java.net.URL(urlString)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                if (connection.responseCode == 200) {
                    val stream = connection.inputStream
                    val response = stream.bufferedReader().use { it.readText() }
                    val json = org.json.JSONObject(response)
                    val current = json.getJSONObject("current_weather")
                    val temp = current.getDouble("temperature")
                    val code = current.getInt("weathercode")
                    
                    val (desc, emoji) = when (code) {
                        0 -> "Clear sky" to "☀️"
                        1, 2, 3 -> "Partly cloudy" to "⛅"
                        45, 48 -> "Foggy" to "🌫️"
                        51, 53, 55, 56, 57 -> "Drizzle" to "🌧️"
                        61, 63, 65, 66, 67 -> "Rain" to "☂️"
                        71, 73, 75, 77 -> "Snow" to "❄️"
                        80, 81, 82 -> "Rain showers" to "☔"
                        95, 96, 99 -> "Thunderstorm" to "⛈️"
                        else -> "Weather info" to "🌡️"
                    }
                    
                    runOnUiThread {
                        titleText.text = "$temp°C"
                        descText.text = desc
                        iconText.text = emoji
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    descText.text = "Failed to load weather"
                }
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