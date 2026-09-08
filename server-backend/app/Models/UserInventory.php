<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

class UserInventory extends Model
{
    use HasFactory;

    protected $table = 'user_inventory';

    public $timestamps = false;

    protected $fillable = [
        'user_id',
        'item_id',
        'is_equipped',
        'purchased_at',
    ];

    protected $casts = [
        'user_id' => 'integer',
        'item_id' => 'integer',
        'is_equipped' => 'boolean',
        'purchased_at' => 'datetime',
    ];

    public function user()
    {
        return $this->belongsTo(User::class, 'user_id');
    }

    public function item()
    {
        return $this->belongsTo(StoreItem::class, 'item_id');
    }
}
