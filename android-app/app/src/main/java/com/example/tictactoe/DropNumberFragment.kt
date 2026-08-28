package com.example.tictactoe

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tictactoe.databinding.FragmentDropNumberBinding

class DropNumberFragment : Fragment() {

    private var _binding: FragmentDropNumberBinding? = null
    private val binding get() = _binding!!

    private val logic = DropNumberLogic(cols = 5, rows = 7)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDropNumberBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnDropBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnRestart.setOnClickListener { restartGame() }

        binding.dropBoardView.setLogic(logic)
        binding.dropBoardView.onColumnTapped = { col ->
            handleColumnDrop(col)
        }

        binding.btnBomb.setOnClickListener {
            useBombPowerup()
        }

        updateUI()
    }

    private fun handleColumnDrop(col: Int) {
        if (logic.isGameOver) return

        HapticHelper.performClick(requireContext())
        SoundHelper.playMoveSound(requireContext())

        val success = logic.dropTile(col)
        binding.dropBoardView.invalidate()
        updateUI()

        if (success) {
            HapticHelper.performHeavyImpact(requireContext())
            SoundHelper.playCaptureSound(requireContext())
        }

        if (logic.isWin) {
            handleVictory()
        } else if (logic.isGameOver) {
            handleGameOver()
        }
    }

    private fun updateUI() {
        binding.tvDropScore.text = "Score: ${logic.score}"
        binding.tvTargetTile.text = "🎯 Target: ${logic.targetTile}"

        // Update Shooter Tile
        binding.tvCurrentShooter.text = logic.currentTile.toString()
        binding.tvCurrentShooter.backgroundTintList = ColorStateList.valueOf(DropNumberView.getTileColor(logic.currentTile))
        binding.tvCurrentShooter.setTextColor(if (logic.currentTile in 2..4) Color.parseColor("#1E293B") else Color.WHITE)

        // Update Next Tile
        binding.tvNextShooter.text = logic.nextTile.toString()
        binding.tvNextShooter.backgroundTintList = ColorStateList.valueOf(DropNumberView.getTileColor(logic.nextTile))
        binding.tvNextShooter.setTextColor(if (logic.nextTile in 2..4) Color.parseColor("#1E293B") else Color.WHITE)
    }

    private fun restartGame() {
        logic.initGame(2048)
        binding.dropBoardView.invalidate()
        updateUI()
        Toast.makeText(context, "Game Restarted! 🎯", Toast.LENGTH_SHORT).show()
    }

    private fun useBombPowerup() {
        val prefs = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val coins = prefs.getInt("coins", 0)
        if (coins < 25) {
            Toast.makeText(context, "Not enough coins! (Needs 25 🪙)", Toast.LENGTH_SHORT).show()
            return
        }

        prefs.edit().putInt("coins", coins - 25).apply()
        HapticHelper.performHeavyImpact(requireContext())
        SoundHelper.playCaptureSound(requireContext())

        logic.bombBottomRow()
        binding.dropBoardView.invalidate()
        updateUI()
        Toast.makeText(context, "💣 Bottom row cleared (-25 🪙)!", Toast.LENGTH_SHORT).show()
    }

    private fun handleVictory() {
        ConfettiView.show(binding.root as ViewGroup)
        HapticHelper.performVictory(requireContext())
        SoundHelper.playVictorySound(requireContext())

        val prefs = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val curCoins = prefs.getInt("coins", 0) + 100
        val curXp = prefs.getInt("xp", 0) + 250
        prefs.edit()
            .putInt("coins", curCoins)
            .putInt("xp", curXp)
            .apply()

        QuestManager.recordGamePlayed(requireContext(), "drop_number", false, true)

        AlertDialog.Builder(requireContext())
            .setTitle("🏆 2048 MERGE VICTORY!")
            .setMessage("You reached the ${logic.targetTile} Tile!\nScore: ${logic.score}\nReward: +100 Coins 🪙 | +250 XP ⚡")
            .setPositiveButton("CONTINUE TO 4096 🚀") { _, _ ->
                logic.targetTile *= 2
                logic.isWin = false
                updateUI()
            }
            .setNegativeButton("MENU") { _, _ ->
                findNavController().navigateUp()
            }
            .setCancelable(false)
            .show()
    }

    private fun handleGameOver() {
        val prefs = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val coins = prefs.getInt("coins", 0)

        AlertDialog.Builder(requireContext())
            .setTitle("💥 Board Full!")
            .setMessage("The numbers reached the top!\nFinal Score: ${logic.score}\n\nRevive with Bomb for 25 🪙?")
            .setPositiveButton("💣 REVIVE (25 🪙)") { _, _ ->
                if (coins >= 25) {
                    useBombPowerup()
                } else {
                    Toast.makeText(context, "Not enough coins! (Needs 25 🪙)", Toast.LENGTH_SHORT).show()
                    restartGame()
                }
            }
            .setNegativeButton("RETRY 🔄") { _, _ ->
                restartGame()
            }
            .setNeutralButton("MENU", { _, _ ->
                findNavController().navigateUp()
            })
            .setCancelable(false)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
