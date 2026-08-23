package com.example.tictactoe

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tictactoe.databinding.FragmentWelcomeBinding

class WelcomeFragment : Fragment() {

    private var _binding: FragmentWelcomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWelcomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", android.content.Context.MODE_PRIVATE)
        val savedUsername = sharedPref.getString("username", "")
        binding.etUsername.setText(savedUsername)

        binding.btnPlayerX.setOnClickListener {
            startGame("X")
        }

        binding.btnPlayerO.setOnClickListener {
            startGame("O")
        }
    }

    private fun startGame(startingPlayer: String) {
        val username = binding.etUsername.text.toString()
        if (username.isNotEmpty()) {
            val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", android.content.Context.MODE_PRIVATE)
            sharedPref.edit().putString("username", username).apply()
        }

        val size = when (binding.rgSize.checkedRadioButtonId) {
            R.id.rb4x4 -> 4
            R.id.rb5x5 -> 5
            else -> 3
        }

        val bundle = Bundle().apply {
            putString("startingPlayer", startingPlayer)
            putBoolean("isInfinityMode", binding.switchInfinityMode.isChecked)
            putBoolean("isAiMode", binding.switchAiMode.isChecked)
            putBoolean("isArcadeMode", binding.switchArcadeMode.isChecked)
            putInt("boardSize", size)
            putString("username", username)
        }
        findNavController().navigate(R.id.action_welcomeFragment_to_gameFragment, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
