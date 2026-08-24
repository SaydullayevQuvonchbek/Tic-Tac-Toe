package com.example.tictactoe

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.tictactoe.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadProfile()
    }

    private fun loadProfile() {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", android.content.Context.MODE_PRIVATE)
        val username = sharedPref.getString("username", "Guest") ?: "Guest"
        val level = sharedPref.getInt("level", 1)
        val xp = sharedPref.getInt("xp", 0)
        val coins = sharedPref.getInt("coins", 0)
        val streak = sharedPref.getInt("streak_count", 0)

        binding.tvProfileName.text = username
        binding.tvProfileLevel.text = "Level $level | $xp XP"
        binding.tvProfileStreak.text = "🔥 Streak: $streak days"
        binding.tvProfileCoins.text = "🪙 Coins: $coins"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
