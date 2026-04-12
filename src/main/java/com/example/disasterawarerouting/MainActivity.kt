package com.example.disasterawarerouting

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.android.material.bottomnavigation.BottomNavigationView

import android.os.Handler
import android.os.Looper
import android.location.LocationManager
import android.provider.Settings
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST = 100
    private val LOCATION_PERMISSION_MAP_REQUEST = 101
    private val LOCATION_PERMISSION_STARTUP_REQUEST = 102
    private var isWaitingForGps = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 👤 Fetch User Name and greet them
        val currentUser = FirebaseAuth.getInstance().currentUser
        val userName = currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: currentUser?.email?.substringBefore("@")
            ?: "User"
        findViewById<android.widget.TextView>(R.id.userNameText)?.text = "👋 Welcome Back, $userName"

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

        // 🗺️ Map Button (on Navigate page)
        val openMapButton = findViewById<View>(R.id.openMapButton)
        openMapButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION
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

        // 🆘 SOS Page — individual call buttons
        val dialIntent: (String) -> Unit = { number ->
            startActivity(Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:$number") })
        }
        findViewById<View>(R.id.sosCall112).setOnClickListener  { dialIntent("112")  }
        findViewById<View>(R.id.sosCall100).setOnClickListener  { dialIntent("100")  }
        findViewById<View>(R.id.sosCall108).setOnClickListener  { dialIntent("108")  }
        findViewById<View>(R.id.sosCall101).setOnClickListener  { dialIntent("101")  }
        findViewById<View>(R.id.sosCall1078).setOnClickListener { dialIntent("1078") }

        // 🚪 Logout Button
        val logoutButton = findViewById<View>(R.id.logoutButton)
        logoutButton.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out of your account?")
                .setBackground(android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE))
                .setPositiveButton("Log Out") { _, _ ->
                    FirebaseAuth.getInstance().signOut()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show().apply {
                    getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(android.graphics.Color.parseColor("#EF4444")) // Red
                    getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(android.graphics.Color.parseColor("#64748B")) // Grey
                }
        }

        // Fetch weather
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) fetchWeather(location.latitude, location.longitude)
                else fetchWeather(40.7128, -74.0060)
            }
        } else {
            fetchWeather(40.7128, -74.0060)
        }

        // Show location prompt
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isDestroyed && !isFinishing) showLocationPromptDialog()
        }, 600)

        // ═══ Bottom Navigation — 5 tabs ═══
        val pageDashboard = findViewById<View>(R.id.pageDashboard)
        val pageNavigate  = findViewById<View>(R.id.pageNavigate)
        val pageSos       = findViewById<View>(R.id.pageSos)
        val pageAlerts    = findViewById<View>(R.id.pageAlerts)
        val pageAssistant = findViewById<View>(R.id.pageAssistant)

        val allPages = listOf(pageDashboard, pageNavigate, pageSos, pageAlerts, pageAssistant)

        // ═══ Resource Cards Setup ═══
        findViewById<View>(R.id.cardShelter)?.setOnClickListener { fetchLocationAndOpenMap("Community Center Shelter") }
        findViewById<View>(R.id.cardWater)?.setOnClickListener { fetchLocationAndOpenMap("Water Distribution Point") }
        findViewById<View>(R.id.cardFirstAid)?.setOnClickListener { fetchLocationAndOpenMap("Mobile First Aid Station") }
        findViewById<View>(R.id.cardFoodBank)?.setOnClickListener { fetchLocationAndOpenMap("Emergency Food Bank") }

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNavigationView.setOnItemSelectedListener { item ->
            allPages.forEach { it.visibility = View.GONE }
            when (item.itemId) {
                R.id.nav_dashboard -> pageDashboard.visibility = View.VISIBLE
                R.id.nav_navigate  -> pageNavigate.visibility  = View.VISIBLE
                R.id.nav_sos       -> pageSos.visibility       = View.VISIBLE
                R.id.nav_alerts    -> pageAlerts.visibility    = View.VISIBLE
                R.id.nav_assistant -> pageAssistant.visibility = View.VISIBLE
            }
            true
        }
    }


    override fun onResume() {
        super.onResume()
        
        // Check if location is enabled to hide/show the location error banner
        val permissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        
        val errorBanner = findViewById<View>(R.id.locationErrorBanner)
        if (errorBanner != null) {
            if (permissionGranted && isGpsEnabled) {
                errorBanner.visibility = View.GONE
            } else {
                errorBanner.visibility = View.VISIBLE
            }
        }

        if (isWaitingForGps) {
            isWaitingForGps = false
            if (isGpsEnabled) {
                restartApp()
            }
        }
    }

    private fun restartApp() {
        val intent = Intent(this, SplashActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    private fun showLocationPromptDialog() {
        val permissionGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || 
                           locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        // If everything is already fine, don't show the popup
        if (permissionGranted && isGpsEnabled) {
            return
        }

        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_location_permission)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.setCancelable(false)
        
        val btnEnableLocation = dialog.findViewById<View>(R.id.btnEnableLocation)
        val btnNotNow = dialog.findViewById<View>(R.id.btnNotNow)
        
        btnEnableLocation.setOnClickListener {
            dialog.dismiss()
            if (!permissionGranted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_STARTUP_REQUEST
                )
            } else if (!isGpsEnabled) {
                isWaitingForGps = true
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
        }
        
        btnNotNow.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
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

    // 🔹 Permission result
    
    private fun fetchLocationAndOpenMap(targetResourceName: String? = null) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                val lat = location?.latitude ?: 40.7128
                val lng = location?.longitude ?: -74.0060
                val trackingIntent = Intent(this@MainActivity, MapTrackerActivity::class.java).apply {
                    putExtra("LATITUDE", lat)
                    putExtra("LONGITUDE", lng)
                    if (targetResourceName != null) {
                        putExtra("TARGET_RESOURCE_NAME", targetResourceName)
                    }
                }
                startActivity(trackingIntent)
            }.addOnFailureListener {
                Toast.makeText(this, "Failed to get location", Toast.LENGTH_SHORT).show()
                val trackingIntent = Intent(this@MainActivity, MapTrackerActivity::class.java).apply {
                    putExtra("LATITUDE", 40.7128)
                    putExtra("LONGITUDE", -74.0060)
                    if (targetResourceName != null) {
                        putExtra("TARGET_RESOURCE_NAME", targetResourceName)
                    }
                }
                startActivity(trackingIntent)
            }
        }
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == LOCATION_PERMISSION_MAP_REQUEST) {
                fetchLocationAndOpenMap()
            } else if (requestCode == LOCATION_PERMISSION_STARTUP_REQUEST) {
                restartApp()
            }
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }
}