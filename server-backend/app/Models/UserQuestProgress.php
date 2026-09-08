<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

class UserQuestProgress extends Model
{
    use HasFactory;

    protected $table = 'user_quest_progress';

    public $timestamps = false;

    protected $fillable = [
        'user_id',
        'quest_id',
        'quest_date',
        'current_progress',
        'is_claimed',
        'claimed_at',
    ];

    protected $casts = [
        'user_id' => 'integer',
        'quest_id' => 'integer',
        'current_progress' => 'integer',
        'is_claimed' => 'boolean',
        'quest_date' => 'date',
        'claimed_at' => 'datetime',
    ];

    public function user()
    {
        return $this->belongsTo(User::class, 'user_id');
    }

    public function quest()
    {
        return $this->belongsTo(DailyQuest::class, 'quest_id');
    }
}
