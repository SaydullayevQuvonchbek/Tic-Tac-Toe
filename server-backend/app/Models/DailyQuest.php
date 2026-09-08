<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

class DailyQuest extends Model
{
    use HasFactory;

    protected $table = 'daily_quests';

    public $timestamps = false;

    protected $fillable = [
        'quest_key',
        'game_key',
        'title',
        'target_count',
        'coin_reward',
        'xp_reward',
        'is_active',
        'created_at',
    ];

    protected $casts = [
        'target_count' => 'integer',
        'coin_reward' => 'integer',
        'xp_reward' => 'integer',
        'is_active' => 'boolean',
    ];

    public function userProgress()
    {
        return $this->hasMany(UserQuestProgress::class, 'quest_id');
    }
}
