# 🎮 Mini Game Arena (TicTacToe PRO & Beyond)

Android platformasi uchun yaratilgan ko'p o'yinli intellektual o'yinlar platformasi (15+ o'yin bitta ilovada).

---

## 🌟 O'yinlar Ro'yxati

1. **👑 Shaxmat (Chess PRO)** — To'liq xalqaro shaxmat qoidalari, minimax AI va real-time multiplayer.
2. **🔴 Shashka (Checkers)** — Rus/Xalqaro shashka, majburiy urish, damkaga chiqish, AI bot.
3. **🃏 Durak (Durdona karta o'yini)** — 36 kartali klassik o'yin, sudrab tashlash (Drag-to-Play), qo'lda kartalar o'rnini almashtirish, o'ng tomondan qo'shilish va AI bot.
4. **⭕ Tic Tac Toe (X/O)** — 3x3 klassik va qiyin bot (Minimax).
5. **⚪ Gomoku (5 in a row)** — 15x15 taxtada qatorasiga 5 ta tosh yig'ish, AI va onlayn rejim.
6. **🟡 Connect 4** — 7x6 vertikal doskada 4 ta ketma-ket tosh tushirish.
7. **📦 Dots & Boxes** — Nuqtalarni birlashtirib katakchalarni egallash.
8. **🔢 2048** — Klassik raqamlar boshqotirmasi.
9. **🧪 Water Sort Puzzle** — Rangli suyuqliklarni probirkalarga ajratish (50 daraja).
10. **🧠 Memory Game** — Kartalarni ochib juftini topish.
11. **⚡ Math Challenge** — Tezkor arifmetika va vaqt poygasi.
12. **🎨 Color Match** — Stroop effekti asosidagi ranglar testi.
13. **💣 Drop Number** — Tushayotgan raqamli bloklarni birlashtirish.
14. **♾️ Infinity Tic Tac Toe** — Doskada faqat 3 ta belgi saqlanadigan cheksiz rejim.

---

## 🏗 Texnologiyalar va Arxitektura

- **Til:** Kotlin 1.9+
- **Min SDK:** 24 (Android 7.0+)
- **Target SDK:** 34 (Android 14)
- **UI:** ViewBinding, Custom Canvas Views (DurakCardView, CheckersBoardView, ChessBoardView, GomokuBoardView, WaterTubeView, LuckyWheelView)
- **Navigatsiya:** Jetpack Navigation Component
- **Tarmoq:** 
  - REST API: Retrofit 2 + Gson Converter + OkHttp 4
  - Real-time WebSockets: Pusher Channels Client (eu cluster)
- **Asinxron AI:** AiThinker (Background Thread Minimax/Alpha-Beta search)
- **Audio & Haptic:** SoundHelper, HapticHelper
- **Havfsizlik:** SafeClickListener (Throttle clicks), Lifecycle guards (isAdded / _binding check)

---

## 🛠 O'rnatish va Yig'ish (Build & Run)

### Talablar:
- Android Studio Hedgehog yoki undan yangisi
- JDK 17+
- Android SDK 34

### Terminal orqali yig'ish:
`powershell
cd android-app
.\gradlew.bat assembleDebug --no-daemon
`

Yig'ilgan APK fayl manzili:
ndroid-app/app/build/outputs/apk/debug/app-debug.apk yoki loyiha ildizidagi TicTacToe.apk.

---

## 📡 Tarmoq va API Arxitekturasi

- **Base REST URL:** https://new.elegant-house.uz/public/api/
- **Hujjatlar:** api_documentation.md
