package com.example.tictactoe

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.tictactoe.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as androidx.navigation.fragment.NavHostFragment
        val navController = navHostFragment.navController
        androidx.navigation.ui.NavigationUI.setupWithNavController(binding.bottomNav, navController)
        
        // Hide bottom nav in game screens
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.dashboardFragment, R.id.leaderboardFragment, R.id.profileFragment -> {
                    binding.bottomNav.visibility = android.view.View.VISIBLE
                }
                else -> {
                    binding.bottomNav.visibility = android.view.View.GONE
                }
            }
        }
    }
}
