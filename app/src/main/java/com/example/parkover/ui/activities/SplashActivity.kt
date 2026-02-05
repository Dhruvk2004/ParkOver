package com.example.parkover.ui.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.parkover.R
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    private val splashDuration = 4000L
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make status bar and navigation bar dark for splash screen
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.parseColor("#1A1A1A")
        window.navigationBarColor = android.graphics.Color.parseColor("#1A1A1A")
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        
        setContentView(R.layout.activity_splash)

        val logoContainer = findViewById<View>(R.id.logoContainer)
        val darkOverlay = findViewById<View>(R.id.darkOverlay)
        val borderFrame = findViewById<View>(R.id.borderFrame)

        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        val fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out)

        Handler(Looper.getMainLooper()).postDelayed({
            logoContainer.visibility = View.VISIBLE
            logoContainer.alpha = 1f
            logoContainer.startAnimation(fadeIn)
        }, 500)

        Handler(Looper.getMainLooper()).postDelayed({
            fadeOut.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationRepeat(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    logoContainer.visibility = View.INVISIBLE
                }
            })
            logoContainer.startAnimation(fadeOut)

            darkOverlay.animate()
                .alpha(0f)
                .setDuration(800)
                .withEndAction {
                    darkOverlay.visibility = View.GONE
                    borderFrame.visibility = View.VISIBLE
                    borderFrame.alpha = 0f
                    borderFrame.animate().alpha(1f).setDuration(400).start()
                }
                .start()
        }, 2500)

        Handler(Looper.getMainLooper()).postDelayed({
            navigateToNextScreen()
        }, splashDuration)
    }

    private fun navigateToNextScreen() {
        val intent = if (auth.currentUser != null) {
            // User is already logged in, go to MainActivity
            Intent(this, MainActivity::class.java)
        } else {
            // User not logged in, go to StartActivity
            Intent(this, StartActivity::class.java)
        }
        
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
