<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

class GameMatch extends Model
{
    use HasFactory;

    protected $table = 'game_matches';

    protected $fillable = [
        'room_code',
        'game_type',
        'player1_id',
        'player2_id',
        'winner_id',
        'status',
        'moves_count',
        'p1_claimed',
        'p2_claimed',
        'started_at',
        'finished_at',
    ];

    protected $casts = [
        'player1_id' => 'integer',
        'player2_id' => 'integer',
        'winner_id' => 'integer',
        'moves_count' => 'integer',
        'p1_claimed' => 'boolean',
        'p2_claimed' => 'boolean',
        'started_at' => 'datetime',
        'finished_at' => 'datetime',
    ];

    public function player1()
    {
        return $this->belongsTo(User::class, 'player1_id');
    }

    public function player2()
    {
        return $this->belongsTo(User::class, 'player2_id');
    }

    public function winner()
    {
        return $this->belongsTo(User::class, 'winner_id');
    }

    public function resultFor(User $user): string
    {
        if ($this->winner_id === null) {
            return 'DRAW';
        }
        return ($this->winner_id === $user->id) ? 'WIN' : 'LOSS';
    }
}
