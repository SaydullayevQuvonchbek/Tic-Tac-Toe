package com.example.tictactoe.repository

import android.content.Context
import com.example.tictactoe.TicTacToeApp
import com.example.tictactoe.network.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.UUID

object EconomyRepository {

    private const val PREFS_NAME = "TicTacToePrefs"
    private const val KEY_BALANCE = "coins"
    private const val KEY_XP = "xp"
    private const val KEY_LEVEL = "level"

    private fun prefs() = TicTacToeApp.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCachedBalance(): Int {
        return prefs().getInt(KEY_BALANCE, 100)
    }

    fun saveBalance(balance: Int) {
        prefs().edit().putInt(KEY_BALANCE, balance).apply()
    }

    fun refreshProfile(onComplete: ((success: Boolean, balance: Int, xp: Int, level: Int) -> Unit)? = null) {
        ApiClient.instance.getEconomyProfile().enqueue(object : Callback<EconomyProfileDto> {
            override fun onResponse(call: Call<EconomyProfileDto>, response: Response<EconomyProfileDto>) {
                if (response.isSuccessful && response.body() != null) {
                    val p = response.body()!!
                    saveBalance(p.balance)
                    prefs().edit()
                        .putInt(KEY_XP, p.xp)
                        .putInt(KEY_LEVEL, p.level)
                        .apply()
                    onComplete?.invoke(true, p.balance, p.xp, p.level)
                } else {
                    onComplete?.invoke(false, getCachedBalance(), prefs().getInt(KEY_XP, 0), prefs().getInt(KEY_LEVEL, 1))
                }
            }

            override fun onFailure(call: Call<EconomyProfileDto>, t: Throwable) {
                onComplete?.invoke(false, getCachedBalance(), prefs().getInt(KEY_XP, 0), prefs().getInt(KEY_LEVEL, 1))
            }
        })
    }

    fun claimMatch(
        matchId: String,
        onComplete: (success: Boolean, coinsEarned: Int, xpEarned: Int, newBalance: Int, message: String?) -> Unit
    ) {
        ApiClient.instance.claimMatchReward(matchId).enqueue(object : Callback<MatchClaimResponseDto> {
            override fun onResponse(call: Call<MatchClaimResponseDto>, response: Response<MatchClaimResponseDto>) {
                val body = response.body()
                if (response.isSuccessful && body != null && body.success) {
                    saveBalance(body.balance)
                    if (body.newTotalXp != null) {
                        prefs().edit().putInt(KEY_XP, body.newTotalXp).apply()
                    }
                    if (body.currentLevel != null) {
                        prefs().edit().putInt(KEY_LEVEL, body.currentLevel).apply()
                    }
                    onComplete(true, body.reward, body.xp, body.balance, null)
                } else {
                    val err = body?.message ?: "Server xatosi: ${response.code()}"
                    onComplete(false, 0, 0, getCachedBalance(), err)
                }
            }

            override fun onFailure(call: Call<MatchClaimResponseDto>, t: Throwable) {
                onComplete(false, 0, 0, getCachedBalance(), t.localizedMessage)
            }
        })
    }

    fun startSession(
        gameType: String,
        onComplete: (sessionId: String?, seed: String?) -> Unit
    ) {
        ApiClient.instance.startSession(GameSessionStartRequest(gameType)).enqueue(object : Callback<GameSessionStartResponse> {
            override fun onResponse(call: Call<GameSessionStartResponse>, response: Response<GameSessionStartResponse>) {
                val body = response.body()
                if (response.isSuccessful && body != null && body.success) {
                    onComplete(body.sessionId, body.seed)
                } else {
                    onComplete(null, null)
                }
            }

            override fun onFailure(call: Call<GameSessionStartResponse>, t: Throwable) {
                onComplete(null, null)
            }
        })
    }

    fun finishSession(
        sessionId: String,
        score: Int,
        level: Int,
        moves: Int,
        durationSeconds: Int,
        isWin: Boolean,
        onComplete: (success: Boolean, coinsEarned: Int, xpEarned: Int, newBalance: Int) -> Unit
    ) {
        val req = GameSessionFinishRequest(score, level, moves, durationSeconds, isWin)
        ApiClient.instance.finishSession(sessionId, req).enqueue(object : Callback<GameSessionFinishResponse> {
            override fun onResponse(call: Call<GameSessionFinishResponse>, response: Response<GameSessionFinishResponse>) {
                val body = response.body()
                if (response.isSuccessful && body != null && body.success) {
                    saveBalance(body.balance)
                    val curXp = prefs().getInt(KEY_XP, 0) + body.xp
                    prefs().edit().putInt(KEY_XP, curXp).apply()
                    onComplete(true, body.reward, body.xp, body.balance)
                } else {
                    onComplete(false, 0, 0, getCachedBalance())
                }
            }

            override fun onFailure(call: Call<GameSessionFinishResponse>, t: Throwable) {
                onComplete(false, 0, 0, getCachedBalance())
            }
        })
    }

    fun getTodayQuests(onComplete: (List<QuestDto>) -> Unit) {
        ApiClient.instance.getTodayQuests().enqueue(object : Callback<QuestListResponse> {
            override fun onResponse(call: Call<QuestListResponse>, response: Response<QuestListResponse>) {
                val body = response.body()
                if (response.isSuccessful && body != null && body.success) {
                    onComplete(body.quests)
                } else {
                    onComplete(emptyList())
                }
            }

            override fun onFailure(call: Call<QuestListResponse>, t: Throwable) {
                onComplete(emptyList())
            }
        })
    }

    fun claimQuest(
        questId: Long,
        onComplete: (success: Boolean, coins: Int, xp: Int, newBalance: Int, message: String?) -> Unit
    ) {
        ApiClient.instance.claimQuestReward(questId).enqueue(object : Callback<QuestClaimResponse> {
            override fun onResponse(call: Call<QuestClaimResponse>, response: Response<QuestClaimResponse>) {
                val body = response.body()
                if (response.isSuccessful && body != null && body.success) {
                    saveBalance(body.balance)
                    val curXp = prefs().getInt(KEY_XP, 0) + body.xp
                    prefs().edit().putInt(KEY_XP, curXp).apply()
                    onComplete(true, body.reward, body.xp, body.balance, null)
                } else {
                    onComplete(false, 0, 0, getCachedBalance(), body?.message)
                }
            }

            override fun onFailure(call: Call<QuestClaimResponse>, t: Throwable) {
                onComplete(false, 0, 0, getCachedBalance(), t.localizedMessage)
            }
        })
    }

    fun spinWheel(
        onComplete: (success: Boolean, segment: String?, label: String?, coins: Int, xp: Int, newBalance: Int, nextSpinAt: String?, message: String?) -> Unit
    ) {
        ApiClient.instance.spinWheel().enqueue(object : Callback<SpinResponseDto> {
            override fun onResponse(call: Call<SpinResponseDto>, response: Response<SpinResponseDto>) {
                val body = response.body()
                if (response.isSuccessful && body != null && body.success) {
                    saveBalance(body.balance)
                    val curXp = prefs().getInt(KEY_XP, 0) + body.xp
                    prefs().edit().putInt(KEY_XP, curXp).apply()
                    onComplete(true, body.segment, body.label, body.reward, body.xp, body.balance, body.nextSpinAt, null)
                } else {
                    val msg = body?.message ?: "Server xatosi: ${response.code()}"
                    onComplete(false, null, null, 0, 0, getCachedBalance(), body?.nextSpinAt, msg)
                }
            }

            override fun onFailure(call: Call<SpinResponseDto>, t: Throwable) {
                onComplete(false, null, null, 0, 0, getCachedBalance(), null, t.localizedMessage)
            }
        })
    }

    fun getStoreItems(onComplete: (List<StoreItemDto>) -> Unit) {
        ApiClient.instance.getStoreItems().enqueue(object : Callback<StoreItemListResponse> {
            override fun onResponse(call: Call<StoreItemListResponse>, response: Response<StoreItemListResponse>) {
                val body = response.body()
                if (response.isSuccessful && body != null && body.success) {
                    onComplete(body.items)
                } else {
                    onComplete(emptyList())
                }
            }

            override fun onFailure(call: Call<StoreItemListResponse>, t: Throwable) {
                onComplete(emptyList())
            }
        })
    }

    fun buyStoreItem(
        itemKey: String,
        onComplete: (success: Boolean, newBalance: Int, message: String?) -> Unit
    ) {
        val requestId = UUID.randomUUID().toString()
        ApiClient.instance.buyStoreItem(itemKey, requestId).enqueue(object : Callback<StoreBuyResponseDto> {
            override fun onResponse(call: Call<StoreBuyResponseDto>, response: Response<StoreBuyResponseDto>) {
                val body = response.body()
                if (response.isSuccessful && body != null && body.success) {
                    saveBalance(body.balance)
                    prefs().edit().putBoolean("unlocked_$itemKey", true).apply()
                    onComplete(true, body.balance, body.message)
                } else {
                    val msg = body?.message ?: "Xarid amalga oshmadi (${response.code()})"
                    onComplete(false, getCachedBalance(), msg)
                }
            }

            override fun onFailure(call: Call<StoreBuyResponseDto>, t: Throwable) {
                onComplete(false, getCachedBalance(), t.localizedMessage)
            }
        })
    }

    fun equipItem(
        itemKey: String,
        onComplete: (success: Boolean, message: String?) -> Unit
    ) {
        ApiClient.instance.equipItem(itemKey).enqueue(object : Callback<EquipResponseDto> {
            override fun onResponse(call: Call<EquipResponseDto>, response: Response<EquipResponseDto>) {
                val body = response.body()
                if (response.isSuccessful && body != null && body.success) {
                    onComplete(true, body.message)
                } else {
                    onComplete(false, body?.message)
                }
            }

            override fun onFailure(call: Call<EquipResponseDto>, t: Throwable) {
                onComplete(false, t.localizedMessage)
            }
        })
    }
}
