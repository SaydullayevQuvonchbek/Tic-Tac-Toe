package com.example.tictactoe

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tictactoe.databinding.FragmentLeaderboardBinding
import com.example.tictactoe.network.ApiClient
import com.example.tictactoe.network.LeaderboardPlayer
import com.example.tictactoe.network.LeaderboardResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LeaderboardFragment : Fragment() {

    private var _binding: FragmentLeaderboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: LeaderboardAdapter
    private var currentTab = "GLOBAL"
    private var cachedGlobalPlayers: List<LeaderboardPlayer> = emptyList()

    private var myUsername: String = "Player"
    private var myLevel: Int = 1
    private var myXp: Int = 0
    private var myWins: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLeaderboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadUserData()

        adapter = LeaderboardAdapter(
            players = emptyList(),
            currentUsername = myUsername,
            showChallenge = false,
            onChallengeClicked = { player -> onPlayerChallenge(player) }
        )
        binding.rvLeaderboard.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        binding.rvLeaderboard.adapter = adapter

        // Setup Tabs
        binding.tabGlobal.setOnClickListener { switchTab("GLOBAL") }
        binding.tabFriends.setOnClickListener { switchTab("FRIENDS") }
        binding.tabLeague.setOnClickListener { switchTab("LIGA") }

        // Setup Add Friend Button
        binding.btnAddFriend.setOnClickListener {
            showAddFriendDialog()
        }

        // Setup Compact My Rank Click (gives quick feedback)
        binding.cardMyRankCompact.setOnClickListener {
            val league = LeagueManager.getCurrentLeague(myXp)
            Toast.makeText(
                context,
                "👤 $myUsername · ${league.name} (${league.badge}) · $myXp XP",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Default to Global tab
        switchTab("GLOBAL")
    }

    private fun loadUserData() {
        val sharedPref = requireActivity().getSharedPreferences("TicTacToePrefs", Context.MODE_PRIVATE)
        myUsername = sharedPref.getString("username", "Player") ?: "Player"
        myLevel = sharedPref.getInt("level", 1)
        myXp = sharedPref.getInt("xp", 0)
        myWins = sharedPref.getInt("wins", 0)
        updateCompactBadge(rank = null, xp = myXp)
    }

    private fun updateCompactBadge(rank: Int?, xp: Int) {
        if (_binding == null) return
        val rankStr = if (rank != null) "#$rank" else if (cachedGlobalPlayers.isNotEmpty()) "#${cachedGlobalPlayers.size + 1}+" else "#1"
        binding.tvMyRankMini.text = "Siz: $rankStr"
        binding.tvMyXpMini.text = "· $xp XP"
    }

    private fun switchTab(tab: String) {
        currentTab = tab
        binding.tabGlobal.isActivated = (tab == "GLOBAL")
        binding.tabFriends.isActivated = (tab == "FRIENDS")
        binding.tabLeague.isActivated = (tab == "LIGA")

        binding.podiumContainer.visibility = if (tab == "GLOBAL") View.VISIBLE else View.GONE
        binding.friendsHeaderContainer.visibility = if (tab == "FRIENDS") View.VISIBLE else View.GONE
        binding.leagueContainer.visibility = if (tab == "LIGA") View.VISIBLE else View.GONE

        when (tab) {
            "GLOBAL" -> {
                if (cachedGlobalPlayers.isEmpty()) {
                    fetchLeaderboard()
                } else {
                    renderGlobalLeaderboard(cachedGlobalPlayers)
                }
            }
            "FRIENDS" -> {
                renderFriendsList()
            }
            "LIGA" -> {
                renderLeagueStandings()
            }
        }
    }

    private fun renderGlobalLeaderboard(players: List<LeaderboardPlayer>) {
        bindPodium(players.take(3))
        // Pass all remaining players (no .take(10) truncation!)
        val listRest = players.drop(3)
        adapter.updateData(
            newPlayers = listRest,
            myUsername = myUsername,
            showChallengeButton = false
        )
        binding.rvLeaderboard.visibility = View.VISIBLE

        val userInList = players.firstOrNull { it.username.equals(myUsername, ignoreCase = true) }
        val rank = userInList?.rank
        val xp = userInList?.xp ?: myXp
        updateCompactBadge(rank, xp)
    }

    private fun renderFriendsList() {
        // 1. Render current cached friends immediately
        val cachedFriends = FriendsManager.getFriends(requireContext())
        displayFriends(cachedFriends)

        // 2. Fetch fresh friends from server asynchronously
        FriendsManager.fetchFriendsFromServer(requireContext()) { freshFriends ->
            activity?.runOnUiThread {
                if (!isAdded || _binding == null || currentTab != "FRIENDS") return@runOnUiThread
                displayFriends(freshFriends)
            }
        }
    }

    private fun displayFriends(friends: List<FriendsManager.Friend>) {
        binding.tvFriendsCount.text = "DO'STLARINGIZ (${friends.size})"

        val friendList = friends.map {
            LeaderboardPlayer(
                rank = 0,
                username = it.username,
                level = it.level,
                xp = it.xp,
                wins = it.wins
            )
        }.toMutableList()

        if (friendList.none { it.username.equals(myUsername, ignoreCase = true) }) {
            friendList.add(LeaderboardPlayer(0, myUsername, myLevel, myXp, myWins))
        }

        friendList.sortByDescending { it.xp }
        val rankedList = friendList.mapIndexed { idx, p ->
            LeaderboardPlayer(
                rank = idx + 1,
                username = p.username,
                level = p.level,
                xp = p.xp,
                wins = p.wins
            )
        }

        val myFriendRank = rankedList.firstOrNull { it.username.equals(myUsername, ignoreCase = true) }?.rank
        updateCompactBadge(myFriendRank, myXp)

        adapter.updateData(
            newPlayers = rankedList,
            myUsername = myUsername,
            showChallengeButton = true,
            onChallenge = { player -> onPlayerChallenge(player) }
        )
        binding.rvLeaderboard.visibility = View.VISIBLE
    }

    private fun renderLeagueStandings() {
        val league = LeagueManager.getCurrentLeague(myXp)
        binding.tvLeagueBadgeIcon.text = league.badge
        binding.tvCurrentLeagueName.text = league.name.uppercase()
        binding.tvCurrentLeagueName.setTextColor(Color.parseColor(league.colorHex))
        binding.tvLeagueTierDesc.text = league.description

        val nextLeague = LeagueManager.getNextLeague(myXp)
        val neededXp = LeagueManager.getXpNeededForNextLeague(myXp)

        if (nextLeague != null) {
            val tierRange = nextLeague.minXp - league.minXp
            val userTierProgress = (myXp - league.minXp).coerceAtLeast(0)
            binding.pbLeagueProgress.max = if (tierRange > 0) tierRange else 100
            binding.pbLeagueProgress.progress = userTierProgress
            binding.tvLeagueProgressText.text = "${nextLeague.name}ga o'tish uchun yana $neededXp XP kerak"
        } else {
            binding.pbLeagueProgress.max = 100
            binding.pbLeagueProgress.progress = 100
            binding.tvLeagueProgressText.text = "Eng yuqori Master ligadasiz! Chempion! 👑"
        }

        // Render initial local division
        val localPlayers = LeagueManager.getDivisionPlayers(league, myXp, myUsername)
        displayDivisionPlayers(localPlayers)

        // Fetch authoritative server division and season status
        LeagueManager.fetchDivisionPlayers(league, myXp, myUsername) { divisionPlayers ->
            activity?.runOnUiThread {
                if (!isAdded || _binding == null || currentTab != "LIGA") return@runOnUiThread
                displayDivisionPlayers(divisionPlayers)
            }
        }

        LeagueManager.fetchLeagueStatus { status ->
            activity?.runOnUiThread {
                if (!isAdded || _binding == null || currentTab != "LIGA" || status == null) return@runOnUiThread
                if (status.season != null && status.season.time_left_seconds > 0) {
                    val days = status.season.time_left_seconds / 86400
                    val hours = (status.season.time_left_seconds % 86400) / 3600
                    binding.tvLeagueTierDesc.text = "${league.description} • ⏳ Mavsum tugashiga: ${days}k ${hours}s"
                }
            }
        }
    }

    private fun displayDivisionPlayers(players: List<LeagueManager.LeaguePlayer>) {
        val rankedDivision = players.map {
            LeaderboardPlayer(
                rank = it.rank,
                username = it.username,
                level = (it.xp / 200).coerceAtLeast(1),
                xp = it.xp,
                wins = it.wins
            )
        }

        val myDivRank = rankedDivision.firstOrNull { it.username.equals(myUsername, ignoreCase = true) }?.rank
        updateCompactBadge(myDivRank, myXp)

        adapter.updateData(
            newPlayers = rankedDivision,
            myUsername = myUsername,
            showChallengeButton = true,
            onChallenge = { player -> onPlayerChallenge(player) }
        )
        binding.rvLeaderboard.visibility = View.VISIBLE
    }

    private fun showAddFriendDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Foydalanuvchi nomi..."
            setPadding(40, 30, 40, 30)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_color))
            setHintTextColor(ContextCompat.getColor(requireContext(), R.color.text_muted))
        }

        AlertDialog.Builder(requireContext())
            .setTitle("➕ Do'st qo'shish")
            .setMessage("Do'stingizning o'yindagi taxallusini (username) kiriting:")
            .setView(input)
            .setPositiveButton("Qo'shish") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val added = FriendsManager.addFriend(requireContext(), name)
                    if (added) {
                        Toast.makeText(context, "✅ $name do'stlaringizga qo'shildi!", Toast.LENGTH_SHORT).show()
                        if (currentTab == "FRIENDS") {
                            renderFriendsList()
                        }
                    } else {
                        Toast.makeText(context, "⚠️ Bu foydalanuvchi allaqachon do'stlaringiz ro'yxatida bor", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Bekor qilish", null)
            .show()
    }

    private fun onPlayerChallenge(player: LeaderboardPlayer) {
        val games = arrayOf("❌⭕ Tic-Tac-Toe", "♟️ Shaxmat (Chess)", "⚪ Shashka (Checkers)", "🔴 Connect 4")
        val gameKeys = arrayOf("tictactoe", "chess", "checkers", "connect4")

        AlertDialog.Builder(requireContext())
            .setTitle("⚔️ ${player.username} bilan bellashuv")
            .setItems(games) { _, which ->
                val selectedKey = gameKeys[which]
                val safeContext = context ?: return@setItems
                Toast.makeText(safeContext, "⚔️ ${player.username} ga taklif yuborilmoqda...", Toast.LENGTH_SHORT).show()

                val friendObj = FriendsManager.getFriends(safeContext).firstOrNull { it.username.equals(player.username, ignoreCase = true) }
                val targetId = friendObj?.user_id ?: 0

                if (targetId > 0) {
                    FriendsManager.challengeFriend(targetId, selectedKey) { success, roomCode, _ ->
                        if (!isAdded || _binding == null) return@challengeFriend
                        if (success && !roomCode.isNullOrEmpty()) {
                            launchGameWithRoom(selectedKey, roomCode, player.username)
                        } else {
                            launchLocalChallenge(selectedKey, player.username)
                        }
                    }
                } else {
                    launchLocalChallenge(selectedKey, player.username)
                }
            }
            .setNegativeButton("Bekor qilish", null)
            .show()
    }

    private fun launchGameWithRoom(gameKey: String, roomCode: String, opponentName: String) {
        val bundle = Bundle().apply {
            putString("roomCode", roomCode)
            putBoolean("isHost", true)
            putBoolean("isOnlineMode", true)
            putString("opponent_name", opponentName)
        }
        when (gameKey) {
            "chess" -> findNavController().navigate(R.id.chessFragment, bundle)
            "checkers" -> findNavController().navigate(R.id.checkersFragment, bundle)
            "connect4" -> findNavController().navigate(R.id.connect4Fragment, bundle)
            else -> findNavController().navigate(R.id.gameFragment, bundle)
        }
    }

    private fun launchLocalChallenge(gameKey: String, opponentName: String) {
        val bundle = Bundle().apply {
            putBoolean("is_quick_match", true)
            putString("opponent_name", opponentName)
        }
        when (gameKey) {
            "chess" -> findNavController().navigate(R.id.chessFragment, bundle)
            "checkers" -> findNavController().navigate(R.id.checkersFragment, bundle)
            "connect4" -> findNavController().navigate(R.id.connect4Fragment, bundle)
            else -> findNavController().navigate(R.id.gameFragment, bundle)
        }
    }

    private fun initialsOf(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.size >= 2 -> (parts[0].take(1) + parts[1].take(1)).uppercase()
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "?"
        }
    }

    private fun bindPodium(top: List<LeaderboardPlayer>) {
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

        ApiClient.instance.getLeaderboard().enqueue(object : Callback<LeaderboardResponse> {
            override fun onResponse(call: Call<LeaderboardResponse>, response: Response<LeaderboardResponse>) {
                if (!isAdded || _binding == null) return
                binding.progressBar.visibility = View.GONE
                if (response.isSuccessful && response.body()?.status == "success") {
                    val list = response.body()?.leaderboard ?: emptyList()
                    cachedGlobalPlayers = if (list.isNotEmpty()) list else generateFallbackPlayers()
                    if (currentTab == "GLOBAL") {
                        renderGlobalLeaderboard(cachedGlobalPlayers)
                    }
                } else {
                    useFallbackLeaderboard()
                }
            }

            override fun onFailure(call: Call<LeaderboardResponse>, t: Throwable) {
                if (!isAdded || _binding == null) return
                binding.progressBar.visibility = View.GONE
                useFallbackLeaderboard()
            }
        })
    }

    private fun useFallbackLeaderboard() {
        if (cachedGlobalPlayers.isEmpty()) {
            cachedGlobalPlayers = generateFallbackPlayers()
        }
        if (currentTab == "GLOBAL") {
            renderGlobalLeaderboard(cachedGlobalPlayers)
        }
    }

    private fun generateFallbackPlayers(): List<LeaderboardPlayer> {
        return listOf(
            LeaderboardPlayer(1, "Jahongir_King", 15, 6420, 184),
            LeaderboardPlayer(2, "Sardor_Dev", 14, 5890, 162),
            LeaderboardPlayer(3, "Nodira_Master", 13, 5120, 140),
            LeaderboardPlayer(4, "Sherzod_Pro", 12, 4780, 125),
            LeaderboardPlayer(5, "Alisher_UZB", 11, 4310, 118),
            LeaderboardPlayer(6, "Malika_Star", 10, 3950, 99),
            LeaderboardPlayer(7, "Bobur_99", 9, 3420, 85),
            LeaderboardPlayer(8, "Ziyoda_Chess", 8, 2980, 74),
            LeaderboardPlayer(9, "Rustam_Fast", 7, 2610, 68),
            LeaderboardPlayer(10, "Farrux_Arena", 6, 2190, 53),
            LeaderboardPlayer(11, "Bekzod_Win", 5, 1840, 47),
            LeaderboardPlayer(12, "Otabek_Top", 5, 1530, 39),
            LeaderboardPlayer(13, "Kamola_Play", 4, 1280, 32),
            LeaderboardPlayer(14, "Azamat_Cool", 3, 980, 24),
            LeaderboardPlayer(15, "Madina_Smart", 2, 750, 18)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
