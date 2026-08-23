# Tic Tac Toe Pro+++ API Qo'llanmasi

Bu hujjat orqali siz o'yinning frontend qismini (Unity, Flutter, React Native va h.k) to'g'ridan-to'g'ri backend bilan ishlashini sozlashingiz mumkin.

## Asosiy Ma'lumotlar
- **Base URL:** `https://new.elegant-house.uz/public/api`
- **Headers (Har bir HTTP so'rovda qo'shilishi shart):**
  - `Content-Type: application/json`
  - `Accept: application/json`

---

## 1. REST API Endpoints (HTTP so'rovlar)

### 1.1. Foydalanuvchi Kirishi / Ro'yxatdan o'tish
O'yinchi qurilmasi orqali kirganda ishlatiladi. Agar `device_id` bazada bo'lmasa yangi o'yinchi yaratiladi, bo'lsa o'sha o'yinchi ma'lumotlari qaytariladi.

- **URL:** `/users/auth`
- **Method:** `POST`
- **Body (JSON):**
```json
{
    "device_id": "a1b2c3d4e5f6",
    "username": "NinjaPlayer"
}
```
- **Response:**
```json
{
    "status": "success",
    "user": {
        "id": 1,
        "device_id": "a1b2c3d4e5f6",
        "username": "NinjaPlayer",
        "level": 1,
        "xp": 0,
        "wins": 0,
        "losses": 0,
        "draws": 0
    }
}
```

### 1.2. Global Reyting (Leaderboard)
Top 50 ta eng ko'p XP yig'gan o'yinchilarni qaytaradi.

- **URL:** `/leaderboard`
- **Method:** `GET`
- **Response:**
```json
{
    "status": "success",
    "leaderboard": [
        {
            "rank": 1,
            "username": "Shoh",
            "level": 10,
            "xp": 5000,
            "wins": 120
        }
    ]
}
```

### 1.3. O'yin Natijasi va XP berish
O'yin tugaganda, yutgan yoki yutqazganlik haqida jo'natiladi. Server avtomatik XP hisoblaydi (Win: +50, Draw: +10, Loss: -5).

- **URL:** `/match/result`
- **Method:** `POST`
- **Body (JSON):**
```json
{
    "player_id": 1,
    "result": "win" // Qabul qiladi: "win", "loss", "draw"
}
```
- **Response:**
```json
{
    "status": "success",
    "xp_earned": 50,
    "new_total_xp": 50,
    "level_up": false, // Agar daraja oshsa true qaytadi
    "current_level": 1
}
```

### 1.4. Yangi Xona (Room) Yaratish
Birinchi o'yinchi xona yaratganda ishlatiladi.

- **URL:** `/room/create`
- **Method:** `POST`
- **Body (JSON):**
```json
{
    "player_id": 1,
    "board_size": 3,
    "infinity_mode": false
}
```
- **Response:**
```json
{
    "status": "success",
    "room_code": "A7B9"
}
```

### 1.5. Xonaga Qo'shilish
Ikkinchi o'yinchi tayyor xonaga kirganda ishlatiladi.

- **URL:** `/room/join`
- **Method:** `POST`
- **Body (JSON):**
```json
{
    "player_id": 2,
    "room_code": "A7B9"
}
```
- **Response:**
```json
{
    "status": "success",
    "room_code": "A7B9"
}
```

### 1.6. Yurish Qilish (Move)
O'yinchi katakni bosganda ishlatiladi. Bu API ishga tushgach, orqadagi Pusher avtomatik hammaga websocket signal yuboradi.

- **URL:** `/room/move`
- **Method:** `POST`
- **Body (JSON):**
```json
{
    "room_code": "A7B9",
    "player_id": 1,
    "row": 0,
    "col": 2,
    "next_turn": 2 // Navbat qaysi player_id ga o'tganligi
}
```
- **Response:**
```json
{
    "status": "success"
}
```

### 1.7. Xonani Tark Etish
O'yinchi chiqib ketsa (yoki o'yinni yopsa) API chaqiriladi. Raqibga darhol signal yuboriladi.

- **URL:** `/room/leave`
- **Method:** `POST`
- **Body (JSON):**
```json
{
    "room_code": "A7B9",
    "player_id": 1
}
```
- **Response:**
```json
{
    "status": "success"
}
```

---

## 2. WebSocket Hodisalari (Pusher)

O'yinda haqiqiy vaqt rejimida (real-time) ma'lumot olish uchun frontend **Pusher Client** orqali serverga ulanishi kerak. 

- **Channel (Kanal) nomi:** Har bir o'yin uchun kanal nomi `game.{room_code}` bo'ladi (Masalan: `game.A7B9`).
- Siz kanalga ulanib, quyidagi eventlarni (hodisalarni) eshitib turishingiz (listen) kerak:

### 2.1. `game_started`
Ikkinchi o'yinchi xonaga ulanganda keladi. Shunda o'yin boshlanadi.

- **Kutib olinadigan ma'lumot (Payload):**
```json
{
    "roomCode": "A7B9",
    "opponent": {
        "username": "Player2",
        "level": 3
    },
    "starting_turn": 1 // Birinchi bo'lib yuradigan player_id
}
```

### 2.2. `move_made`
Kimdir yurish qilganda keladi. Raqib qurilmasida katakni yangilash uchun ishlatiladi.

- **Kutib olinadigan ma'lumot (Payload):**
```json
{
    "roomCode": "A7B9",
    "player_id": 1,
    "row": 0,
    "col": 2,
    "next_turn": 2
}
```

### 2.3. `opponent_disconnected`
Raqib `/room/leave` ni bossa yoki bazaviy uzilish qilsa keladi. Ekranga "Raqib chiqib ketdi, siz yutdingiz" degan yozuv chiqarish uchun.

- **Kutib olinadigan ma'lumot (Payload):**
```json
{
    "roomCode": "A7B9",
    "message": "Opponent left the match."
}
```
