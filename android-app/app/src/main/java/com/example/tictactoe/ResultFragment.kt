package com.example.tictactoe

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tictactoe.databinding.FragmentResultBinding

class ResultFragment : Fragment() {

    private var _binding: FragmentResultBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val resultMessage = arguments?.getString("resultMessage") ?: "Unknown"
        val isDraw = arguments?.getBoolean("isDraw") ?: false

        binding.tvResultMessage.text = resultMessage

        if (isDraw) {
            binding.tvHeader.text = "Draw"
            binding.ivResult.setImageResource(R.drawable.board)
            binding.tvSubMessage.text = "Congrats to both of you for equally excelling in the art of not winning."
            binding.btnAction.text = "REPLAY"
        } else {
            binding.tvHeader.text = "Winner"
            binding.ivResult.setImageResource(R.drawable.trophy)
            binding.tvSubMessage.text = "Congrats on being the undisputed champion of pressing buttons like a pro."
            binding.btnAction.text = "RESTART"
        }

        binding.btnAction.setOnClickListener {
            findNavController().navigate(R.id.action_resultFragment_to_welcomeFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
