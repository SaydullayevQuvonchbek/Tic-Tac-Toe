<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

class GameSession extends Model
{
    use HasFactory;

    protected $table = 'game_sessions';

    protected $fillable = [
        'session_id',
        'user_id',
        'game_type',
        'seed',
        'status',
        'score',
        'level',
        'moves',
        'duration_seconds',
        'reward_coins',
        'reward_xp',
        'expires_at',
        'finished_at',
    ];

    protected $casts = [
        'user_id' => 'integer',
        'score' => 'integer',
        'level' => 'integer',
        'moves' => 'integer',
        'duration_seconds' => 'integer',
        'reward_coins' => 'integer',
        'reward_xp' => 'integer',
        'expires_at' => 'datetime',
        'finished_at' => 'datetime',
    ];

    public function user()
    {
        return $this->belongsTo(User::class, 'user_id');
    }
}
