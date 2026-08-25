package com.example.tictactoe

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tictactoe.databinding.FragmentWaterSortBinding

class WaterSortFragment : Fragment() {

    private var _binding: FragmentWaterSortBinding? = null
    private val binding get() = _binding!!

    private val logic = WaterSortLogic()
    private var selectedTubeIndex = -1
    private val tubeViews = mutableListOf<WaterTubeView>()

    private var currentPlayingLevel = 1
    private var unlockedLevel = 1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWaterSortBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadProgress()
        initSetupUI()

        binding.btnSetupBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnGameplayBack.setOnClickListener { handleBackNavigation() }
        binding.btnUndo.setOnClickListener { handleUndo() }
        binding.btnRestart.setOnClickListener { restartLevel() }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })
    }

    private fun loadProgress() {
        val prefs = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        unlockedLevel = prefs.getInt("water_sort_unlocked_level", 1)
        currentPlayingLevel = unlockedLevel.coerceIn(1, WaterSortLogic.MAX_LEVELS)
    }

    private fun saveLevelWin(level: Int, stars: Int) {
        val prefs = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val prevStars = prefs.getInt("water_sort_level_${level}_stars", 0)
        if (stars > prevStars) {
            prefs.edit().putInt("water_sort_level_${level}_stars", stars).apply()
        }

        if (level >= unlockedLevel && unlockedLevel < WaterSortLogic.MAX_LEVELS) {
            unlockedLevel = level + 1
            prefs.edit().putInt("water_sort_unlocked_level", unlockedLevel).apply()
        }

        // Award Coins & XP
        val rewardCoins = when (stars) {
            3 -> 60
            2 -> 40
            else -> 25
        }
        val curCoins = prefs.getInt("coins", 0)
        val curXp = prefs.getInt("xp", 0)
        prefs.edit()
            .putInt("coins", curCoins + rewardCoins)
            .putInt("xp", curXp + rewardCoins * 2)
            .apply()

        QuestManager.recordGamePlayed(requireContext(), "water_sort", false, true)
    }

    private fun handleBackNavigation() {
        if (binding.gameplayContainer.visibility == View.VISIBLE) {
            AlertDialog.Builder(requireContext())
                .setTitle("Exit Puzzle? (Boshqotirmadan chiqish)")
                .setMessage("Do you want to exit to the level map? (Bosqichlar xaritasiga qaytishni xohlaysizmi?)")
                .setPositiveButton("Yes, Exit (Ha)") { _, _ ->
                    showSetupScreen()
                }
                .setNegativeButton("Cancel (Yo'q)", null)
                .show()
        } else {
            findNavController().navigateUp()
        }
    }

    private fun initSetupUI() {
        showSetupScreen()
        updateTotalStarsBadge()

        binding.rvLevels.layoutManager = GridLayoutManager(requireContext(), 4)
        binding.rvLevels.adapter = LevelAdapter()

        binding.btnContinueLevel.text = "CONTINUE LEVEL $unlockedLevel ▶"
        binding.btnContinueLevel.setOnClickListener {
            startLevel(unlockedLevel)
        }
    }

    private fun updateTotalStarsBadge() {
        val prefs = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        var totalStars = 0
        for (i in 1..WaterSortLogic.MAX_LEVELS) {
            totalStars += prefs.getInt("water_sort_level_${i}_stars", 0)
        }
        binding.tvTotalStars.text = "⭐ $totalStars / ${WaterSortLogic.MAX_LEVELS * 3}"
    }

    private fun startLevel(levelNumber: Int) {
        currentPlayingLevel = levelNumber
        logic.initLevelByNumber(currentPlayingLevel)
        selectedTubeIndex = -1

        binding.tvTitle.text = "🧪 Level $currentPlayingLevel"
        binding.tvMoves.text = "Moves: 0"

        buildTubesUI()
        showGameplayScreen()
    }

    private fun restartLevel() {
        logic.initLevelByNumber(currentPlayingLevel)
        selectedTubeIndex = -1
        binding.tvMoves.text = "Moves: 0"
        buildTubesUI()
    }

    private fun buildTubesUI() {
        tubeViews.clear()
        binding.rowTop.removeAllViews()
        binding.rowBottom.removeAllViews()

        val totalTubes = logic.tubes.size
        val half = (totalTubes + 1) / 2

        for (i in 0 until totalTubes) {
            val tubeView = WaterTubeView(requireContext()).apply {
                val density = resources.displayMetrics.density
                val w = (64 * density).toInt()
                val h = (170 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(w, h).apply {
                    setMargins((6 * density).toInt(), 0, (6 * density).toInt(), 0)
                }
                setWaterColors(logic.tubes[i])
                setOnClickListener { handleTubeClick(i) }
            }

            tubeViews.add(tubeView)
            if (i < half) {
                binding.rowTop.addView(tubeView)
            } else {
                binding.rowBottom.addView(tubeView)
            }
        }
    }

    private var isPouring = false

    private fun handleTubeClick(index: Int) {
        if (isPouring) return

        if (selectedTubeIndex == -1) {
            // First tap: select source
            if (logic.tubes[index].isNotEmpty()) {
                selectedTubeIndex = index
                tubeViews[index].isSelectedTube = true
            }
        } else {
            // Second tap: destination
            if (selectedTubeIndex == index) {
                // Deselect
                tubeViews[selectedTubeIndex].isSelectedTube = false
                selectedTubeIndex = -1
            } else {
                val from = selectedTubeIndex
                val to = index

                if (logic.canPour(from, to)) {
                    val fromView = tubeViews[from]
                    val toView = tubeViews[to]

                    isPouring = true
                    fromView.isSelectedTube = false
                    selectedTubeIndex = -1

                    val isTiltRight = to > from
                    fromView.animatePour(
                        isTiltRight = isTiltRight,
                        onHalfWay = {
                            logic.pour(from, to)
                            fromView.setWaterColors(logic.tubes[from])
                            toView.setWaterColors(logic.tubes[to])
                            binding.tvMoves.text = "Moves: ${logic.movesCount}"
                        },
                        onComplete = {
                            isPouring = false
                            if (logic.isWin()) {
                                handleLevelCompleted()
                            }
                        }
                    )
                } else {
                    // Invalid dest: switch selection if valid source
                    tubeViews[selectedTubeIndex].isSelectedTube = false
                    if (logic.tubes[index].isNotEmpty()) {
                        selectedTubeIndex = index
                        tubeViews[index].isSelectedTube = true
                    } else {
                        selectedTubeIndex = -1
                    }
                }
            }
        }
    }

    private fun handleUndo() {
        if (logic.undo()) {
            selectedTubeIndex = -1
            for (i in logic.tubes.indices) {
                tubeViews[i].isSelectedTube = false
                tubeViews[i].setWaterColors(logic.tubes[i])
            }
            binding.tvMoves.text = "Moves: ${logic.movesCount}"
        }
    }

    private fun handleLevelCompleted() {
        val stars = logic.calculateStars(logic.movesCount, currentPlayingLevel)
        saveLevelWin(currentPlayingLevel, stars)
        updateTotalStarsBadge()

        val starsStr = "⭐".repeat(stars) + "☆".repeat(3 - stars)
        val coinsEarned = when (stars) { 3 -> 60; 2 -> 40; else -> 25 }

        AlertDialog.Builder(requireContext())
            .setTitle("🎉 Level $currentPlayingLevel Completed!")
            .setMessage("Rating: $starsStr\nMoves: ${logic.movesCount}\nReward: +$coinsEarned Coins 🪙")
            .setCancelable(false)
            .setPositiveButton(if (currentPlayingLevel < WaterSortLogic.MAX_LEVELS) "NEXT LEVEL ▶" else "CONTINUE") { _, _ ->
                if (currentPlayingLevel < WaterSortLogic.MAX_LEVELS) {
                    startLevel(currentPlayingLevel + 1)
                } else {
                    showSetupScreen()
                }
            }
            .setNegativeButton("LEVELS MAP 🗺️") { _, _ ->
                showSetupScreen()
            }
            .show()
    }

    private fun showSetupScreen() {
        binding.setupContainer.visibility = View.VISIBLE
        binding.gameplayContainer.visibility = View.GONE
        loadProgress()
        updateTotalStarsBadge()
        binding.btnContinueLevel.text = "CONTINUE LEVEL $unlockedLevel ▶"
        binding.rvLevels.adapter?.notifyDataSetChanged()
    }

    private fun showGameplayScreen() {
        binding.setupContainer.visibility = View.GONE
        binding.gameplayContainer.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ==========================================
    // RECYCLERVIEW ADAPTER FOR 50 LEVELS
    // ==========================================
    private inner class LevelAdapter : RecyclerView.Adapter<LevelAdapter.LevelViewHolder>() {

        inner class LevelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val cardLevel: CardView = itemView.findViewById(R.id.cardLevel)
            val tvLevelNumber: TextView = itemView.findViewById(R.id.tvLevelNumber)
            val tvLevelStars: TextView = itemView.findViewById(R.id.tvLevelStars)
            val ivLock: ImageView = itemView.findViewById(R.id.ivLock)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LevelViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_water_sort_level, parent, false)
            return LevelViewHolder(v)
        }

        override fun onBindViewHolder(holder: LevelViewHolder, position: Int) {
            val level = position + 1
            val isUnlocked = level <= unlockedLevel
            val prefs = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
            val stars = prefs.getInt("water_sort_level_${level}_stars", 0)

            holder.tvLevelNumber.text = level.toString()

            if (isUnlocked) {
                holder.ivLock.visibility = View.GONE
                holder.tvLevelNumber.visibility = View.VISIBLE
                holder.tvLevelStars.visibility = View.VISIBLE

                holder.tvLevelStars.text = if (stars > 0) "⭐".repeat(stars) else "☆☆☆"
                holder.cardLevel.setCardBackgroundColor(Color.parseColor(if (level == unlockedLevel) "#0D9488" else "#1E293B"))
                holder.tvLevelNumber.setTextColor(Color.WHITE)

                holder.cardLevel.setOnClickListener {
                    startLevel(level)
                }
            } else {
                holder.ivLock.visibility = View.VISIBLE
                holder.tvLevelNumber.visibility = View.GONE
                holder.tvLevelStars.visibility = View.GONE
                holder.cardLevel.setCardBackgroundColor(Color.parseColor("#0F172A"))

                holder.cardLevel.setOnClickListener {
                    Toast.makeText(context, "Level $level is locked 🔒. Complete Level ${level - 1} first!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun getItemCount(): Int = WaterSortLogic.MAX_LEVELS
    }
}
