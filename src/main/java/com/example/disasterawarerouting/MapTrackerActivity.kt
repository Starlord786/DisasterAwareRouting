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
                    var map = L.map('map').setView([$lat, $lng], 7);
                    
                    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        maxZoom: 19,
                        attribution: '© OpenStreetMap components'
                    }).addTo(map);

                    // Safe zone at current location
                    var safeCircle = L.circle([$lat, $lng], {
                        color: 'green',
                        fillColor: '#059669',
                        fillOpacity: 0.4,
                        radius: 10000
                    }).addTo(map);

                    var marker = L.marker([$lat, $lng]).addTo(map);
                    marker.bindPopup('<b>Safe Zone: Current Location</b>').openPopup();

                    function addDisasterZone(dLat, dLng, disasterName, radiusM) {
                        var circle = L.circle([dLat, dLng], {
                            color: 'red',
                            fillColor: '#DC2626',
                            fillOpacity: 0.5,
                            radius: radiusM
                        }).addTo(map);
                        circle.bindPopup('<b>DANGER: ' + disasterName + '</b>');
                    }

                    // Near me like ocean near place (approx 50-70km away)
                    addDisasterZone($lat + 0.4, $lng + 0.5, "Tsunami / Coastal Flood Warning", 15000);
                    addDisasterZone($lat - 0.5, $lng + 0.3, "Severe Flash Flooding", 12000);

                    // Random places around 200km away (~1.8 degrees)
                    var disasters = ["Magnitude 6.5 Earthquake", "Major Wildfire", "Category 4 Hurricane", "Severe Tornado Warning", "Chemical Spill"];
                    for(var i=0; i<disasters.length; i++) {
                        var angle = Math.random() * Math.PI * 2;
                        var dist = 1.6 + Math.random() * 0.6; // approx 1.6 to 2.2 degrees
                        var rLat = $lat + Math.cos(angle) * dist;
                        var rLng = $lng + Math.sin(angle) * dist;
                        addDisasterZone(rLat, rLng, disasters[i], 25000 + Math.random() * 25000);
                    }

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
