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
        val targetResourceName = intent.getStringExtra("TARGET_RESOURCE_NAME")
        
        // Use a small offset so the resource is placed near but not exactly on top of user
        val targetLatStr = if (targetResourceName != null) (lat + 0.08).toString() else "null"
        val targetLngStr = if (targetResourceName != null) (lng + 0.08).toString() else "null"

        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                <script src="https://maps.googleapis.com/maps/api/js?key=AIzaSyAiXrATylJKiIEqdeM_M8oKb0Ona3dnIAk"></script>
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <script src="https://unpkg.com/leaflet.gridlayer.googlemutant@latest/dist/Leaflet.GoogleMutant.js"></script>
                <style>
                    body { padding: 0; margin: 0; font-family: sans-serif; }
                    html, body, #map { height: 100vh; width: 100vw; }
                    
                    .routing-panel {
                        position: absolute;
                        top: 10px;
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
                    
                    .sidebar {
                        position: absolute;
                        top: 140px;
                        left: 10px;
                        width: 260px;
                        background: rgba(255, 255, 255, 0.95);
                        z-index: 1000;
                        border-radius: 12px;
                        box-shadow: 0 4px 15px rgba(0,0,0,0.2);
                        display: none;
                        flex-direction: column;
                        max-height: calc(100vh - 130px);
                        font-family: sans-serif;
                    }
                    .sidebar-header {
                        padding: 12px;
                        border-bottom: 1px solid #E2E8F0;
                        background: #F8FAFC;
                        border-top-left-radius: 12px;
                        border-top-right-radius: 12px;
                    }
                    .sidebar-header h3 {
                        margin: 0;
                        font-size: 15px;
                        color: #1E293B;
                    }
                    .sidebar-header p {
                        margin: 2px 0 0 0;
                        font-size: 12px;
                        color: #64748B;
                    }
                    .sidebar-content {
                        overflow-y: auto;
                        padding-bottom: 8px;
                    }
                    .alert-item {
                        padding: 12px;
                        border-bottom: 1px solid #F1F5F9;
                        display: flex;
                        align-items: center;
                        gap: 12px;
                    }
                    .alert-item:last-child {
                        border-bottom: none;
                    }
                    .alert-icon {
                        width: 28px;
                        height: 28px;
                        border-radius: 50%;
                        background: #DC2626;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        font-size: 14px;
                        box-shadow: 0 2px 5px rgba(220, 38, 38, 0.3);
                    }
                    .alert-text h4 {
                        margin: 0;
                        font-size: 13px;
                        color: #334155;
                        font-weight: 600;
                    }
                    .alert-text p {
                        margin: 2px 0 0 0;
                        font-size: 11px;
                        color: #64748B;
                    }
                    
                    .custom-modal {
                        position: fixed;
                        top: 0; left: 0; width: 100vw; height: 100vh;
                        background: rgba(0,0,0,0.5);
                        z-index: 9999;
                        display: none;
                        justify-content: center;
                        align-items: center;
                        backdrop-filter: blur(4px);
                    }
                    .modal-content {
                        background: white;
                        padding: 30px;
                        border-radius: 20px;
                        width: 85%;
                        max-width: 320px;
                        text-align: center;
                        box-shadow: 0 10px 25px rgba(0,0,0,0.4);
                        font-family: sans-serif;
                    }
                    .modal-icon {
                        font-size: 55px;
                        margin-bottom: 12px;
                    }
                    .modal-content h3 {
                        margin: 0 0 10px 0;
                        color: #DC2626;
                        font-size: 22px;
                        font-weight: 800;
                    }
                    .modal-content p {
                        color: #475569;
                        font-size: 15px;
                        margin-bottom: 25px;
                        line-height: 1.5;
                        font-weight: 600;
                    }
                    .modal-btn {
                        background: #DC2626;
                        color: white;
                        border: none;
                        padding: 14px 24px;
                        border-radius: 12px;
                        font-weight: bold;
                        width: 100%;
                        cursor: pointer;
                        font-size: 16px;
                        margin-top: 4px;
                    }
                    .modal-btn-outline {
                        background: transparent;
                        color: #64748B;
                        border: 2px solid #E2E8F0;
                        padding: 12px 24px;
                        border-radius: 12px;
                        font-weight: bold;
                        width: 100%;
                        cursor: pointer;
                        font-size: 15px;
                        margin-top: 10px;
                    }
                    .loc-modal {
                        position: fixed;
                        top: 0; left: 0; width: 100vw; height: 100vh;
                        background: rgba(0,0,0,0.45);
                        z-index: 9999;
                        display: none;
                        justify-content: center;
                        align-items: center;
                        backdrop-filter: blur(4px);
                    }
                    .loc-modal-content {
                        background: white;
                        padding: 28px 24px 20px;
                        border-radius: 22px;
                        width: 85%;
                        max-width: 320px;
                        text-align: center;
                        box-shadow: 0 12px 30px rgba(0,0,0,0.3);
                        font-family: sans-serif;
                    }
                    .loc-modal-icon { font-size: 50px; margin-bottom: 10px; }
                    .loc-modal-content h3 {
                        margin: 0 0 8px 0;
                        color: #0F172A;
                        font-size: 20px;
                        font-weight: 800;
                    }
                    .loc-modal-content p {
                        color: #64748B;
                        font-size: 14px;
                        margin-bottom: 20px;
                        line-height: 1.5;
                    }
                    .loc-btn-yes {
                        background: linear-gradient(135deg, #10B981, #059669);
                        color: white;
                        border: none;
                        padding: 14px 24px;
                        border-radius: 12px;
                        font-weight: bold;
                        width: 100%;
                        cursor: pointer;
                        font-size: 16px;
                        box-shadow: 0 4px 12px rgba(16,185,129,0.35);
                    }
                    .loc-btn-no {
                        background: transparent;
                        color: #94A3B8;
                        border: 1.5px solid #E2E8F0;
                        padding: 12px 24px;
                        border-radius: 12px;
                        font-weight: 600;
                        width: 100%;
                        cursor: pointer;
                        font-size: 15px;
                        margin-top: 10px;
                    }
                    .map-toggle {
                        position: absolute;
                        bottom: 30px;
                        right: 15px;
                        background: rgba(255, 255, 255, 0.95);
                        z-index: 1000;
                        border-radius: 20px;
                        box-shadow: 0 4px 15px rgba(0,0,0,0.2);
                        display: flex;
                        overflow: hidden;
                    }
                    .toggle-btn {
                        padding: 10px 16px;
                        font-weight: 800;
                        font-size: 13px;
                        color: #64748B;
                        cursor: pointer;
                        transition: all 0.3s;
                        border-right: 1px solid #E2E8F0;
                    }
                    .toggle-btn:last-child {
                        border-right: none;
                    }
                    .toggle-btn.active {
                        background: #3B82F6;
                        color: white;
                    }
                </style>
            </head>
            <body>
                <div id="map"></div>
                
                <div class="map-toggle">
                    <div class="toggle-btn active" id="btn-road" onclick="setMapType('road')">🗺️ Street</div>
                    <div class="toggle-btn" id="btn-sat" onclick="setMapType('sat')">🛰️ Satellite</div>
                </div>
                
                <div class="custom-modal" id="dangerModal">
                    <div class="modal-content">
                        <div class="modal-icon">⚠️</div>
                        <h3 id="modalTitle">CAUTION</h3>
                        <p id="modalMessage">You are charting a course into or through a Danger Zone. Please exercise extreme caution!</p>
                        <button class="modal-btn" onclick="document.getElementById('dangerModal').style.display='none'">I Understand</button>
                    </div>
                </div>

                <!-- Custom Location Prompt Modal -->
                <div class="loc-modal" id="locModal">
                    <div class="loc-modal-content">
                        <div class="loc-modal-icon">📍</div>
                        <h3>Use My Location?</h3>
                        <p>Set your current GPS location as the starting point for the route.</p>
                        <button class="loc-btn-yes" id="locYesBtn">✅ Yes, Use My Location</button>
                        <button class="loc-btn-no" onclick="document.getElementById('locModal').style.display='none'">No, I'll type it</button>
                    </div>
                </div>

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

                <!-- Add delay sidebar -->
                <div class="sidebar" id="delaySidebar">
                    <div class="sidebar-header" style="display:flex; justify-content:space-between; align-items:flex-start;">
                        <div>
                            <h3>Delays & Alerts</h3>
                            <p>Route conditions ahead</p>
                        </div>
                        <button onclick="document.getElementById('delaySidebar').style.display='none'" style="background:none;border:none;font-size:24px;color:#94a3b8;cursor:pointer;padding:0;line-height:0.8;font-weight:bold;">&times;</button>
                    </div>
                    <div class="sidebar-content" id="delaySidebarContent">
                        <!-- Populated randomly by JS -->
                    </div>
                </div>

                <script>
                    var map = L.map('map', { zoomControl: false }).setView([$lat, $lng], 8);
                    
                    var roadMap = L.gridLayer.googleMutant({
                        type: 'roadmap',
                        maxZoom: 20
                    });
                    
                    var satelliteMap = L.gridLayer.googleMutant({
                        type: 'satellite',
                        maxZoom: 20
                    });

                    // Add default map layer
                    roadMap.addTo(map);

                    function setMapType(type) {
                        if (type === 'road') {
                            map.removeLayer(satelliteMap);
                            map.addLayer(roadMap);
                            document.getElementById('btn-road').classList.add('active');
                            document.getElementById('btn-sat').classList.remove('active');
                        } else {
                            map.removeLayer(roadMap);
                            map.addLayer(satelliteMap);
                            document.getElementById('btn-sat').classList.add('active');
                            document.getElementById('btn-road').classList.remove('active');
                        }
                    }

                    // Add Custom Icons
                    var policeIcon = L.divIcon({ html: '<div style="font-size: 22px; text-shadow: 1px 1px 2px white;">🚓</div>', className: 'custom-icon', iconSize: [24,24], iconAnchor: [12,12] });
                    var hospitalIcon = L.divIcon({ html: '<div style="font-size: 22px; text-shadow: 1px 1px 2px white;">🏥</div>', className: 'custom-icon', iconSize: [24,24], iconAnchor: [12,12] });
                    var resourceIcon = L.divIcon({ html: '<div style="font-size: 26px; text-shadow: 1px 1px 3px black;">📦</div>', className: 'custom-icon', iconSize: [28,28], iconAnchor: [14,14] });
                    
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
                    
                    var disasterZones = [];
                    function addDisasterZone(dLat, dLng, name, radius) {
                        var dist = getDistance($lat, $lng, dLat, dLng);
                        var circle = L.circle([dLat, dLng], { color: 'red', fillColor: '#DC2626', fillOpacity: 0.5, radius: radius }).addTo(map);
                        circle.bindPopup('<b>DANGER: ' + name + '</b><br>📍 ' + dist + ' km away');
                        disasterZones.push({lat: dLat, lng: dLng, name: name, radius: radius});
                    }

                    function addPOI(pLat, pLng, type, name) {
                        var icon = type === 'hospital' ? hospitalIcon : (type === 'resource' ? resourceIcon : policeIcon);
                        var prefix = type === 'hospital' ? "🏥 Hospital" : (type === 'resource' ? "📦 Resource" : "🚓 Police Station");
                        var marker = L.marker([pLat, pLng], { icon: icon }).addTo(map);
                        marker.bindPopup("<b>" + prefix + "</b><br>" + name);
                    }

                    function generateRandomDelays() {
                        var allOptions = [
                            { icon: "⚠️", bg: "#DC2626", title: "Speedbreaker Ahead", desc: "In 8m" },
                            { icon: "⚠️", bg: "#F59E0B", title: "Speedbreaker Ahead", desc: "In 25m" },
                            { icon: "🚗", bg: "#DC2626", title: "Slowdown on Expressway", desc: Math.floor(Math.random()*15+5) + "-min delay" },
                            { icon: "🚗", bg: "#DC2626", title: "Heavy Traffic", desc: Math.floor(Math.random()*10+10) + "-min delay" },
                            { icon: "🚗", bg: "#F59E0B", title: "Slowdown on Main Rd", desc: Math.floor(Math.random()*8+3) + "-min delay" },
                            { icon: "🚧", bg: "#F59E0B", title: "Road Construction", desc: "Lane closed" },
                            { icon: "🚧", bg: "#F59E0B", title: "Pothole Repair", desc: "Near intersection" },
                            { icon: "🌧️", bg: "#3B82F6", title: "Slippery Road", desc: "Due to recent rain" },
                            { icon: "🛑", bg: "#DC2626", title: "Accident Ahead", desc: "Right lane blocked" }
                        ];
                        
                        var shuffled = allOptions.sort(function() { return 0.5 - Math.random() });
                        var numItems = Math.floor(Math.random() * 3) + 2; 
                        var contentHtml = "";
                        
                        for(var i=0; i<numItems; i++) {
                            var item = shuffled[i];
                            contentHtml += '<div class="alert-item">' +
                                '<div class="alert-icon" style="background:' + item.bg + '">' + item.icon + '</div>' +
                                '<div class="alert-text">' +
                                    '<h4>' + item.title + '</h4>' +
                                    '<p>' + item.desc + '</p>' +
                                '</div>' +
                            '</div>';
                        }
                        var el = document.getElementById("delaySidebarContent");
                        if(el) el.innerHTML = contentHtml;
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
                        el.blur();
                        var modal = document.getElementById("locModal");
                        modal.style.display = "flex";
                        document.getElementById("locYesBtn").onclick = function() {
                            el.value = "$lat, $lng";
                            modal.style.display = "none";
                        };
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

                    // ---- HELPER: Fetch OSRM route (used only for clean routes) ----
                    async function fetchOSRMRoute(from, to) {
                        var coordStr = from[1] + "," + from[0] + ";" + to[1] + "," + to[0];
                        try {
                            var res = await fetch("https://router.project-osrm.org/route/v1/driving/" + coordStr + "?overview=full&geometries=geojson");
                            var data = await res.json();
                            if(data && data.routes && data.routes.length > 0) {
                                return data.routes[0].geometry.coordinates.map(function(c){ return [c[1], c[0]]; });
                            }
                        } catch(e) { console.error("OSRM error:", e); }
                        return null;
                    }

                    // ---- HELPER: Check if any point in a path is inside any zone ----
                    function pathHitsAnyZone(pts) {
                        for(var j = 0; j < pts.length; j++) {
                            for(var k = 0; k < disasterZones.length; k++) {
                                var dz = disasterZones[k];
                                if(getDistance(pts[j][0], pts[j][1], dz.lat, dz.lng) <= (dz.radius / 1000)) {
                                    return true;
                                }
                            }
                        }
                        return false;
                    }

                    // ---- HELPER: Force-field deflection — push sampled points away from all zones ----
                    function computeDeflectedRoute(from, to) {
                        var N = 80; // sample resolution
                        var SAFE_BUFFER_KM = 35; // push at least this far outside zone radius
                        var pts = [];

                        for(var step = 0; step <= N; step++) {
                            var t = step / N;
                            var lat = from[0] + t * (to[0] - from[0]);
                            var lon = from[1] + t * (to[1] - from[1]);

                            // Accumulate deflection from every nearby zone
                            var defLat = 0, defLon = 0;
                            for(var k = 0; k < disasterZones.length; k++) {
                                var dz = disasterZones[k];
                                var safeKm = (dz.radius / 1000) + SAFE_BUFFER_KM;
                                var d = getDistance(lat, lon, dz.lat, dz.lng);
                                if(d < safeKm) {
                                    // Vector FROM zone center to current point
                                    var vLat = lat - dz.lat;
                                    var vLon = lon - dz.lng;
                                    var vLen = Math.sqrt(vLat*vLat + vLon*vLon);
                                    if(vLen < 0.001) { vLat = 0.01; vLon = 0.01; vLen = 0.014; }
                                    // Strength: stronger when closer to center
                                    var strength = (safeKm - d) / safeKm;
                                    var pushDeg = strength * safeKm * 0.009;
                                    defLat += (vLat / vLen) * pushDeg;
                                    defLon += (vLon / vLen) * pushDeg;
                                }
                            }
                            pts.push([lat + defLat, lon + defLon]);
                        }
                        return pts;
                    }

                    async function drawDynamicSafeRoute() {
                        var fromStr = document.getElementById("fromLoc").value.trim();
                        var toStr   = document.getElementById("toLoc").value.trim();
                        if(!fromStr || !toStr) { alert("Please enter both locations."); return; }

                        var btn = document.querySelector(".go-btn");
                        btn.innerHTML = "⏳";

                        var fromCoords = await geocode(fromStr);
                        var toCoords   = await geocode(toStr);

                        if(!fromCoords || !toCoords) {
                            btn.innerHTML = "GO";
                            alert("Could not locate one or both places. Try a well-known city name.");
                            return;
                        }

                        // Clear old route
                        if(currentRouteLine) { map.removeLayer(currentRouteLine); currentRouteLine = null; }
                        dynamicRouteMarkers.forEach(function(m){ map.removeLayer(m); });
                        dynamicRouteMarkers = [];

                        // Block if DESTINATION itself is inside a red zone
                        for(var i = 0; i < disasterZones.length; i++) {
                            var dz = disasterZones[i];
                            if(getDistance(toCoords[0], toCoords[1], dz.lat, dz.lng) <= (dz.radius / 1000)) {
                                btn.innerHTML = "GO";
                                document.getElementById("modalMessage").innerText =
                                    "Your destination is inside Danger Zone: " + dz.name + ". Cannot plan a safe route there.";
                                document.getElementById("dangerModal").style.display = "flex";
                                return;
                            }
                        }

                        btn.innerHTML = "🛣️";

                        // Step 1: Try real OSRM route
                        var osrmRoute = await fetchOSRMRoute(fromCoords, toCoords);

                        var finalWaypoints;
                        if(osrmRoute && !pathHitsAnyZone(osrmRoute)) {
                            // OSRM route is clean — use it directly (real roads)
                            finalWaypoints = osrmRoute;
                        } else {
                            // OSRM route passes through a zone (or failed) — 
                            // Use force-field deflection to compute a safe visual path
                            finalWaypoints = computeDeflectedRoute(fromCoords, toCoords);
                        }

                        // Show delays panel
                        generateRandomDelays();
                        var sd = document.getElementById("delaySidebar");
                        if(sd) sd.style.display = "flex";

                        btn.innerHTML = "GO";

                        currentRouteLine = L.polyline(finalWaypoints, {
                            color: '#3B82F6',
                            weight: 5,
                            opacity: 0.95,
                            lineJoin: 'round',
                            lineCap: 'round'
                        }).addTo(map);

                        currentRouteLine.bindTooltip('<b>✅ Safest Route</b>', { sticky: true });

                        var destIcon = L.divIcon({
                            html: '<div style="background-color:#10B981; border: 3px solid white; border-radius: 50%; box-shadow: 0 4px 10px rgba(0,0,0,0.3); width: 28px; height: 28px; display: flex; align-items: center; justify-content: center; font-size: 16px;">🏁</div>',
                            className: 'custom-route-dest',
                            iconSize: [28,28],
                            iconAnchor: [14,28]
                        });
                        var startIcon = L.divIcon({
                            html: '<div style="background-color:#2563EB; border: 3px solid white; border-radius: 50%; box-shadow: 0 4px 10px rgba(0,0,0,0.3); width: 28px; height: 28px; display: flex; align-items: center; justify-content: center; font-size: 16px;">🟢</div>',
                            className: 'custom-route-start',
                            iconSize: [28,28],
                            iconAnchor: [14,28]
                        });

                        var m1 = L.marker(fromCoords, {icon: startIcon}).addTo(map).bindPopup('<b>🟢 Start:</b> ' + fromStr).openPopup();
                        var m2 = L.marker(toCoords, {icon: destIcon}).addTo(map).bindPopup('<b>📍 Destination:</b> ' + toStr);
                        dynamicRouteMarkers.push(m1, m2);

                        map.fitBounds(currentRouteLine.getBounds(), { padding: [60, 60] });
                    }

                    // --- TARGET RESOURCE LOGIC ---
                    var targetResourceName = "$targetResourceName";
                    if (targetResourceName && targetResourceName !== "null") {
                        var targetLat = $targetLatStr;
                        var targetLng = $targetLngStr;
                        // Place a massive green zone around the resource
                        addSafeZone(targetLat, targetLng, targetResourceName, 20000);
                        addPOI(targetLat, targetLng, 'resource', targetResourceName);

                        // Auto-draw route
                        setTimeout(function() {
                            document.getElementById("fromLoc").value = $lat + ", " + $lng;
                            document.getElementById("toLoc").value = targetLat + ", " + targetLng;
                            drawDynamicSafeRoute();
                        }, 800);
                    } else {
                        // --- 2. Exactly 2 Green Zones ---
                        // Zone 1: Center is safely offset (~15km away), but massive radius (25km) completely overlaps the user's location seamlessly!
                        addSafeZone($lat + 0.1, $lng - 0.1, "Primary Relief Camp", 25000); 
                        // Zone 2: Far away safe zone (~200+ km away)
                        addSafeZone($lat - 1.8, $lng + 1.6, "Secondary Evacuation Center", 20000);
                    }

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
