package com.example.tictactoe

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.tictactoe.databinding.FragmentLeaderboardBinding
import com.example.tictactoe.network.ApiClient

class LeaderboardFragment : Fragment() {

    private var _binding: FragmentLeaderboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLeaderboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fetchAndShowLeaderboard()
    }

    private fun fetchAndShowLeaderboard() {
        val pd = android.app.ProgressDialog(context)
        pd.setMessage("Loading Leaderboard...")
        pd.show()

        ApiClient.instance.getLeaderboard().enqueue(object : retrofit2.Callback<com.example.tictactoe.network.LeaderboardResponse> {
            override fun onResponse(call: retrofit2.Call<com.example.tictactoe.network.LeaderboardResponse>, response: retrofit2.Response<com.example.tictactoe.network.LeaderboardResponse>) {
                pd.dismiss()
                if (response.isSuccessful && response.body()?.status == "success") {
                    val list = response.body()?.leaderboard
                    if (list.isNullOrEmpty()) {
                        Toast.makeText(context, "No players yet!", Toast.LENGTH_SHORT).show()
                    } else {
                        // For simplicity in Phase 1 without a custom adapter, we just show in an Alert or simple text view.
                        // Wait, I put a RecyclerView in the XML. Let's just use a simple ArrayAdapter and ListView for now 
                        // or just show an alert since I don't have an adapter class yet.
                        val names = list.map { "${it.rank}. ${it.username} (Level ${it.level} - ${it.xp} XP)" }.toTypedArray()
                        AlertDialog.Builder(requireContext())
                            .setTitle("Global Leaderboard")
                            .setItems(names, null)
                            .setPositiveButton("Close", null)
                            .show()
                    }
                } else {
                    Toast.makeText(context, "Failed to load", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: retrofit2.Call<com.example.tictactoe.network.LeaderboardResponse>, t: Throwable) {
                pd.dismiss()
                Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
