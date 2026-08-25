package com.example.tictactoe

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tictactoe.databinding.FragmentMemoryGameBinding

class MemoryGameFragment : Fragment() {

    private var _binding: FragmentMemoryGameBinding? = null
    private val binding get() = _binding!!

    private var currentPlayingLevel = 1
    private var unlockedLevel = 1

    private var firstCard: CardView? = null
    private var secondCard: CardView? = null
    private var isProcessing = false
    private var matches = 0
    private var totalPairs = 0
    private var totalTimeSeconds = 60
    private var timeRemainingSeconds = 60
    private var timer: CountDownTimer? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMemoryGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadProgress()
        initSetupUI()

        binding.btnSetupBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnBack.setOnClickListener { handleBackNavigation() }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })
    }

    private fun loadProgress() {
        val prefs = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        unlockedLevel = prefs.getInt("memory_unlocked_level", 1)
        currentPlayingLevel = unlockedLevel.coerceIn(1, MemoryCampaignConfig.MAX_LEVELS)
    }

    private fun saveLevelWin(level: Int, stars: Int) {
        val prefs = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val prevStars = prefs.getInt("memory_level_${level}_stars", 0)
        if (stars > prevStars) {
            prefs.edit().putInt("memory_level_${level}_stars", stars).apply()
        }

        if (level >= unlockedLevel && unlockedLevel < MemoryCampaignConfig.MAX_LEVELS) {
            unlockedLevel = level + 1
            prefs.edit().putInt("memory_unlocked_level", unlockedLevel).apply()
        }

        val rewardCoins = when (stars) {
            3 -> 50
            2 -> 30
            else -> 15
        }
        val curCoins = prefs.getInt("coins", 0)
        val curXp = prefs.getInt("xp", 0)
        prefs.edit()
            .putInt("coins", curCoins + rewardCoins)
            .putInt("xp", curXp + rewardCoins * 2)
            .apply()

        QuestManager.recordGamePlayed(requireContext(), "memory_matrix", false, true)
    }

    private fun handleBackNavigation() {
        if (binding.gameplayContainer.visibility == View.VISIBLE) {
            AlertDialog.Builder(requireContext())
                .setTitle("Exit Level?")
                .setMessage("Do you want to return to the campaign level map?")
                .setPositiveButton("Yes, Exit") { _, _ ->
                    timer?.cancel()
                    showSetupScreen()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            findNavController().navigateUp()
        }
    }

    private fun showSetupScreen() {
        timer?.cancel()
        binding.setupContainer.visibility = View.VISIBLE
        binding.gameplayContainer.visibility = View.GONE
        initSetupUI()
    }

    private fun initSetupUI() {
        loadProgress()
        binding.setupContainer.visibility = View.VISIBLE
        binding.gameplayContainer.visibility = View.GONE

        // Total Stars
        val prefs = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        var totalStars = 0
        for (i in 1..MemoryCampaignConfig.MAX_LEVELS) {
            totalStars += prefs.getInt("memory_level_${i}_stars", 0)
        }
        binding.tvTotalStars.text = "⭐ $totalStars / ${MemoryCampaignConfig.MAX_LEVELS * 3}"

        // Continue Button
        binding.btnContinueLevel.text = "CONTINUE LEVEL $currentPlayingLevel ▶"
        binding.btnContinueLevel.setOnClickListener {
            startLevel(currentPlayingLevel)
        }

        // Levels Grid
        binding.rvLevels.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvLevels.adapter = LevelsAdapter(unlockedLevel) { selectedLevel ->
            startLevel(selectedLevel)
        }
    }

    private fun startLevel(level: Int) {
        currentPlayingLevel = level
        val config = MemoryCampaignConfig.getLevelConfig(level)

        binding.setupContainer.visibility = View.GONE
        binding.gameplayContainer.visibility = View.VISIBLE

        binding.tvLevelTitle.text = "Level $level: ${config.themeName}"
        totalPairs = config.pairsCount
        matches = 0
        binding.tvMatches.text = "Matches: 0/$totalPairs"

        setupBoard(config)
        startTimer(config.timeSeconds)
    }

    private fun setupBoard(config: MemoryCampaignConfig.LevelConfig) {
        val rows = config.rows
        val cols = config.cols
        val count = rows * cols

        binding.gridMemory.removeAllViews()
        binding.gridMemory.rowCount = rows
        binding.gridMemory.columnCount = cols

        val shuffledPool = config.pool.shuffled().take(config.pairsCount)
        val cardItems = (shuffledPool + shuffledPool).shuffled()

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels - (32 * displayMetrics.density)
        val screenHeight = displayMetrics.heightPixels - (180 * displayMetrics.density)

        val cardWidth = (screenWidth / cols).toInt() - (8 * displayMetrics.density).toInt()
        val cardHeight = (screenHeight / rows).toInt() - (8 * displayMetrics.density).toInt()
        val cardSize = Math.min(cardWidth, cardHeight).coerceIn(44, 96)

        val marginPx = (3 * displayMetrics.density).toInt()

        for (i in 0 until count) {
            val card = CardView(requireContext()).apply {
                val r = i / cols
                val c = i % cols
                val params = GridLayout.LayoutParams(
                    GridLayout.spec(r),
                    GridLayout.spec(c)
                ).apply {
                    width = cardSize
                    height = cardSize
                    setMargins(marginPx, marginPx, marginPx, marginPx)
                }
                layoutParams = params
                setCardBackgroundColor(Color.parseColor("#312E81"))
                radius = 16f
                cardElevation = 6f
                tag = cardItems[i]
            }

            val tv = TextView(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.CENTER
                textSize = if (cardSize > 64) 28f else 20f
                text = ""
                tag = "tv"
            }
            card.addView(tv)

            card.setOnClickListener { onCardClicked(card) }
            binding.gridMemory.addView(card)
        }
    }

    private fun onCardClicked(card: CardView) {
        if (isProcessing || card.alpha < 1f || card == firstCard) return

        flipCard(card, true)

        if (firstCard == null) {
            firstCard = card
        } else {
            secondCard = card
            isProcessing = true

            val firstEmoji = firstCard?.tag as String
            val secondEmoji = secondCard?.tag as String

            if (firstEmoji == secondEmoji) {
                // Match
                binding.root.postDelayed({
                    firstCard?.animate()?.alpha(0f)?.setDuration(250)?.start()
                    secondCard?.animate()?.alpha(0f)?.setDuration(250)?.start()

                    matches++
                    binding.tvMatches.text = "Matches: $matches/$totalPairs"

                    firstCard = null
                    secondCard = null
                    isProcessing = false

                    if (matches == totalPairs) {
                        endGame(true)
                    }
                }, 400)
            } else {
                // No Match
                binding.root.postDelayed({
                    firstCard?.let { flipCard(it, false) }
                    secondCard?.let { flipCard(it, false) }

                    firstCard = null
                    secondCard = null
                    isProcessing = false
                }, 700)
            }
        }
    }

    private fun flipCard(card: CardView, faceUp: Boolean) {
        val tv = card.findViewWithTag<TextView>("tv") ?: return

        card.animate().rotationYBy(90f).setDuration(120).withEndAction {
            if (faceUp) {
                tv.text = card.tag as String
                card.setCardBackgroundColor(Color.WHITE)
            } else {
                tv.text = ""
                card.setCardBackgroundColor(Color.parseColor("#312E81"))
            }
            card.animate().rotationYBy(90f).setDuration(120).start()
        }.start()
    }

    private fun startTimer(seconds: Int) {
        totalTimeSeconds = seconds
        timeRemainingSeconds = seconds
        timer?.cancel()

        timer = object : CountDownTimer((seconds * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemainingSeconds = (millisUntilFinished / 1000).toInt()
                binding.tvTimer.text = "⏳ ${timeRemainingSeconds}s"
            }

            override fun onFinish() {
                binding.tvTimer.text = "⏳ 0s"
                endGame(false)
            }
        }.start()
    }

    private fun endGame(win: Boolean) {
        timer?.cancel()
        if (win) {
            val ratio = timeRemainingSeconds.toFloat() / totalTimeSeconds.toFloat()
            val stars = when {
                ratio >= 0.40f -> 3
                ratio >= 0.15f -> 2
                else -> 1
            }
            val rewardCoins = when (stars) {
                3 -> 50
                2 -> 30
                else -> 15
            }

            saveLevelWin(currentPlayingLevel, stars)

            val starStr = "⭐".repeat(stars)
            var msg = "🎉 Level $currentPlayingLevel Complete!\n\nRating: $starStr\nReward: +$rewardCoins Coins 🪙 | +${rewardCoins * 2} XP ⚡"

            val builder = AlertDialog.Builder(requireContext())
                .setTitle("Victory! 🏆")
                .setMessage(msg)
                .setCancelable(false)

            if (currentPlayingLevel < MemoryCampaignConfig.MAX_LEVELS) {
                builder.setPositiveButton("Next Level ▶") { _, _ ->
                    startLevel(currentPlayingLevel + 1)
                }
                builder.setNegativeButton("Level Map 🗺️") { _, _ ->
                    showSetupScreen()
                }
            } else {
                builder.setPositiveButton("Level Map 🗺️") { _, _ ->
                    showSetupScreen()
                }
            }
            builder.show()
        } else {
            AlertDialog.Builder(requireContext())
                .setTitle("Time's Up! ⏰")
                .setMessage("You ran out of time. Would you like to try Level $currentPlayingLevel again?")
                .setPositiveButton("Retry 🔄") { _, _ ->
                    startLevel(currentPlayingLevel)
                }
                .setNegativeButton("Level Map 🗺️") { _, _ ->
                    showSetupScreen()
                }
                .setCancelable(false)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
        _binding = null
    }

    // ==========================================
    // RECYCLERVIEW ADAPTER FOR CAMPAIGN MAP
    // ==========================================
    private inner class LevelsAdapter(
        private val currentUnlocked: Int,
        private val onLevelClick: (Int) -> Unit
    ) : RecyclerView.Adapter<LevelsAdapter.LevelViewHolder>() {

        private val prefs = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)

        inner class LevelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val cardLevel: CardView = itemView.findViewById(R.id.cardLevel)
            val tvLevelNumber: TextView = itemView.findViewById(R.id.tvLevelNumber)
            val tvLevelStars: TextView = itemView.findViewById(R.id.tvLevelStars)
            val ivLock: ImageView = itemView.findViewById(R.id.ivLock)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LevelViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_memory_level, parent, false)
            return LevelViewHolder(v)
        }

        override fun onBindViewHolder(holder: LevelViewHolder, position: Int) {
            val level = position + 1
            holder.tvLevelNumber.text = "$level"

            val isUnlocked = level <= currentUnlocked
            val stars = prefs.getInt("memory_level_${level}_stars", 0)

            if (isUnlocked) {
                holder.ivLock.visibility = View.GONE
                holder.tvLevelNumber.visibility = View.VISIBLE
                holder.tvLevelStars.visibility = View.VISIBLE
                holder.tvLevelStars.text = when (stars) {
                    3 -> "⭐⭐⭐"
                    2 -> "⭐⭐"
                    1 -> "⭐"
                    else -> "☆☆☆"
                }

                if (level == currentUnlocked) {
                    holder.cardLevel.setCardBackgroundColor(Color.parseColor("#4F46E5"))
                    holder.tvLevelNumber.setTextColor(Color.WHITE)
                } else {
                    holder.cardLevel.setCardBackgroundColor(
                        ContextCompat.getColor(holder.itemView.context, R.color.card_background)
                    )
                    holder.tvLevelNumber.setTextColor(
                        ContextCompat.getColor(holder.itemView.context, R.color.text_color)
                    )
                }

                holder.itemView.setOnClickListener { onLevelClick(level) }
            } else {
                holder.ivLock.visibility = View.VISIBLE
                holder.tvLevelNumber.visibility = View.GONE
                holder.tvLevelStars.visibility = View.GONE
                holder.cardLevel.setCardBackgroundColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.card_surface)
                )
                holder.itemView.setOnClickListener {
                    Toast.makeText(holder.itemView.context, "Complete Level ${level - 1} to unlock!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun getItemCount(): Int = MemoryCampaignConfig.MAX_LEVELS
    }
}
