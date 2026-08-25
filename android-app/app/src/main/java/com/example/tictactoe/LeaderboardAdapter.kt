package com.example.tictactoe

import android.graphics.Color
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

        holder.tvPlayerName.text = player.username
        holder.tvYouBadge.visibility = if (isMe) View.VISIBLE else View.GONE
        holder.tvLevel.text = "Lvl ${player.level}"
        holder.tvXpWins.text = "${player.xp} XP • ${player.wins} Wins"

        // Gold, Silver, Bronze badges for top 3
        when (player.rank) {
            1 -> {
                holder.tvRank.text = "🥇 1"
                holder.tvRank.setTextColor(Color.parseColor("#F59E0B")) // Gold
                holder.tvRank.textSize = 14f
            }
            2 -> {
                holder.tvRank.text = "🥈 2"
                holder.tvRank.setTextColor(Color.parseColor("#CBD5E1")) // Silver
                holder.tvRank.textSize = 14f
            }
            3 -> {
                holder.tvRank.text = "🥉 3"
                holder.tvRank.setTextColor(Color.parseColor("#FB923C")) // Bronze
                holder.tvRank.textSize = 14f
            }
            else -> {
                holder.tvRank.text = player.rank.toString()
                holder.tvRank.setTextColor(Color.WHITE)
                holder.tvRank.textSize = 17f
            }
        }
    }

    override fun getItemCount() = players.size

    fun updateData(newPlayers: List<LeaderboardPlayer>, myUsername: String = currentUsername) {
        players = newPlayers
        currentUsername = myUsername
        notifyDataSetChanged()
    }
}
