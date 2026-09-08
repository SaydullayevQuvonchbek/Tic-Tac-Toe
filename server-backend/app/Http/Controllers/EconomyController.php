<?php

namespace App\Http\Controllers;

use App\Models\Wallet;
use App\Models\CoinTransaction;
use App\Models\StoreItem;
use App\Models\UserInventory;
use App\Models\GameMatch;
use App\Models\GameSession;
use App\Models\DailyQuest;
use App\Models\UserQuestProgress;
use App\Models\WheelSpin;
use App\Models\EconomySetting;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Carbon\Carbon;

class EconomyController extends Controller
{
    /**
     * 1. GET /api/economy/profile
     */
    public function profile(Request $request)
    {
        $user = $request->user();
        $wallet = Wallet::firstOrCreate(
            ['user_id' => $user->id],
            ['balance' => EconomySetting::getInt('INITIAL_WALLET_BALANCE', 100), 'version' => 1]
        );

        $today = Carbon::today()->toDateString();
        $todayEarnedCoins = CoinTransaction::where('user_id', $user->id)
            ->whereDate('created_at', $today)
            ->where('amount', '>', 0)
            ->sum('amount');

        $todaySoloCoins = CoinTransaction::where('user_id', $user->id)
            ->whereDate('created_at', $today)
            ->where('transaction_type', 'SESSION_REWARD')
            ->sum('amount');

        $lastSpin = WheelSpin::where('user_id', $user->id)->latest('created_at')->first();
        $cooldownHours = EconomySetting::getInt('WHEEL_COOLDOWN_HOURS', 24);
        $nextSpinAt = null;
        $isWheelAvailable = true;

        if ($lastSpin) {
            $unlockTime = Carbon::parse($lastSpin->created_at)->addHours($cooldownHours);
            if ($unlockTime->isFuture()) {
                $nextSpinAt = $unlockTime->toIso8601String();
                $isWheelAvailable = false;
            }
        }

        return response()->json([
            'success' => true,
            'balance' => (int) $wallet->balance,
            'xp' => (int) ($user->xp ?: 0),
            'level' => (int) ($user->level ?: 1),
            'todayEarnedCoins' => (int) $todayEarnedCoins,
            'todaySoloCoins' => (int) $todaySoloCoins,
            'soloDailyLimit' => EconomySetting::getInt('SOLO_DAILY_LIMIT', 150),
            'isWheelAvailable' => $isWheelAvailable,
            'nextSpinAt' => $nextSpinAt,
        ]);
    }

    /**
     * 2. GET /api/economy/history
     */
    public function history(Request $request)
    {
        $user = $request->user();
        $transactions = CoinTransaction::where('user_id', $user->id)
            ->orderBy('id', 'desc')
            ->limit(30)
            ->get();

        return response()->json([
            'success' => true,
            'transactions' => $transactions,
        ]);
    }

    /**
     * 3. POST /api/matches/{matchId}/claim
     */
    public function claimMatch(Request $request, $matchId)
    {
        $user = $request->user();

        // Find by ID or room_code
        $match = is_numeric($matchId)
            ? GameMatch::find($matchId)
            : GameMatch::where('room_code', $matchId)->first();

        if (!$match) {
            return response()->json(['success' => false, 'message' => 'Match topilmadi'], 404);
        }

        if ($match->player1_id != $user->id && $match->player2_id != $user->id) {
            return response()->json(['success' => false, 'message' => 'Siz ushbu match ishtirokchisi emassiz'], 403);
        }

        $isP1 = ($match->player1_id == $user->id);
        $alreadyClaimed = $isP1 ? $match->p1_claimed : $match->p2_claimed;

        if ($alreadyClaimed) {
            return response()->json(['success' => false, 'message' => 'Mukofot avval olingan'], 409);
        }

        return DB::transaction(function () use ($user, $match, $isP1) {
            $wallet = Wallet::where('user_id', $user->id)->lockForUpdate()->firstOrFail();
            $result = $match->resultFor($user);

            $rewardCoins = match ($result) {
                'WIN' => EconomySetting::getInt('ONLINE_WIN_REWARD', 50),
                'DRAW' => EconomySetting::getInt('ONLINE_DRAW_REWARD', 20),
                'LOSS' => EconomySetting::getInt('ONLINE_LOSS_REWARD', 10),
            };

            $rewardXp = match ($result) {
                'WIN' => EconomySetting::getInt('ONLINE_WIN_XP', 100),
                'DRAW' => EconomySetting::getInt('ONLINE_DRAW_XP', 40),
                'LOSS' => EconomySetting::getInt('ONLINE_LOSS_XP', 20),
            };

            $before = $wallet->balance;
            $wallet->balance += $rewardCoins;
            $wallet->version += 1;
            $wallet->save();

            CoinTransaction::create([
                'user_id' => $user->id,
                'amount' => $rewardCoins,
                'balance_before' => $before,
                'balance_after' => $wallet->balance,
                'transaction_type' => 'ONLINE_MATCH_' . $result,
                'reference_id' => 'match_' . $match->id,
                'description' => $match->game_type . ' Onlayn match: ' . $result,
                'created_at' => now(),
            ]);

            // Mark claimed
            if ($isP1) {
                $match->p1_claimed = true;
            } else {
                $match->p2_claimed = true;
            }
            $match->save();

            // Update user XP and match stats
            $user->xp = ($user->xp ?: 0) + $rewardXp;
            if ($result === 'WIN') $user->wins = ($user->wins ?: 0) + 1;
            elseif ($result === 'LOSS') $user->losses = ($user->losses ?: 0) + 1;
            elseif ($result === 'DRAW') $user->draws = ($user->draws ?: 0) + 1;
            $user->level = max(1, (int) floor($user->xp / 200) + 1);
            $user->save();

            // Progress matching quests
            $this->advanceQuests($user->id, $match->game_type, true, ($result === 'WIN'));

            return response()->json([
                'success' => true,
                'result' => $result,
                'reward' => $rewardCoins,
                'xp' => $rewardXp,
                'balance' => $wallet->balance,
                'newTotalXp' => $user->xp,
                'currentLevel' => $user->level,
            ]);
        });
    }

    /**
     * 4. POST /api/game-sessions/start
     */
    public function startSession(Request $request)
    {
        $user = $request->user();
        $gameType = $request->input('game', 'SOLO');

        $sessionId = 'gs_' . Str::random(16);
        $seed = Str::random(12);
        $expiresAt = Carbon::now()->addMinutes(30);

        GameSession::create([
            'session_id' => $sessionId,
            'user_id' => $user->id,
            'game_type' => $gameType,
            'seed' => $seed,
            'status' => 'ACTIVE',
            'expires_at' => $expiresAt,
        ]);

        return response()->json([
            'success' => true,
            'sessionId' => $sessionId,
            'game' => $gameType,
            'seed' => $seed,
            'expiresAt' => $expiresAt->toIso8601String(),
        ]);
    }

    /**
     * 5. POST /api/game-sessions/{sessionId}/finish
     */
    public function finishSession(Request $request, $sessionId)
    {
        $user = $request->user();
        $score = (int) $request->input('score', 0);
        $level = (int) $request->input('level', 1);
        $moves = (int) $request->input('moves', 0);
        $durationSeconds = (int) $request->input('durationSeconds', 0);
        $isWin = (bool) $request->input('isWin', true);

        $session = GameSession::where('session_id', $sessionId)
            ->where('user_id', $user->id)
            ->first();

        if (!$session) {
            return response()->json(['success' => false, 'message' => 'Sessiya topilmadi'], 404);
        }

        if ($session->status !== 'ACTIVE') {
            return response()->json(['success' => false, 'message' => 'Sessiya avval yakunlangan'], 409);
        }

        if ($session->expires_at && Carbon::parse($session->expires_at)->isPast()) {
            $session->status = 'EXPIRED';
            $session->save();
            return response()->json(['success' => false, 'message' => 'Sessiya vaqti tugagan'], 400);
        }

        return DB::transaction(function () use ($user, $session, $score, $level, $moves, $durationSeconds, $isWin) {
            $wallet = Wallet::where('user_id', $user->id)->lockForUpdate()->firstOrFail();

            // Daily Solo Limit Check
            $today = Carbon::today()->toDateString();
            $todaySoloEarned = CoinTransaction::where('user_id', $user->id)
                ->whereDate('created_at', $today)
                ->where('transaction_type', 'SESSION_REWARD')
                ->sum('amount');

            $limit = EconomySetting::getInt('SOLO_DAILY_LIMIT', 150);
            $remainingAllowed = max(0, $limit - $todaySoloEarned);

            $baseCoins = $isWin ? 25 : 5;
            $baseXp = $isWin ? 45 : 10;
            $grantedCoins = min($baseCoins, $remainingAllowed);

            $before = $wallet->balance;
            $wallet->balance += $grantedCoins;
            $wallet->version += 1;
            $wallet->save();

            if ($grantedCoins > 0) {
                CoinTransaction::create([
                    'user_id' => $user->id,
                    'amount' => $grantedCoins,
                    'balance_before' => $before,
                    'balance_after' => $wallet->balance,
                    'transaction_type' => 'SESSION_REWARD',
                    'reference_id' => $session->session_id,
                    'description' => $session->game_type . ' Solo o\'yin natijasi',
                    'created_at' => now(),
                ]);
            }

            $session->status = 'FINISHED';
            $session->score = $score;
            $session->level = $level;
            $session->moves = $moves;
            $session->duration_seconds = $durationSeconds;
            $session->reward_coins = $grantedCoins;
            $session->reward_xp = $baseXp;
            $session->finished_at = now();
            $session->save();

            $user->xp = ($user->xp ?: 0) + $baseXp;
            if ($isWin) $user->wins = ($user->wins ?: 0) + 1;
            $user->level = max(1, (int) floor($user->xp / 200) + 1);
            $user->save();

            // Advance quest progress
            $this->advanceQuests($user->id, $session->game_type, false, $isWin);

            return response()->json([
                'success' => true,
                'reward' => $grantedCoins,
                'xp' => $baseXp,
                'balance' => $wallet->balance,
                'dailyLimitReached' => ($remainingAllowed <= $grantedCoins),
            ]);
        });
    }

    /**
     * 6. GET /api/quests/today
     */
    public function todayQuests(Request $request)
    {
        $user = $request->user();
        $today = Carbon::today()->toDateString();

        $activeQuests = DailyQuest::where('is_active', true)->limit(3)->get();
        $response = [];

        foreach ($activeQuests as $q) {
            $progress = UserQuestProgress::firstOrCreate(
                ['user_id' => $user->id, 'quest_id' => $q->id, 'quest_date' => $today],
                ['current_progress' => 0, 'is_claimed' => false]
            );

            $response[] = [
                'id' => $q->id,
                'questKey' => $q->quest_key,
                'gameKey' => $q->game_key,
                'title' => $q->title,
                'target' => $q->target_count,
                'currentProgress' => $progress->current_progress,
                'coinReward' => $q->coin_reward,
                'xpReward' => $q->xp_reward,
                'isClaimed' => (bool) $progress->is_claimed,
                'isCompleted' => ($progress->current_progress >= $q->target_count),
            ];
        }

        return response()->json([
            'success' => true,
            'quests' => $response,
        ]);
    }

    /**
     * 7. POST /api/quests/{questId}/claim
     */
    public function claimQuest(Request $request, $questId)
    {
        $user = $request->user();
        $today = Carbon::today()->toDateString();

        $progress = UserQuestProgress::where('user_id', $user->id)
            ->where('quest_id', $questId)
            ->where('quest_date', $today)
            ->first();

        if (!$progress) {
            return response()->json(['success' => false, 'message' => 'Quest topilmadi'], 404);
        }

        $quest = DailyQuest::find($questId);
        if (!$quest || $progress->current_progress < $quest->target_count) {
            return response()->json(['success' => false, 'message' => 'Vazifa hali to\'liq bajarilmagan'], 400);
        }

        if ($progress->is_claimed) {
            return response()->json(['success' => false, 'message' => 'Mukofot allaqachon olingan'], 409);
        }

        return DB::transaction(function () use ($user, $quest, $progress, $today) {
            $wallet = Wallet::where('user_id', $user->id)->lockForUpdate()->firstOrFail();

            $before = $wallet->balance;
            $wallet->balance += $quest->coin_reward;
            $wallet->version += 1;
            $wallet->save();

            CoinTransaction::create([
                'user_id' => $user->id,
                'amount' => $quest->coin_reward,
                'balance_before' => $before,
                'balance_after' => $wallet->balance,
                'transaction_type' => 'QUEST_REWARD',
                'reference_id' => 'quest_' . $today . '_' . $quest->id,
                'description' => 'Kunlik vazifa: ' . $quest->title,
                'created_at' => now(),
            ]);

            $progress->is_claimed = true;
            $progress->claimed_at = now();
            $progress->save();

            $user->xp = ($user->xp ?: 0) + $quest->xp_reward;
            $user->level = max(1, (int) floor($user->xp / 200) + 1);
            $user->save();

            return response()->json([
                'success' => true,
                'reward' => $quest->coin_reward,
                'xp' => $quest->xp_reward,
                'balance' => $wallet->balance,
            ]);
        });
    }

    /**
     * 8. POST /api/wheel/spin
     */
    public function spinWheel(Request $request)
    {
        $user = $request->user();
        $cooldownHours = EconomySetting::getInt('WHEEL_COOLDOWN_HOURS', 24);

        $lastSpin = WheelSpin::where('user_id', $user->id)->latest('created_at')->first();
        if ($lastSpin) {
            $unlockTime = Carbon::parse($lastSpin->created_at)->addHours($cooldownHours);
            if ($unlockTime->isFuture()) {
                return response()->json([
                    'success' => false,
                    'message' => 'Sovg\'a olish vaqti kelmadi! Ertaga qaytib keling.',
                    'nextSpinAt' => $unlockTime->toIso8601String(),
                ], 429);
            }
        }

        // Weighted segments
        $segments = [
            ['key' => 'COIN_50', 'label' => '+50 🪙', 'coins' => 50, 'xp' => 0, 'weight' => 25],
            ['key' => 'XP_100', 'label' => '+100 ⚡', 'coins' => 0, 'xp' => 100, 'weight' => 20],
            ['key' => 'COIN_100', 'label' => '+100 🪙', 'coins' => 100, 'xp' => 0, 'weight' => 20],
            ['key' => 'XP_250', 'label' => '💎 +250 ⚡', 'coins' => 0, 'xp' => 250, 'weight' => 10],
            ['key' => 'MEGA_COIN_200', 'label' => '+200 🪙', 'coins' => 200, 'xp' => 0, 'weight' => 10],
            ['key' => 'PACKAGE_150', 'label' => '🎁 +150', 'coins' => 75, 'xp' => 75, 'weight' => 8],
            ['key' => 'JACKPOT_500', 'label' => '👑 +500 🪙', 'coins' => 500, 'xp' => 200, 'weight' => 2],
            ['key' => 'BONUS_XP_150', 'label' => '+150 ⚡', 'coins' => 0, 'xp' => 150, 'weight' => 5],
        ];

        $rand = rand(1, 100);
        $accum = 0;
        $chosen = $segments[0];
        foreach ($segments as $s) {
            $accum += $s['weight'];
            if ($rand <= $accum) {
                $chosen = $s;
                break;
            }
        }

        return DB::transaction(function () use ($user, $chosen, $cooldownHours) {
            $wallet = Wallet::where('user_id', $user->id)->lockForUpdate()->firstOrFail();

            $before = $wallet->balance;
            $wallet->balance += $chosen['coins'];
            $wallet->version += 1;
            $wallet->save();

            $today = Carbon::today()->toDateString();
            CoinTransaction::create([
                'user_id' => $user->id,
                'amount' => $chosen['coins'],
                'balance_before' => $before,
                'balance_after' => $wallet->balance,
                'transaction_type' => 'WHEEL_REWARD',
                'reference_id' => 'spin_' . $today . '_' . Str::random(6),
                'description' => 'Omad g\'ildiragi: ' . $chosen['label'],
                'created_at' => now(),
            ]);

            WheelSpin::create([
                'user_id' => $user->id,
                'spin_date' => $today,
                'segment_key' => $chosen['key'],
                'reward_coins' => $chosen['coins'],
                'reward_xp' => $chosen['xp'],
                'created_at' => now(),
            ]);

            if ($chosen['xp'] > 0) {
                $user->xp = ($user->xp ?: 0) + $chosen['xp'];
                $user->level = max(1, (int) floor($user->xp / 200) + 1);
                $user->save();
            }

            $nextSpin = Carbon::now()->addHours($cooldownHours)->toIso8601String();

            return response()->json([
                'success' => true,
                'segment' => $chosen['key'],
                'label' => $chosen['label'],
                'reward' => $chosen['coins'],
                'xp' => $chosen['xp'],
                'balance' => $wallet->balance,
                'nextSpinAt' => $nextSpin,
            ]);
        });
    }

    /**
     * 9. GET /api/store/items
     */
    public function storeItems(Request $request)
    {
        $user = $request->user();
        $items = StoreItem::where('is_active', true)->get();

        $ownedItemIds = UserInventory::where('user_id', $user->id)->pluck('is_equipped', 'item_id')->toArray();

        $result = $items->map(function ($item) use ($ownedItemIds) {
            $isOwned = isset($ownedItemIds[$item->id]) || ($item->price === 0);
            $isEquipped = isset($ownedItemIds[$item->id]) ? (bool) $ownedItemIds[$item->id] : ($item->price === 0);

            return [
                'id' => $item->id,
                'itemKey' => $item->item_key,
                'name' => $item->name,
                'category' => $item->category,
                'price' => (int) $item->price,
                'rarity' => $item->rarity,
                'description' => $item->description,
                'owned' => $isOwned,
                'equipped' => $isEquipped,
            ];
        });

        return response()->json([
            'success' => true,
            'items' => $result,
        ]);
    }

    /**
     * 10. POST /api/store/items/{itemKey}/buy
     */
    public function buyStoreItem(Request $request, $itemKey)
    {
        $user = $request->user();
        $requestId = $request->header('Idempotency-Key') ?: $request->input('requestId', Str::uuid()->toString());

        // Check if idempotency key was already executed
        $existingTx = CoinTransaction::where('user_id', $user->id)
            ->where('transaction_type', 'STORE_PURCHASE')
            ->where('reference_id', $requestId)
            ->first();

        if ($existingTx) {
            $wallet = Wallet::where('user_id', $user->id)->first();
            return response()->json([
                'success' => true,
                'message' => 'Xarid avval bajarilgan (idempotent)',
                'balance' => $wallet ? $wallet->balance : 0,
                'itemKey' => $itemKey,
            ]);
        }

        $item = StoreItem::where('item_key', $itemKey)->where('is_active', true)->first();
        if (!$item) {
            return response()->json(['success' => false, 'message' => 'Tovar topilmadi'], 404);
        }

        // Check if already owned
        $alreadyOwned = UserInventory::where('user_id', $user->id)->where('item_id', $item->id)->exists();
        if ($alreadyOwned) {
            return response()->json(['success' => false, 'message' => 'Bu tovar allaqachon sotib olingan'], 409);
        }

        return DB::transaction(function () use ($user, $item, $requestId) {
            $wallet = Wallet::where('user_id', $user->id)->lockForUpdate()->firstOrFail();

            if ($wallet->balance < $item->price) {
                return response()->json([
                    'success' => false,
                    'message' => "Mablag' yetarli emas! Sizda {$wallet->balance} 🪙 bor, narxi: {$item->price} 🪙.",
                    'balance' => $wallet->balance,
                ], 400);
            }

            $before = $wallet->balance;
            $wallet->balance -= $item->price;
            $wallet->version += 1;
            $wallet->save();

            UserInventory::create([
                'user_id' => $user->id,
                'item_id' => $item->id,
                'is_equipped' => true,
                'purchased_at' => now(),
            ]);

            CoinTransaction::create([
                'user_id' => $user->id,
                'amount' => -$item->price,
                'balance_before' => $before,
                'balance_after' => $wallet->balance,
                'transaction_type' => 'STORE_PURCHASE',
                'reference_id' => $requestId,
                'description' => 'Xarid: ' . $item->name,
                'created_at' => now(),
            ]);

            return response()->json([
                'success' => true,
                'message' => "{$item->name} muvaffaqiyatli sotib olindi!",
                'balance' => $wallet->balance,
                'itemKey' => $item->item_key,
            ]);
        });
    }

    /**
     * 11. GET /api/inventory
     */
    public function inventory(Request $request)
    {
        $user = $request->user();
        $inventory = UserInventory::with('item')
            ->where('user_id', $user->id)
            ->get();

        return response()->json([
            'success' => true,
            'inventory' => $inventory,
        ]);
    }

    /**
     * 12. POST /api/inventory/{itemKey}/equip
     */
    public function equipItem(Request $request, $itemKey)
    {
        $user = $request->user();
        $item = StoreItem::where('item_key', $itemKey)->first();

        if (!$item) {
            return response()->json(['success' => false, 'message' => 'Tovar topilmadi'], 404);
        }

        $inv = UserInventory::where('user_id', $user->id)->where('item_id', $item->id)->first();
        if (!$inv && $item->price > 0) {
            return response()->json(['success' => false, 'message' => 'Sizda bu buyum mavjud emas'], 403);
        }

        // Unequip others in same category
        $categoryItemIds = StoreItem::where('category', $item->category)->pluck('id');
        UserInventory::where('user_id', $user->id)
            ->whereIn('item_id', $categoryItemIds)
            ->update(['is_equipped' => false]);

        if ($inv) {
            $inv->is_equipped = true;
            $inv->save();
        }

        return response()->json([
            'success' => true,
            'message' => 'Muvaffaqiyatli o\'rnatildi',
            'equippedItem' => $itemKey,
        ]);
    }

    /**
     * Helper: advance matching daily quests for user
     */
    private function advanceQuests($userId, $gameKey, $isOnline, $isWin)
    {
        $today = Carbon::today()->toDateString();
        $quests = DailyQuest::where('is_active', true)->get();

        foreach ($quests as $q) {
            $matchGame = empty($q->game_key) || str_contains(strtolower($gameKey), strtolower($q->game_key));
            if (!$matchGame) continue;

            $progress = UserQuestProgress::firstOrCreate(
                ['user_id' => $userId, 'quest_id' => $q->id, 'quest_date' => $today],
                ['current_progress' => 0, 'is_claimed' => false]
            );

            if (!$progress->is_claimed && $progress->current_progress < $q->target_count) {
                $progress->current_progress += 1;
                $progress->save();
            }
        }
    }
}
