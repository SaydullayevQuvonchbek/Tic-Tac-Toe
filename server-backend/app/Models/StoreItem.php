<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

class StoreItem extends Model
{
    use HasFactory;

    protected $table = 'store_items';

    protected $fillable = [
        'item_key',
        'name',
        'category',
        'price',
        'rarity',
        'description',
        'is_active',
    ];

    protected $casts = [
        'price' => 'integer',
        'is_active' => 'boolean',
    ];

    public function inventories()
    {
        return $this->hasMany(UserInventory::class, 'item_id');
    }
}
