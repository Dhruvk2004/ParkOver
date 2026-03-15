package com.example.parkover.ui.activities

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.airbnb.lottie.LottieAnimationView
import com.example.parkover.R
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    private val splashDuration = 4500L
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make status bar and navigation bar transparent for immersive splash
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.parseColor("#1A1A1A")
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        
        setContentView(R.layout.activity_splash)

        val splashAnimation = findViewById<LottieAnimationView>(R.id.splashAnimation)
        val loadingAnimation = findViewById<LottieAnimationView>(R.id.loadingAnimation)
        val appName = findViewById<View>(R.id.appName)
        val tagline = findViewById<View>(R.id.tagline)
        val loadingContainer = findViewById<View>(R.id.loadingContainer)
        val decorCircle1 = findViewById<View>(R.id.decorCircle1)
        val decorCircle2 = findViewById<View>(R.id.decorCircle2)

        // Start decorative circles animation
        animateDecorativeCircles(decorCircle1, decorCircle2)

        // Start main animation sequence after a short delay
        Handler(Looper.getMainLooper()).postDelayed({
            // Play Lottie animation
            splashAnimation.playAnimation()
            
            // Animate app name with bounce effect
            Handler(Looper.getMainLooper()).postDelayed({
                animateTextIn(appName, 0)
            }, 800)
            
            // Animate tagline
            Handler(Looper.getMainLooper()).postDelayed({
                animateTextIn(tagline, 100)
            }, 1000)
            
            // Show loading indicator
            Handler(Looper.getMainLooper()).postDelayed({
                animateLoadingIn(loadingContainer)
            }, 1500)
            
        }, 300)

        // Navigate to next screen
        Handler(Looper.getMainLooper()).postDelayed({
            navigateToNextScreen()
        }, splashDuration)
    }

    private fun animateDecorativeCircles(circle1: View, circle2: View) {
        // Circle 1 - fade in and scale
        val fadeIn1 = ObjectAnimator.ofFloat(circle1, View.ALPHA, 0f, 0.6f).apply {
            duration = 1500
            interpolator = AccelerateDecelerateInterpolator()
        }
        val scaleX1 = ObjectAnimator.ofFloat(circle1, View.SCALE_X, 0.5f, 1f).apply {
            duration = 1500
            interpolator = AccelerateDecelerateInterpolator()
        }
        val scaleY1 = ObjectAnimator.ofFloat(circle1, View.SCALE_Y, 0.5f, 1f).apply {
            duration = 1500
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        // Circle 2 - delayed animation
        val fadeIn2 = ObjectAnimator.ofFloat(circle2, View.ALPHA, 0f, 0.4f).apply {
            duration = 1500
            startDelay = 300
            interpolator = AccelerateDecelerateInterpolator()
        }
        val scaleX2 = ObjectAnimator.ofFloat(circle2, View.SCALE_X, 0.5f, 1f).apply {
            duration = 1500
            startDelay = 300
            interpolator = AccelerateDecelerateInterpolator()
        }
        val scaleY2 = ObjectAnimator.ofFloat(circle2, View.SCALE_Y, 0.5f, 1f).apply {
            duration = 1500
            startDelay = 300
            interpolator = AccelerateDecelerateInterpolator()
        }

        AnimatorSet().apply {
            playTogether(fadeIn1, scaleX1, scaleY1, fadeIn2, scaleX2, scaleY2)
            start()
        }
    }

    private fun animateTextIn(view: View, delay: Long) {
        view.translationY = 30f
        view.alpha = 0f
        
        val fadeIn = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
            duration = 600
            startDelay = delay
        }
        val slideUp = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 30f, 0f).apply {
            duration = 600
            startDelay = delay
            interpolator = OvershootInterpolator(1.2f)
        }
        
        AnimatorSet().apply {
            playTogether(fadeIn, slideUp)
            start()
        }
    }

    private fun animateLoadingIn(view: View) {
        view.alpha = 0f
        view.translationY = 20f
        
        val fadeIn = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
            duration = 500
        }
        val slideUp = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 20f, 0f).apply {
            duration = 500
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        AnimatorSet().apply {
            playTogether(fadeIn, slideUp)
            start()
        }
    }

    private fun navigateToNextScreen() {
        val intent = if (auth.currentUser != null) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, StartActivity::class.java)
        }
        
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
