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

        val wins = sharedPref.getInt("wins", 0)
        val losses = sharedPref.getInt("losses", 0)
        val tier = QuestManager.getLeagueTier(xp).first.replace(" League", "").uppercase()

        binding.tvProfileName.text = username
        binding.tvAvatarInitials.text = initialsOf(username)
        binding.tvProfileLevel.text = "LEVEL $level · $tier · $xp XP"
        binding.tvProfileStreak.text = "🔥 $streak"
        binding.tvProfileCoins.text = "🪙 $coins"
        binding.tvStatWins.text = "$wins"
        binding.tvStatWinRate.text = if (wins + losses > 0) "${wins * 100 / (wins + losses)}%" else "—"

        binding.tvProfileName.setOnClickListener { showEditUsernameDialog() }
        binding.rowEditProfile.setOnClickListener { showEditUsernameDialog() }
    }

    private fun initialsOf(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.size >= 2 -> (parts[0].take(1) + parts[1].take(1)).uppercase()
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "?"
        }
    }

    private fun showEditUsernameDialog() {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val currentName = sharedPref.getString("username", "") ?: ""

        val container = android.widget.FrameLayout(requireContext()).apply {
            setPadding(50, 20, 50, 20)
        }
        val input = android.widget.EditText(requireContext()).apply {
            hint = "Foydalanuvchi ismingizni kiriting"
            if (currentName.isNotEmpty()) setText(currentName)
            setSingleLine()
            setPadding(30, 30, 30, 30)
            background = requireContext().getDrawable(R.drawable.edittext_bg)
        }
        container.addView(input)

        AlertDialog.Builder(requireContext())
            .setTitle("👤 Ismni o'zgartirish")
            .setMessage("Yangi foydalanuvchi ismini (username) kiriting:")
            .setView(container)
            .setPositiveButton("Saqlash 💾") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    updateUsernameOnServer(newName)
                } else {
                    android.widget.Toast.makeText(context, "Ism bo'sh bo'lishi mumkin emas", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Bekor qilish", null)
            .show()
    }

    private fun updateUsernameOnServer(newUsername: String) {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        var deviceId = sharedPref.getString("device_id", "") ?: ""
        if (deviceId.isEmpty()) {
            deviceId = java.util.UUID.randomUUID().toString()
            sharedPref.edit().putString("device_id", deviceId).apply()
        }

        sharedPref.edit().putString("username", newUsername).apply()
        loadProfile()

        val pd = android.app.ProgressDialog(context).apply {
            setMessage("Profil saqlanmoqda...")
            show()
        }

        com.example.tictactoe.network.ApiClient.instance.auth(com.example.tictactoe.network.AuthRequest(deviceId, newUsername))
            .enqueue(object : retrofit2.Callback<com.example.tictactoe.network.AuthResponse> {
                override fun onResponse(call: retrofit2.Call<com.example.tictactoe.network.AuthResponse>, response: retrofit2.Response<com.example.tictactoe.network.AuthResponse>) {
                    try { pd.dismiss() } catch (_: Exception) {}
                    if (response.isSuccessful && response.body()?.status == "success") {
                        val user = response.body()?.user
                        if (user != null) {
                            sharedPref.edit().apply {
                                putInt("user_id", user.id)
                                putString("username", user.username)
                                putInt("level", user.level)
                                putInt("xp", user.xp)
                                putInt("coins", user.coins)
                                putInt("streak_count", user.streak_count)
                                apply()
                            }
                            loadProfile()
                            android.widget.Toast.makeText(context, "Ism yangilandi: ${user.username} 🎉", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        android.widget.Toast.makeText(context, "Ism saqlandi: $newUsername", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: retrofit2.Call<com.example.tictactoe.network.AuthResponse>, t: Throwable) {
                    try { pd.dismiss() } catch (_: Exception) {}
                    android.widget.Toast.makeText(context, "Ism saqlandi: $newUsername", android.widget.Toast.LENGTH_SHORT).show()
                }
            })
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
            "ru" -> "РУССКИЙ ›"
            "uz" -> "O'ZBEK ›"
            else -> "ENGLISH ›"
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
