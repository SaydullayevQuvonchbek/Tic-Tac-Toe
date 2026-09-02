package com.example.tictactoe

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.tictactoe.databinding.FragmentLeaderboardBinding
import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.LeaderboardResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LeaderboardFragment : Fragment() {

    private var _binding: FragmentLeaderboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: LeaderboardAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLeaderboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        val myUsername = sharedPref.getString("username", "Player") ?: "Player"

        adapter = LeaderboardAdapter(emptyList(), myUsername)
        binding.rvLeaderboard.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        binding.rvLeaderboard.adapter = adapter

        binding.tabGlobal.isActivated = true
        binding.tabFriends.setOnClickListener {
            Toast.makeText(context, "👥 Do'stlar reytingi tez orada!", Toast.LENGTH_SHORT).show()
        }
        binding.tabLeague.setOnClickListener {
            Toast.makeText(context, "🏆 Liga reytingi tez orada!", Toast.LENGTH_SHORT).show()
        }

        fetchLeaderboard()
    }

    private fun initialsOf(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.size >= 2 -> (parts[0].take(1) + parts[1].take(1)).uppercase()
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "?"
        }
    }

    private fun bindPodium(top: List<com.example.tictactoe.network.LeaderboardPlayer>) {
        val slots = listOf(
            Triple(binding.podium1Ini, binding.podium1Name, binding.podium1Xp),
            Triple(binding.podium2Ini, binding.podium2Name, binding.podium2Xp),
            Triple(binding.podium3Ini, binding.podium3Name, binding.podium3Xp)
        )
        slots.forEachIndexed { i, (ini, name, xp) ->
            val p = top.getOrNull(i)
            if (p != null) {
                ini.text = initialsOf(p.username)
                name.text = p.username
                xp.text = "${p.xp} XP"
            } else {
                ini.text = "–"
                name.text = "—"
                xp.text = "0 XP"
            }
        }
    }

    private fun fetchLeaderboard() {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvLeaderboard.visibility = View.GONE
        binding.cardMyRank.visibility = View.GONE

        ApiClient.instance.getLeaderboard().enqueue(object : Callback<LeaderboardResponse> {
            override fun onResponse(call: Call<LeaderboardResponse>, response: Response<LeaderboardResponse>) {
                binding.progressBar.visibility = View.GONE
                if (response.isSuccessful && response.body()?.status == "success") {
                    val fullList = response.body()?.leaderboard ?: emptyList()
                    val top10 = fullList.take(10)

                    val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
                    val myUsername = sharedPref.getString("username", "Player") ?: "Player"
                    val myLevel = sharedPref.getInt("level", 1)
                    val myXp = sharedPref.getInt("xp", 0)
                    val myWins = sharedPref.getInt("wins", 0)

                    bindPodium(top10)
                    adapter.updateData(top10.drop(3), myUsername)
                    binding.rvLeaderboard.visibility = View.VISIBLE

                    // Check if current user is in full leaderboard list
                    val userInList = fullList.firstOrNull { it.username.equals(myUsername, ignoreCase = true) }
                    val myRank = userInList?.rank
                    val userXp = userInList?.xp ?: myXp
                    val userWins = userInList?.wins ?: myWins
                    val userLevel = userInList?.level ?: myLevel

                    // Display Current User's Pinned Bottom Card
                    binding.cardMyRank.visibility = View.VISIBLE
                    binding.tvMyPlayerName.text = "$myUsername"
                    binding.tvMyStats.text = "$userXp XP • $userWins Wins"
                    binding.tvMyLevel.text = "Lvl $userLevel"

                    val league = QuestManager.getLeagueTier(userXp)
                    binding.tvMyLeagueBadge.text = league.first
                    binding.tvMyLeagueBadge.setTextColor(Color.parseColor(league.second))

                    if (myRank != null && myRank <= 10) {
                        binding.tvMyRank.text = "#$myRank"
                        binding.tvMyRank.setTextColor(Color.parseColor("#F59E0B"))
                        binding.tvMyRankHint.text = "🎉 Awesome! You are ranked #$myRank in the Global Top 10!"
                        binding.tvMyRankHint.setTextColor(Color.parseColor("#10B981"))
                    } else if (myRank != null) {
                        binding.tvMyRank.text = "#$myRank"
                        binding.tvMyRank.setTextColor(Color.parseColor("#38BDF8"))

                        val rank10Xp = if (top10.size >= 10) top10[9].xp else 0
                        val neededXp = (rank10Xp - userXp + 1).coerceAtLeast(1)
                        binding.tvMyRankHint.text = "🔥 You are ranked #$myRank. Earn $neededXp more XP to enter the Top 10!"
                        binding.tvMyRankHint.setTextColor(Color.parseColor("#F59E0B"))
                    } else {
                        val rank10Xp = if (top10.size >= 10) top10[9].xp else 50
                        val neededXp = (rank10Xp - userXp + 1).coerceAtLeast(1)
                        binding.tvMyRank.text = if (fullList.isNotEmpty()) "#${fullList.size + 1}+" else "#1"
                        binding.tvMyRank.setTextColor(Color.parseColor("#94A3B8"))
                        binding.tvMyRankHint.text = "🔥 Win matches and earn $neededXp more XP to enter the Top 10!"
                        binding.tvMyRankHint.setTextColor(Color.parseColor("#F59E0B"))
                    }
                } else {
                    Toast.makeText(context, "Failed to load leaderboard", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<LeaderboardResponse>, t: Throwable) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(context, "Network Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
