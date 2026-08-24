package com.example.tictactoe

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

        adapter = LeaderboardAdapter(emptyList())
        binding.rvLeaderboard.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        binding.rvLeaderboard.adapter = adapter

        fetchLeaderboard()
    }

    private fun fetchLeaderboard() {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvLeaderboard.visibility = View.GONE

        ApiClient.instance.getLeaderboard().enqueue(object : Callback<LeaderboardResponse> {
            override fun onResponse(call: Call<LeaderboardResponse>, response: Response<LeaderboardResponse>) {
                binding.progressBar.visibility = View.GONE
                if (response.isSuccessful && response.body()?.status == "success") {
                    val list = response.body()?.leaderboard ?: emptyList()
                    adapter.updateData(list)
                    binding.rvLeaderboard.visibility = View.VISIBLE
                } else {
                    Toast.makeText(context, "Failed to load leaderboard", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<LeaderboardResponse>, t: Throwable) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(context, "Network Error", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
