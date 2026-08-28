package com.example.tictactoe

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
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
        initSettings()
    }

    private fun loadProfile() {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val username = sharedPref.getString("username", "Guest") ?: "Guest"
        val level = sharedPref.getInt("level", 1)
        val xp = sharedPref.getInt("xp", 0)
        val coins = sharedPref.getInt("coins", 0)
        val streak = sharedPref.getInt("streak_count", 0)

        binding.tvProfileName.text = username
        binding.tvProfileLevel.text = "Level $level | $xp XP"
        binding.tvProfileStreak.text = "🔥 Streak: $streak"
        binding.tvProfileCoins.text = "🪙 Coins: $coins"
    }

    private fun initSettings() {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)

        // 1. Language
        updateLanguageLabel()
        binding.rowLanguage.setOnClickListener {
            showLanguageDialog()
        }

        // 2. Dark Mode
        val isDark = ThemeHelper.isDarkMode(requireContext())
        binding.switchDarkMode.isChecked = isDark
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            ThemeHelper.setDarkMode(requireContext(), isChecked)
        }

        // 3. Sound Effects
        binding.switchSound.isChecked = SoundHelper.isSoundEnabled(requireContext())
        binding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            SoundHelper.setSoundEnabled(requireContext(), isChecked)
            if (isChecked) SoundHelper.playMoveSound(requireContext())
        }

        // 4. Vibration
        binding.switchVibration.isChecked = HapticHelper.isVibrationEnabled(requireContext())
        binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
            HapticHelper.setVibrationEnabled(requireContext(), isChecked)
            if (isChecked) HapticHelper.performClick(requireContext())
        }
    }

    private fun updateLanguageLabel() {
        val lang = LocaleHelper.getLanguage(requireContext())
        binding.tvCurrentLanguage.text = when (lang) {
            "ru" -> "🇷🇺 Русский"
            "uz" -> "🇺🇿 O'zbekcha"
            else -> "🇬🇧 English"
        }
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("🇺🇿 O'zbekcha", "🇬🇧 English", "🇷🇺 Русский")
        val langCodes = arrayOf("uz", "en", "ru")
        val currentLang = LocaleHelper.getLanguage(requireContext())
        val selectedIndex = langCodes.indexOf(currentLang).coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.settings_language))
            .setSingleChoiceItems(languages, selectedIndex) { dialog, which ->
                val newLang = langCodes[which]
                if (newLang != currentLang) {
                    LocaleHelper.setLocale(requireContext(), newLang)
                    dialog.dismiss()
                    // Restart Activity to reload string resources smoothly
                    val intent = Intent(requireActivity(), MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                } else {
                    dialog.dismiss()
                }
            }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
