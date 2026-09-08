<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

class WheelSpin extends Model
{
    use HasFactory;

    protected $table = 'wheel_spins';

    public $timestamps = false;

    protected $fillable = [
        'user_id',
        'spin_date',
        'segment_key',
        'reward_coins',
        'reward_xp',
        'created_at',
    ];

    protected $casts = [
        'user_id' => 'integer',
        'reward_coins' => 'integer',
        'reward_xp' => 'integer',
        'spin_date' => 'date',
        'created_at' => 'datetime',
    ];

    public function user()
    {
        return $this->belongsTo(User::class, 'user_id');
    }
}
