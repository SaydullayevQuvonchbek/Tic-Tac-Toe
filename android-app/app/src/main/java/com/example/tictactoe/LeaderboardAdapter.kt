package com.example.tictactoe

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tictactoe.network.LeaderboardPlayer

class LeaderboardAdapter(
    private var players: List<LeaderboardPlayer>,
    private var currentUsername: String = ""
) : RecyclerView.Adapter<LeaderboardAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRank: TextView = view.findViewById(R.id.tvRank)
        val tvAvatar: TextView = view.findViewById(R.id.tvAvatar)
        val tvPlayerName: TextView = view.findViewById(R.id.tvPlayerName)
        val tvYouBadge: TextView = view.findViewById(R.id.tvYouBadge)
        val tvLevel: TextView = view.findViewById(R.id.tvLevel)
        val tvXpWins: TextView = view.findViewById(R.id.tvXpWins)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leaderboard_player, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val player = players[position]
        val isMe = currentUsername.isNotEmpty() && player.username.equals(currentUsername, ignoreCase = true)

        holder.tvRank.text = player.rank.toString()
        holder.tvAvatar.text = initialsOf(player.username)
        holder.tvPlayerName.text = player.username
        holder.tvYouBadge.visibility = if (isMe) View.VISIBLE else View.GONE
        holder.tvLevel.text = "LVL ${player.level}"
        holder.tvXpWins.text = "${player.xp} XP • ${player.wins} Wins"
    }

    override fun getItemCount() = players.size

    fun updateData(newPlayers: List<LeaderboardPlayer>, myUsername: String = currentUsername) {
        players = newPlayers
        currentUsername = myUsername
        notifyDataSetChanged()
    }

    private fun initialsOf(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.size >= 2 -> (parts[0].take(1) + parts[1].take(1)).uppercase()
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "?"
        }
    }
}
