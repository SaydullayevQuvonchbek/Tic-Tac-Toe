<?php

use Illuminate\Support\Facades\Route;
use App\Http\Controllers\UserController;
use App\Http\Controllers\EconomyController;

/*
|--------------------------------------------------------------------------
| API Routes — Tic Tac Toe Arena
|--------------------------------------------------------------------------
| Nusxalang va serverdagi routes/api.php fayliga joylashtiring.
*/

// ==========================================
// 1. PUBLIC ROUTES (Token talab qilinmaydi)
// ==========================================
Route::post('users/auth', [UserController::class, 'auth']);

// ==========================================
// 2. PROTECTED ROUTES (Sanctum Bearer Token)
// ==========================================
Route::middleware('auth:sanctum')->group(function () {

    // 2.1. Economy Profile & Transactions History
    Route::get('economy/profile', [EconomyController::class, 'profile']);
    Route::get('economy/history', [EconomyController::class, 'history']);

    // 2.2. Matches (Online match mukofotini tasdiqlash)
    Route::post('matches/{matchId}/claim', [EconomyController::class, 'claimMatch']);

    // 2.3. Game Sessions (Offline / Solo / Bot sessiyalar)
    Route::post('game-sessions/start', [EconomyController::class, 'startSession']);
    Route::post('game-sessions/{sessionId}/finish', [EconomyController::class, 'finishSession']);

    // 2.4. Daily Quests
    Route::get('quests/today', [EconomyController::class, 'todayQuests']);
    Route::post('quests/{questId}/claim', [EconomyController::class, 'claimQuest']);

    // 2.5. Lucky Wheel (Omad g'ildiragi)
    Route::post('wheel/spin', [EconomyController::class, 'spinWheel']);

    // 2.6. Store (Do'kon narxlari va xarid qilish)
    Route::get('store/items', [EconomyController::class, 'storeItems']);
    Route::post('store/items/{itemKey}/buy', [EconomyController::class, 'buyStoreItem']);

    // 2.7. Inventory (Foydalanuvchi buyumlari va kiyish)
    Route::get('inventory', [EconomyController::class, 'inventory']);
    Route::post('inventory/{itemKey}/equip', [EconomyController::class, 'equipItem']);
});
