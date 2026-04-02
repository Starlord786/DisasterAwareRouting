package com.example.disasterawarerouting

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MapTrackerActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_tracker)

        val isSafeZone = intent.getBooleanExtra("IS_SAFE_ZONE", true)
        val color = if (isSafeZone) "green" else "red"
        val fillColor = if (isSafeZone) "#059669" else "#DC2626"
        val message = if (isSafeZone) "Safe Zone: All clear." else "DANGER: Critical Zone detected!"

        val webView = findViewById<WebView>(R.id.mapWebView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = WebViewClient()

        // Use real coordinates passed from MainActivity, or fallback
        val lat = intent.getDoubleExtra("LATITUDE", 40.7128)
        val lng = intent.getDoubleExtra("LONGITUDE", -74.0060)

        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <style>
                    body { padding: 0; margin: 0; }
                    html, body, #map { height: 100vh; width: 100vw; }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    var map = L.map('map').setView([$lat, $lng], 14);
                    
                    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        maxZoom: 19,
                        attribution: '© OpenStreetMap components'
                    }).addTo(map);

                    // Draw colored zone overlay
                    var circle = L.circle([$lat, $lng], {
                        color: '$color',
                        fillColor: '$fillColor',
                        fillOpacity: 0.4,
                        radius: 600
                    }).addTo(map);

                    // Add a center marker
                    var marker = L.marker([$lat, $lng]).addTo(map);
                    marker.bindPopup('<b>$message</b>').openPopup();

                    // Query nearby Police Stations only
                    var overpassQuery = '[out:json];(node["amenity"="police"](around:3000, $lat, $lng););out body;';
                    var queryUrl = "https://overpass-api.de/api/interpreter?data=" + encodeURIComponent(overpassQuery);

                    var policeIcon = L.divIcon({
                        html: '<div style="font-size: 22px; text-shadow: 1px 1px 2px white;">🚓</div>',
                        className: 'custom-police-icon',
                        iconSize: [24, 24],
                        iconAnchor: [12, 12]
                    });

                    fetch(queryUrl)
                        .then(response => response.json())
                        .then(data => {
                            data.elements.forEach(el => {
                                if(el.lat && el.lon) {
                                    var poiName = el.tags && el.tags.name ? el.tags.name : "Unnamed Station";
                                    var poiMarker = L.marker([el.lat, el.lon], { icon: policeIcon }).addTo(map);
                                    poiMarker.bindPopup("<b>🚓 Police Station</b><br>" + poiName);
                                }
                            });
                        })
                        .catch(err => console.error("Error fetching Police POIs: ", err));
                </script>
            </body>
            </html>
        """.trimIndent()

        // Load the HTML content directly into the WebView rendering the Leaflet Map
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }
}
