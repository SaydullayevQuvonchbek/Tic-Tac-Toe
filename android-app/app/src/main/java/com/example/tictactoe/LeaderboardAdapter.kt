package com.example.tictactoe

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tictactoe.network.LeaderboardPlayer

class LeaderboardAdapter(private var players: List<LeaderboardPlayer>) :
    RecyclerView.Adapter<LeaderboardAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRank: TextView = view.findViewById(R.id.tvRank)
        val tvPlayerName: TextView = view.findViewById(R.id.tvPlayerName)
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
        
        holder.tvRank.text = player.rank.toString()
        holder.tvPlayerName.text = player.username
        holder.tvLevel.text = "Lvl ${player.level}"
        holder.tvXpWins.text = "${player.xp} XP • ${player.wins} Wins"

        // Gold, Silver, Bronze for top 3
        when (player.rank) {
            1 -> holder.tvRank.setTextColor(Color.parseColor("#FBBF24")) // Gold
            2 -> holder.tvRank.setTextColor(Color.parseColor("#94A3B8")) // Silver
            3 -> holder.tvRank.setTextColor(Color.parseColor("#B45309")) // Bronze
            else -> holder.tvRank.setTextColor(Color.WHITE)
        }
    }

    override fun getItemCount() = players.size

    fun updateData(newPlayers: List<LeaderboardPlayer>) {
        players = newPlayers
        notifyDataSetChanged()
    }
}
