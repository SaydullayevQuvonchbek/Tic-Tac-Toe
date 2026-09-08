package com.example.tictactoe.network

import com.google.gson.annotations.SerializedName

// 1. Economy Profile
data class EconomyProfileDto(
    @SerializedName("success") val success: Boolean,
    @SerializedName("balance") val balance: Int,
    @SerializedName("xp") val xp: Int,
    @SerializedName("level") val level: Int,
    @SerializedName("todayEarnedCoins") val todayEarnedCoins: Int,
    @SerializedName("todaySoloCoins") val todaySoloCoins: Int,
    @SerializedName("soloDailyLimit") val soloDailyLimit: Int,
    @SerializedName("isWheelAvailable") val isWheelAvailable: Boolean,
    @SerializedName("nextSpinAt") val nextSpinAt: String?
)

// 2. Transaction History
data class CoinTransactionDto(
    @SerializedName("id") val id: Long,
    @SerializedName("amount") val amount: Int,
    @SerializedName("balance_before") val balanceBefore: Int,
    @SerializedName("balance_after") val balanceAfter: Int,
    @SerializedName("transaction_type") val transactionType: String,
    @SerializedName("reference_id") val referenceId: String,
    @SerializedName("description") val description: String?,
    @SerializedName("created_at") val createdAt: String?
)

data class EconomyHistoryDto(
    @SerializedName("success") val success: Boolean,
    @SerializedName("transactions") val transactions: List<CoinTransactionDto>
)

// 3. Match Claim
data class MatchClaimResponseDto(
    @SerializedName("success") val success: Boolean,
    @SerializedName("result") val result: String?,
    @SerializedName("reward") val reward: Int,
    @SerializedName("xp") val xp: Int,
    @SerializedName("balance") val balance: Int,
    @SerializedName("newTotalXp") val newTotalXp: Int?,
    @SerializedName("currentLevel") val currentLevel: Int?,
    @SerializedName("message") val message: String?
)

// 4. Game Sessions
data class GameSessionStartRequest(
    @SerializedName("game") val game: String
)

data class GameSessionStartResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("sessionId") val sessionId: String,
    @SerializedName("game") val game: String,
    @SerializedName("seed") val seed: String,
    @SerializedName("expiresAt") val expiresAt: String
)

data class GameSessionFinishRequest(
    @SerializedName("score") val score: Int,
    @SerializedName("level") val level: Int,
    @SerializedName("moves") val moves: Int,
    @SerializedName("durationSeconds") val durationSeconds: Int,
    @SerializedName("isWin") val isWin: Boolean
)

data class GameSessionFinishResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("reward") val reward: Int,
    @SerializedName("xp") val xp: Int,
    @SerializedName("balance") val balance: Int,
    @SerializedName("dailyLimitReached") val dailyLimitReached: Boolean,
    @SerializedName("message") val message: String?
)

// 5. Daily Quests
data class QuestDto(
    @SerializedName("id") val id: Long,
    @SerializedName("questKey") val questKey: String,
    @SerializedName("gameKey") val gameKey: String,
    @SerializedName("title") val title: String,
    @SerializedName("target") val target: Int,
    @SerializedName("currentProgress") val currentProgress: Int,
    @SerializedName("coinReward") val coinReward: Int,
    @SerializedName("xpReward") val xpReward: Int,
    @SerializedName("isClaimed") val isClaimed: Boolean,
    @SerializedName("isCompleted") val isCompleted: Boolean
)

data class QuestListResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("quests") val quests: List<QuestDto>
)

data class QuestClaimResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("reward") val reward: Int,
    @SerializedName("xp") val xp: Int,
    @SerializedName("balance") val balance: Int,
    @SerializedName("message") val message: String?
)

// 6. Lucky Wheel Spin
data class SpinResponseDto(
    @SerializedName("success") val success: Boolean,
    @SerializedName("segment") val segment: String?,
    @SerializedName("label") val label: String?,
    @SerializedName("reward") val reward: Int,
    @SerializedName("xp") val xp: Int,
    @SerializedName("balance") val balance: Int,
    @SerializedName("nextSpinAt") val nextSpinAt: String?,
    @SerializedName("message") val message: String?
)

// 7. Store Items
data class StoreItemDto(
    @SerializedName("id") val id: Long,
    @SerializedName("itemKey") val itemKey: String,
    @SerializedName("name") val name: String,
    @SerializedName("category") val category: String,
    @SerializedName("price") val price: Int,
    @SerializedName("rarity") val rarity: String,
    @SerializedName("description") val description: String?,
    @SerializedName("owned") val owned: Boolean,
    @SerializedName("equipped") val equipped: Boolean
)

data class StoreItemListResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("items") val items: List<StoreItemDto>
)

data class StoreBuyResponseDto(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("balance") val balance: Int,
    @SerializedName("itemKey") val itemKey: String?
)

// 8. Inventory
data class InventoryItemDto(
    @SerializedName("id") val id: Long,
    @SerializedName("user_id") val userId: Long,
    @SerializedName("item_id") val itemId: Long,
    @SerializedName("is_equipped") val isEquipped: Boolean,
    @SerializedName("purchased_at") val purchasedAt: String?,
    @SerializedName("item") val item: StoreItemDto?
)

data class InventoryListResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("inventory") val inventory: List<InventoryItemDto>
)

data class EquipResponseDto(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("equippedItem") val equippedItem: String?
)
