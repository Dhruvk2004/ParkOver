package com.example.parkover.ui.activities

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.fragment.NavHostFragment
import com.example.parkover.R
import com.example.parkover.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    
    private lateinit var navHome: FrameLayout
    private lateinit var navSaved: FrameLayout
    private lateinit var navBooking: FrameLayout
    private lateinit var navProfile: FrameLayout
    
    private lateinit var bgHome: View
    private lateinit var bgSaved: View
    private lateinit var bgBooking: View
    private lateinit var bgProfile: View
    
    private lateinit var icHome: ImageView
    private lateinit var icSaved: ImageView
    private lateinit var icBooking: ImageView
    private lateinit var icProfile: ImageView
    
    private var currentTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        // Force light mode - dark mode not fully supported yet
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Set light status bar icons (dark icons on light background)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCustomBottomNav()
        setupWindowInsets()
    }

    private fun setupCustomBottomNav() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        // Get references to nav items
        navHome = findViewById(R.id.nav_home)
        navSaved = findViewById(R.id.nav_saved)
        navBooking = findViewById(R.id.nav_booking)
        navProfile = findViewById(R.id.nav_profile)
        
        bgHome = findViewById(R.id.bg_home)
        bgSaved = findViewById(R.id.bg_saved)
        bgBooking = findViewById(R.id.bg_booking)
        bgProfile = findViewById(R.id.bg_profile)
        
        icHome = findViewById(R.id.ic_home)
        icSaved = findViewById(R.id.ic_saved)
        icBooking = findViewById(R.id.ic_booking)
        icProfile = findViewById(R.id.ic_profile)

        // Set click listeners
        navHome.setOnClickListener {
            if (currentTab != 0) {
                navController.navigate(R.id.homeFragment)
                selectTab(0)
            }
        }
        
        navSaved.setOnClickListener {
            if (currentTab != 1) {
                navController.navigate(R.id.savedFragment)
                selectTab(1)
            }
        }
        
        navBooking.setOnClickListener {
            if (currentTab != 2) {
                navController.navigate(R.id.bookingFragment)
                selectTab(2)
            }
        }
        
        navProfile.setOnClickListener {
            if (currentTab != 3) {
                navController.navigate(R.id.profileFragment)
                selectTab(3)
            }
        }

        // Set initial state (no animation)
        selectTabInitial(0)
    }

    private fun selectTabInitial(index: Int) {
        val white = ContextCompat.getColor(this, R.color.white)
        val inactive = ContextCompat.getColor(this, R.color.nav_inactive)

        // Reset all
        bgHome.visibility = View.GONE
        bgSaved.visibility = View.GONE
        bgBooking.visibility = View.GONE
        bgProfile.visibility = View.GONE
        
        icHome.setColorFilter(inactive)
        icSaved.setColorFilter(inactive)
        icBooking.setColorFilter(inactive)
        icProfile.setColorFilter(inactive)

        // Select active
        when (index) {
            0 -> {
                bgHome.visibility = View.VISIBLE
                icHome.setColorFilter(white)
            }
            1 -> {
                bgSaved.visibility = View.VISIBLE
                icSaved.setColorFilter(white)
            }
            2 -> {
                bgBooking.visibility = View.VISIBLE
                icBooking.setColorFilter(white)
            }
            3 -> {
                bgProfile.visibility = View.VISIBLE
                icProfile.setColorFilter(white)
            }
        }
        currentTab = index
    }

    private fun selectTab(index: Int) {
        val white = ContextCompat.getColor(this, R.color.white)
        val inactive = ContextCompat.getColor(this, R.color.nav_inactive)

        val backgrounds = listOf(bgHome, bgSaved, bgBooking, bgProfile)
        val icons = listOf(icHome, icSaved, icBooking, icProfile)

        // Animate out previous tab
        val prevBg = backgrounds[currentTab]
        val prevIcon = icons[currentTab]
        
        // Fade out and scale down previous background
        val fadeOut = ObjectAnimator.ofFloat(prevBg, "alpha", 1f, 0f).setDuration(150)
        val scaleOutX = ObjectAnimator.ofFloat(prevBg, "scaleX", 1f, 0.8f).setDuration(150)
        val scaleOutY = ObjectAnimator.ofFloat(prevBg, "scaleY", 1f, 0.8f).setDuration(150)
        
        fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                prevBg.visibility = View.GONE
                prevBg.alpha = 1f
                prevBg.scaleX = 1f
                prevBg.scaleY = 1f
            }
        })

        AnimatorSet().apply {
            playTogether(fadeOut, scaleOutX, scaleOutY)
            start()
        }
        
        prevIcon.setColorFilter(inactive)

        // Animate in new tab
        val newBg = backgrounds[index]
        val newIcon = icons[index]
        
        newBg.visibility = View.VISIBLE
        newBg.alpha = 0f
        newBg.scaleX = 0.8f
        newBg.scaleY = 0.8f
        
        val fadeIn = ObjectAnimator.ofFloat(newBg, "alpha", 0f, 1f).setDuration(200)
        val scaleInX = ObjectAnimator.ofFloat(newBg, "scaleX", 0.8f, 1f).setDuration(250)
        val scaleInY = ObjectAnimator.ofFloat(newBg, "scaleY", 0.8f, 1f).setDuration(250)
        
        scaleInX.interpolator = OvershootInterpolator(1.5f)
        scaleInY.interpolator = OvershootInterpolator(1.5f)

        AnimatorSet().apply {
            playTogether(fadeIn, scaleInX, scaleInY)
            startDelay = 50
            start()
        }
        
        newIcon.setColorFilter(white)
        
        currentTab = index
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainContainer) { _, insets ->
            insets
        }
    }
}
