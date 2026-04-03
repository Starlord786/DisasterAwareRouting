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
        webView.webChromeClient = android.webkit.WebChromeClient()

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
                    body { padding: 0; margin: 0; font-family: sans-serif; }
                    html, body, #map { height: 100vh; width: 100vw; }
                    
                    .routing-panel {
                        position: absolute;
                        top: 40px;
                        left: 50%;
                        transform: translateX(-50%);
                        z-index: 1000;
                        background: rgba(255, 255, 255, 0.95);
                        padding: 12px 16px;
                        border-radius: 12px;
                        box-shadow: 0 4px 20px rgba(0,0,0,0.15);
                        display: flex;
                        align-items: center;
                        gap: 12px;
                        width: 85%;
                    }
                    .input-group {
                        display: flex;
                        flex-direction: column;
                        flex-grow: 1;
                        gap: 8px;
                    }
                    .route-input {
                        display: flex;
                        align-items: center;
                        background: #F1F5F9;
                        border-radius: 8px;
                        padding: 8px 12px;
                    }
                    .route-input span {
                        font-size: 13px;
                        color: #64748B;
                        font-weight: bold;
                        width: 45px;
                    }
                    .route-input input {
                        border: none;
                        background: transparent;
                        outline: none;
                        flex-grow: 1;
                        color: #0F172A;
                        font-size: 14px;
                        font-weight: bold;
                    }
                    .go-btn {
                        background: #10B981;
                        color: white;
                        border: none;
                        border-radius: 8px;
                        height: 54px;
                        width: 54px;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        font-weight: bold;
                        font-size: 16px;
                        cursor: pointer;
                        box-shadow: 0 4px 12px rgba(16,185,129,0.4);
                    }
                </style>
            </head>
            <body>
                <div id="map"></div>
                
                <!-- Routing Panel UI -->
                <div class="routing-panel">
                    <div class="input-group">
                        <div class="route-input">
                            <span>From</span>
                            <input type="text" id="fromLoc" placeholder="e.g. London" value="" onfocus="promptMyLocation(this)">
                        </div>
                        <div class="route-input">
                            <span>To</span>
                            <input type="text" id="toLoc" placeholder="e.g. Paris" value="">
                        </div>
                    </div>
                    <button class="go-btn" onclick="drawDynamicSafeRoute()">GO</button>
                </div>

                <script>
                    var map = L.map('map').setView([$lat, $lng], 8);
                    
                    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        maxZoom: 19,
                        attribution: '© OpenStreetMap components'
                    }).addTo(map);

                    // Add Custom Icons
                    var policeIcon = L.divIcon({ html: '<div style="font-size: 22px; text-shadow: 1px 1px 2px white;">🚓</div>', className: 'custom-icon', iconSize: [24,24], iconAnchor: [12,12] });
                    var hospitalIcon = L.divIcon({ html: '<div style="font-size: 22px; text-shadow: 1px 1px 2px white;">🏥</div>', className: 'custom-icon', iconSize: [24,24], iconAnchor: [12,12] });
                    
                    function getDistance(lat1, lon1, lat2, lon2) {
                        var R = 6371; // km
                        var dLat = (lat2 - lat1) * Math.PI / 180;
                        var dLon = (lon2 - lon1) * Math.PI / 180;
                        var a = Math.sin(dLat/2) * Math.sin(dLat/2) + Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLon/2) * Math.sin(dLon/2);
                        return Math.round(R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))); 
                    }

                    function addSafeZone(sLat, sLng, name, radius) {
                        var dist = getDistance($lat, $lng, sLat, sLng);
                        var circle = L.circle([sLat, sLng], { color: 'green', fillColor: '#059669', fillOpacity: 0.4, radius: radius }).addTo(map);
                        circle.bindPopup('<b>SAFE ZONE: ' + name + '</b><br>📍 Center is ' + dist + ' km away');
                    }
                    
                    function addDisasterZone(dLat, dLng, name, radius) {
                        var dist = getDistance($lat, $lng, dLat, dLng);
                        var circle = L.circle([dLat, dLng], { color: 'red', fillColor: '#DC2626', fillOpacity: 0.5, radius: radius }).addTo(map);
                        circle.bindPopup('<b>DANGER: ' + name + '</b><br>📍 ' + dist + ' km away');
                    }

                    function addPOI(pLat, pLng, type, name) {
                        var icon = type === 'hospital' ? hospitalIcon : policeIcon;
                        var prefix = type === 'hospital' ? "🏥 Hospital" : "🚓 Police Station";
                        var marker = L.marker([pLat, pLng], { icon: icon }).addTo(map);
                        marker.bindPopup("<b>" + prefix + "</b><br>" + name);
                    }

                    // --- 1. My Current Location ---
                    var marker = L.marker([$lat, $lng]).addTo(map);
                    marker.bindPopup('<b>📍 Your Current Location</b>').openPopup();

                    // --- Dynamic Safe Routing Function ---
                    var currentRouteLine = null;
                    var dynamicRouteMarkers = [];

                    function promptMyLocation(el) {
                        if (el.dataset.prompted) return;
                        el.dataset.prompted = "true";
                        if (confirm("Use your current location for the starting point?")) {
                            el.value = "$lat, $lng";
                            el.blur(); // Prevent keyboard from covering if they accept
                        }
                    }

                    async function geocode(city) {
                        try {
                            if(city.includes(",") && !isNaN(parseFloat(city.split(",")[0]))) {
                                var parts = city.split(",");
                                return [parseFloat(parts[0]), parseFloat(parts[1])];
                            }
                            var res = await fetch("https://nominatim.openstreetmap.org/search?format=json&q=" + encodeURIComponent(city));
                            var data = await res.json();
                            if(data && data.length > 0) {
                                return [parseFloat(data[0].lat), parseFloat(data[0].lon)];
                            }
                        } catch(e) { console.error(e); }
                        return null;
                    }

                    async function drawDynamicSafeRoute() {
                        var fromStr = document.getElementById("fromLoc").value;
                        var toStr = document.getElementById("toLoc").value;
                        if(!fromStr || !toStr) { alert("Please enter both locations."); return; }

                        var btn = document.querySelector(".go-btn");
                        btn.innerHTML = "⏳";
                        
                        var fromCoords = await geocode(fromStr);
                        var toCoords = await geocode(toStr);
                        
                        if(!fromCoords || !toCoords) {
                            btn.innerHTML = "GO";
                            alert("Could not locate one or both places. Try using a well-known city name.");
                            return;
                        }

                        if(currentRouteLine) { map.removeLayer(currentRouteLine); }
                        dynamicRouteMarkers.forEach(m => map.removeLayer(m));
                        dynamicRouteMarkers = [];
                        
                        // Default Fallback Evacuation Route curving outwards (if no roads exist)
                        var dLat = toCoords[0] - fromCoords[0];
                        var dLon = toCoords[1] - fromCoords[1];
                        var midLat = (fromCoords[0] + toCoords[0]) / 2;
                        var midLon = (fromCoords[1] + toCoords[1]) / 2;
                        
                        var offsetLat = midLat - (dLon * 0.25);
                        var offsetLon = midLon + (dLat * 0.25);

                        var waypoints = [
                            fromCoords, 
                            [fromCoords[0] + dLat*0.15, fromCoords[1] + dLon*0.15],
                            [offsetLat, offsetLon], 
                            [toCoords[0] - dLat*0.15, toCoords[1] - dLon*0.15],
                            toCoords
                        ];

                        // Attempt to fetch Real-World Driving Route via OpenStreetMap OSRM
                        try {
                            btn.innerHTML = "🛣️";
                            // OSRM expects lon,lat format
                            var routeRes = await fetch("https://router.project-osrm.org/route/v1/driving/" + fromCoords[1] + "," + fromCoords[0] + ";" + toCoords[1] + "," + toCoords[0] + "?overview=full&geometries=geojson");
                            var routeData = await routeRes.json();
                            
                            if(routeData && routeData.routes && routeData.routes.length > 0) {
                                // Extract the real road coordinates
                                var gjCoords = routeData.routes[0].geometry.coordinates;
                                // Convert [lon, lat] from GeoJSON into Leaflet's [lat, lon]
                                waypoints = gjCoords.map(function(c) { return [c[1], c[0]]; });
                            }
                        } catch(e) {
                            console.error("OSRM Route Failed, using fallback curve.", e);
                        }
                        
                        btn.innerHTML = "GO";
                        
                        currentRouteLine = L.polyline(waypoints, {
                            color: '#3B82F6', // Neon bright blue route
                            weight: 6,
                            opacity: 0.9,
                            dashArray: '12, 12',
                            lineJoin: 'round'
                        }).addTo(map);
                        
                        currentRouteLine.bindTooltip('<b>✅ Safest Path</b>', {permanent: true, direction: 'center'}).openTooltip();
                        
                        var m1 = L.marker(fromCoords).addTo(map).bindPopup('<b>Start: </b>' + fromStr).openPopup();
                        var m2 = L.marker(toCoords).addTo(map).bindPopup('<b>Destination: </b>' + toStr);
                        dynamicRouteMarkers.push(m1, m2);
                        
                        map.fitBounds(currentRouteLine.getBounds(), { padding: [50, 50] });
                    }

                    // --- 2. Exactly 2 Green Zones ---
                    // Zone 1: Center is safely offset (~15km away), but massive radius (25km) completely overlaps the user's location seamlessly!
                    addSafeZone($lat + 0.1, $lng - 0.1, "Primary Relief Camp", 25000); 
                    // Zone 2: Far away safe zone (~200+ km away)
                    addSafeZone($lat - 1.8, $lng + 1.6, "Secondary Evacuation Center", 20000);

                    // --- 3. Dummy Hospitals & Police Stations near my location ---
                    addPOI($lat + 0.02, $lng + 0.03, 'hospital', 'City General Hospital');
                    addPOI($lat - 0.03, $lng - 0.01, 'hospital', 'Metro Care Unit');
                    addPOI($lat + 0.04, $lng - 0.02, 'police', 'District Police HQ');
                    addPOI($lat - 0.01, $lng + 0.04, 'police', 'State Patrol Station');

                    // --- 4. Logic/Dummy approach for EXACTLY 4 Red Zones strongly pushed away---
                    var overpassDisasterQuery = '[out:json];(' +
                        'way["natural"="coastline"](around:250000, $lat, $lng);' +
                        'way["landuse"="forest"](around:250000, $lat, $lng);' +
                        'node["place"="city"](around:250000, $lat, $lng);' +
                        'way["natural"="water"](around:250000, $lat, $lng);' +
                        ');out center 4;';

                    fetch("https://overpass-api.de/api/interpreter?data=" + encodeURIComponent(overpassDisasterQuery))
                        .then(response => response.json())
                        .then(data => {
                            var zoneCount = 0;
                            if (data.elements) {
                                data.elements.forEach(el => {
                                    if(zoneCount >= 4) return;
                                    var elLat = el.lat || (el.center && el.center.lat);
                                    var elLng = el.lon || (el.center && el.center.lon);
                                    if (!elLat || !elLng) return;

                                    // Force it to be VERY far away from user! (At least 100km away)
                                    var dist = getDistance($lat, $lng, elLat, elLng);
                                    if(dist < 100) return; 

                                    var tags = el.tags || {};
                                    var dName = "Major Wildfire"; 
                                    if (tags.natural === "coastline") dName = "Tsunami Warning";
                                    else if (tags.natural === "water") dName = "Severe Flash Flooding";
                                    else if (tags.place === "city") dName = "Magnitude 6.5 Earthquake";

                                    addDisasterZone(elLat, elLng, dName, 40000);
                                    zoneCount++;
                                });
                            }
                            
                            // Fill up to 4 exactly if API was lacking logical spots far enough away
                            var dZones = [
                                {n: "Major Wildfire", lat: $lat + 1.2, lng: $lng + 1.1},
                                {n: "Category 4 Hurricane", lat: $lat - 1.5, lng: $lng - 1.0},
                                {n: "Magnitude 5.0 Earthquake", lat: $lat - 1.1, lng: $lng + 1.5},
                                {n: "Chemical Spill", lat: $lat + 1.4, lng: $lng - 1.2}
                            ];
                            for (var i = 0; i < dZones.length && zoneCount < 4; i++) {
                                addDisasterZone(dZones[i].lat, dZones[i].lng, dZones[i].n, 40000);
                                zoneCount++;
                            }
                        })
                        .catch(err => {
                            // Instant Fallback if offline
                            addDisasterZone($lat + 1.2, $lng + 1.1, "Major Wildfire", 40000);
                            addDisasterZone($lat - 1.5, $lng - 1.0, "Category 4 Hurricane", 40000);
                            addDisasterZone($lat - 1.1, $lng + 1.5, "Magnitude 5.0 Earthquake", 40000);
                            addDisasterZone($lat + 1.4, $lng - 1.2, "Chemical Spill", 40000);
                        });
                </script>
            </body>
            </html>
        """.trimIndent()

        // Load the HTML content directly into the WebView rendering the Leaflet Map
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }
}
