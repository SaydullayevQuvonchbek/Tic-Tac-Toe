package com.example.tictactoe

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.tictactoe.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val isDark = ThemeHelper.isDarkMode(this)
        val insetsController = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark

        // Ensure user is authenticated and has Sanctum token in background
        com.example.tictactoe.network.AuthManager.ensureAuthenticated(this)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as androidx.navigation.fragment.NavHostFragment
        val navController = navHostFragment.navController
        androidx.navigation.ui.NavigationUI.setupWithNavController(binding.bottomNav, navController)
        
        // Hide bottom nav in game screens
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.dashboardFragment, R.id.storeFragment, R.id.leaderboardFragment, R.id.profileFragment -> {
                    binding.bottomNav.visibility = android.view.View.VISIBLE
                }
                else -> {
                    binding.bottomNav.visibility = android.view.View.GONE
                }
            }
        }
    }
}
