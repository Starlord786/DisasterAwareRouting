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

        // ═══ Chatbot AI Setup ═══
        val chatInputEditText = findViewById<android.widget.EditText>(R.id.chatInputEditText)
        val chatSendButton = findViewById<View>(R.id.chatSendButton)
        val chatMessageContainer = findViewById<android.widget.LinearLayout>(R.id.chatMessageContainer)
        val chatScrollView = findViewById<android.widget.ScrollView>(R.id.chatScrollView)

        fun addChatMessage(message: String, isUser: Boolean) {
            val layoutId = if (isUser) R.layout.layout_chat_message_user else R.layout.layout_chat_message_ai
            val chatView = layoutInflater.inflate(layoutId, chatMessageContainer, false)
            chatView.findViewById<android.widget.TextView>(R.id.messageText).text = message
            chatMessageContainer.addView(chatView)
            
            chatScrollView.post {
                chatScrollView.fullScroll(View.FOCUS_DOWN)
            }
        }

        addChatMessage("Hello! I am your AI Disaster Assistant. How can I help you stay safe today?", false)

        chatSendButton.setOnClickListener {
            val query = chatInputEditText.text.toString().trim()
            if (query.isNotEmpty()) {
                addChatMessage(query, true)
                chatInputEditText.text.clear()

                Handler(Looper.getMainLooper()).postDelayed({
                    if (isFinishing || isDestroyed) return@postDelayed
                    val lowerQuery = query.lowercase().trim()
                    val response = when {
                        // Conversational
                        lowerQuery.matches(Regex(".*\\b(hi|hello|hey|hola|yo|greetings)\\b.*")) -> "Hello! I am your Disaster Aware Routing Assistant. How can I help you stay safe today?"
                        lowerQuery.matches(Regex(".*\\b(how are you|how r u|how are u)\\b.*")) -> "I am functioning perfectly and ready to assist you with emergency guidelines, routing, and survival tips."
                        lowerQuery.matches(Regex(".*\\b(who are you|who r u)\\b.*")) -> "I am the Disaster Aware Routing Assistant. I'm an AI built directly into this app to help you navigate hazardous situations safely."
                        lowerQuery.matches(Regex(".*\\b(what can you do|help me|help)\\b.*")) -> "I can give you survival instructions for various emergencies, guide you on using our map for safe routing, and explain app features like the SOS dialer. What do you need?"
                        lowerQuery.matches(Regex(".*\\b(thank you|thanks|thx|ok|okay)\\b.*")) -> "You're very welcome! Please stay safe and let me know if you need anything else."
                        lowerQuery.matches(Regex(".*\\b(bye|goodbye|see ya|exit)\\b.*")) -> "Stay safe! I'll be here if you need me."

                        // Disasters
                        lowerQuery.matches(Regex(".*\\b(earthquake|earthquakes)\\b.*")) -> "In an earthquake: DROP to your hands and knees, COVER your head and neck, and HOLD ON until shaking stops. Stay away from windows and exterior walls."
                        lowerQuery.matches(Regex(".*\\b(fire|fires|wildfire|wildfires)\\b.*")) -> "In case of fire: Evacuate immediately. Crawl low under smoke to avoid inhalation. Do not use elevators. If clothes catch fire, Stop, Drop, and Roll."
                        lowerQuery.matches(Regex(".*\\b(flood|floods|water|tsunami)\\b.*")) -> "For floods and tsunamis: Move to higher ground immediately. Do not walk or drive through floodwaters. Just 6 inches of moving water can knock you down."
                        lowerQuery.matches(Regex(".*\\b(tornado|hurricane|storm|cyclone)\\b.*")) -> "During severe storms or tornadoes: Seek shelter in an interior room on the lowest floor, away from windows. Protect your head with blankets or pillows."
                        lowerQuery.matches(Regex(".*\\b(power|blackout|outage)\\b.*")) -> "During a power outage: Keep freezers and refrigerators closed. Use flashlights instead of candles to prevent fires. Disconnect appliances to avoid damage from surges."
                        
                        // Emergencies / Medical
                        lowerQuery.matches(Regex(".*\\b(injured|medical|hurt|first aid)\\b.*")) -> "If someone is seriously injured, use the SOS tab immediately to call emergency services. Do not move severely injured persons unless they are in immediate danger."
                        lowerQuery.matches(Regex(".*\\b(evacuat|evacuation|evacuate)\\b.*")) -> "When instructed to evacuate: Leave immediately. Follow designated evacuation routes shown on our map, take your emergency kit, and secure your home if time permits."

                        // App Features & Dynamic Data
                        lowerQuery.matches(Regex(".*\\b(route|routes|navigate|navigation|map|maps)\\b.*")) -> "Use the Navigation tab to access our map. It will automatically calculate safe routes for you while dynamically avoiding designated red danger zones."
                        lowerQuery.matches(Regex(".*\\b(safe zone|safe zones|shelter|shelters|hospital|hospitals)\\b.*")) -> "Safe zones and hospitals are marked on the map. You can also tap the resource cards on the Dashboard to get direct routing to the nearest community center or medical station."
                        lowerQuery.matches(Regex(".*\\b(sos|emergency|police|ambulance)\\b.*")) -> "The SOS tab features direct one-tap dialing for Emergency Services, Police, Ambulance, and Fire Departments. Use it if you are in immediate danger."
                        lowerQuery.matches(Regex(".*\\b(alert|alerts|notification|notifications)\\b.*")) -> "The Alerts tab shows active real-time warnings near your location. Ensure your location services are enabled to receive localized alerts."
                        lowerQuery.matches(Regex(".*\\b(weather|wheather|temperature|forecast)\\b.*")) -> {
                            val temp = findViewById<android.widget.TextView>(R.id.weatherTitleText)?.text?.toString() ?: "--°C"
                            val desc = findViewById<android.widget.TextView>(R.id.weatherDescText)?.text?.toString() ?: "Unknown"
                            val icon = findViewById<android.widget.TextView>(R.id.weatherIconText)?.text?.toString() ?: ""
                            "The current weather in your location is $temp, $desc $icon. Please check the Dashboard for real-time updates."
                        }
                        lowerQuery.matches(Regex(".*\\b(active disaster|disaster|disasters|active disasters|current disaster)\\b.*")) -> {
                            "Based on the local status overview, there are exactly 3 Active Alerts, 12 Hospitals, 5 Police Stations, and 8 Safe Zones nearby."
                        }
                        lowerQuery.matches(Regex(".*\\b(dashboard|home)\\b.*")) -> "The Dashboard gives you a quick area overview, including active alerts, nearby hospitals, safe zones, and a daily safety tip."
                        lowerQuery.matches(Regex(".*\\b(password|account|login|logout)\\b.*")) -> "You can safely sign out of your account using the logout button located at the top right of the Dashboard."

                        // Professional Fallback
                        else -> "I understand your query, however, my expertise is strictly focused on emergency preparedness, disaster survival, and navigating this application safely. Please feel free to ask me anything related to those topics or how to use our features."
                    }
                    addChatMessage(response, false)
                }, 1000)
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