<?php

namespace App\Http\Controllers;

use App\Models\User;
use App\Models\Wallet;
use App\Models\EconomySetting;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Validator;

class UserController extends Controller
{
    /**
     * Authenticate or register device, issue Sanctum Bearer Token, ensure wallet exists.
     * POST /api/users/auth
     */
    public function auth(Request $request)
    {
        $validator = Validator::make($request->all(), [
            'device_id' => 'required|string|max:100',
            'username' => 'nullable|string|max:50',
        ]);

        if ($validator->fails()) {
            return response()->json([
                'status' => 'error',
                'message' => $validator->errors()->first(),
            ], 422);
        }

        $deviceId = $request->input('device_id');
        $username = $request->input('username');

        // Find or create user by device_id
        $user = User::where('device_id', $deviceId)->first();

        if (!$user) {
            $initialUsername = $username ?: 'Player_' . substr(md5($deviceId . time()), 0, 6);
            $user = User::create([
                'device_id' => $deviceId,
                'username' => $initialUsername,
                'level' => 1,
                'xp' => 0,
                'wins' => 0,
                'losses' => 0,
                'draws' => 0,
            ]);
        } elseif (!empty($username) && $user->username !== $username) {
            $user->username = $username;
            $user->save();
        }

        // Ensure wallet exists
        $initialBalance = EconomySetting::getInt('INITIAL_WALLET_BALANCE', 100);
        $wallet = Wallet::firstOrCreate(
            ['user_id' => $user->id],
            ['balance' => $initialBalance, 'version' => 1]
        );

        // Revoke old tokens if necessary & create fresh Sanctum token
        if (method_exists($user, 'tokens')) {
            $user->tokens()->where('name', 'android-app')->delete();
            $token = $user->createToken('android-app')->plainTextToken;
        } else {
            // Fallback if Sanctum trait not loaded on User model
            $token = base64_encode($user->id . ':' . hash('sha256', $user->device_id . env('APP_KEY')));
        }

        return response()->json([
            'status' => 'success',
            'token' => $token,
            'balance' => $wallet->balance,
            'user' => [
                'id' => (int) $user->id,
                'username' => $user->username,
                'level' => (int) ($user->level ?: 1),
                'xp' => (int) ($user->xp ?: 0),
                'wins' => (int) ($user->wins ?: 0),
                'losses' => (int) ($user->losses ?: 0),
                'draws' => (int) ($user->draws ?: 0),
                'coins' => (int) $wallet->balance,
                'streak_count' => (int) ($user->streak_count ?: 0),
                'unlocked_games' => $user->unlocked_games ?? [],
            ],
        ]);
    }
}
