package com.example.disasterawarerouting

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.TextView
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val splashIcon = findViewById<ImageView>(R.id.splashIcon)
        val pulseRing = findViewById<View>(R.id.pulseRing)
        val splashTitle = findViewById<TextView>(R.id.splashTitle)
        val splashSubtitle = findViewById<TextView>(R.id.splashSubtitle)

        // 1. Fade in and slide up for text
        val textFade = AlphaAnimation(0f, 1f).apply { duration = 1000; startOffset = 500 }
        splashTitle.startAnimation(textFade)
        splashSubtitle.startAnimation(textFade)

        // 2. Icon pop in
        val iconScale = ScaleAnimation(
            0f, 1f, 0f, 1f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply { duration = 800 }
        
        val iconSet = AnimationSet(true).apply {
            addAnimation(iconScale)
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 800 })
        }
        splashIcon.startAnimation(iconSet)

        // 3. Radar Pulse effect
        val pulseScale = ScaleAnimation(
            1f, 3f, 1f, 3f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply { 
            duration = 1500
            repeatCount = Animation.INFINITE
            repeatMode = Animation.RESTART
        }
        val pulseFade = AlphaAnimation(1f, 0f).apply {
            duration = 1500
            repeatCount = Animation.INFINITE
            repeatMode = Animation.RESTART
        }
        val pulseSet = AnimationSet(false).apply {
            addAnimation(pulseScale)
            addAnimation(pulseFade)
        }
        pulseRing.startAnimation(pulseSet)

        // Navigate to appropriate Auth Screen after 2.5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                // User is already logged in
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                // Not logged in, go to Login Screen
                startActivity(Intent(this, LoginActivity::class.java))
            }
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, 2500)
    }
}
