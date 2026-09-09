# 🚀 Server Backend O'rnatish va Joylashtirish Qo'llanmasi

Ushbu papkada server-authoritative (xavfsiz va server boshqaruvidagi) tanga va o'yin iqtisodiyoti tizimining barcha tayyor server fayllari mavjud.

---

## 1. Ma'lumotlar Bazasini Yangilash (SQL)
1. phpMyAdmin yoki server MySQL boshqaruv paneliga kiring.
2. Bazangizni tanlang va **SQL** bo'limini oching.
3. `server-backend/database_updates.sql` fayli ichidagi barcha kodlarni to'liq nusxalab, SQL oynasiga tashlang va **Go (Vipolnit)** tugmasini bosing.
4. Bu quyidagi 10 ta jadvalni yaratadi va dastlabki sozlamalarni kiritadi:
   - `wallets`
   - `coin_transactions`
   - `store_items`
   - `user_inventory`
   - `game_matches`
   - `game_sessions`
   - `daily_quests`
   - `user_quest_progress`
   - `wheel_spins`
   - `economy_settings`

---

## 2. Laravel Fayllarini Serverga Ko'chirish
Quyidagi fayllarni serveringizdagi Laravel loyihangizning mos papkalariga joylashtiring (almashtiring):

- `server-backend/app/Models/Wallet.php` ➡️ `app/Models/Wallet.php`
- `server-backend/app/Models/CoinTransaction.php` ➡️ `app/Models/CoinTransaction.php`
- `server-backend/app/Models/StoreItem.php` ➡️ `app/Models/StoreItem.php`
- `server-backend/app/Models/UserInventory.php` ➡️ `app/Models/UserInventory.php`
- `server-backend/app/Models/GameSession.php` ➡️ `app/Models/GameSession.php`
- `server-backend/app/Models/GameMatch.php` ➡️ `app/Models/GameMatch.php`
- `server-backend/app/Models/DailyQuest.php` ➡️ `app/Models/DailyQuest.php`
- `server-backend/app/Models/UserQuestProgress.php` ➡️ `app/Models/UserQuestProgress.php`
- `server-backend/app/Models/WheelSpin.php` ➡️ `app/Models/WheelSpin.php`
- `server-backend/app/Models/EconomySetting.php` ➡️ `app/Models/EconomySetting.php`
- `server-backend/app/Http/Controllers/UserController.php` ➡️ `app/Http/Controllers/UserController.php`
- `server-backend/app/Http/Controllers/EconomyController.php` ➡️ `app/Http/Controllers/EconomyController.php`
- `server-backend/routes/api.php` ➡️ `routes/api.php` dagi mavjud marshrutlar saqlanib, yangi marshrutlar qo'shiladi.

---

## 3. Server Keshini Tozalash
Serveringiz SSH terminalida (yoki hosting panelidagi Terminalda) quyidagi buyruqni bering:
```bash
php artisan optimize:clear
```
Yoki:
```bash
composer dump-autoload
php artisan route:clear
php artisan config:clear
```

---

## 4. Charxpalak va 401 (Unauthenticated) Xatoligini Bartaraf Qilish
Agar ilovada charxpalak aylantirishda yoki boshqa so'rovlarda `401 Unauthorized` xatosi chiqsa:

1. **phpMyAdmin da `personal_access_tokens` jadvali borligini tekshiring:**
   - Agar bo'lmasa, `database_updates.sql` dagi 11-jadval (`personal_access_tokens`) SQL ini ishga tushiring.
2. **`app/Models/User.php` modelida Sanctum ulangan bo'lishi kerak:**
   ```php
   use Laravel\Sanctum\HasApiTokens;
   
   class User extends Authenticatable {
       use HasApiTokens, HasFactory, Notifiable;
       ...
   }
   ```
3. **Yoki Omad Charxpalagini (Lucky Wheel) token talab qilmaydigan qilish:**
   - Serverdagi `routes/api.php` da quyidagi qatorni:
     ```php
     Route::post('wheel/spin', [EconomyController::class, 'spinWheel']);
     ```
     `Route::middleware('auth:sanctum')->group(...)` ichidan chiqarib, **1. PUBLIC ROUTES** bo'limiga qo'ying. Shunda har qanday o'yinchi tokenni kutmasdan to'g'ridan-to'g'ri serverdan sovg'a ola oladi.

---

Tabriklaymiz! Serveringiz endi to'liq xavfsiz va yangi Android ilovasi so'rovlarini qabul qilishga tayyor. 🚀
