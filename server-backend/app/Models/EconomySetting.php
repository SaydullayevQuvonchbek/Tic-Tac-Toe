<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;

class EconomySetting extends Model
{
    use HasFactory;

    protected $table = 'economy_settings';

    public $timestamps = false;

    protected $fillable = [
        'setting_key',
        'setting_value',
        'description',
        'updated_at',
    ];

    public static function get(string $key, $default = null)
    {
        $setting = static::where('setting_key', $key)->first();
        return $setting ? $setting->setting_value : $default;
    }

    public static function getInt(string $key, int $default = 0): int
    {
        $val = static::get($key, $default);
        return (int) $val;
    }
}
