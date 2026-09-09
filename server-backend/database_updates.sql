-- ====================================================================
-- TIC TAC TOE ARENA — SERVER-AUTHORITATIVE ECONOMY & SECURITY SCHEMA
-- Ishga tushirish: phpMyAdmin SQL oynasiga nusxalang va "Go/Vipolnit" bosing.
-- ====================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- 1. WALLETS (Foydalanuvchi Balansi)
CREATE TABLE IF NOT EXISTS `wallets` (
    `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT UNSIGNED NOT NULL UNIQUE,
    `balance` INT UNSIGNED NOT NULL DEFAULT 100,
    `version` INT UNSIGNED NOT NULL DEFAULT 1,
    `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_wallets_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. COIN_TRANSACTIONS (Ledger / Kirim-Chiqim Moliya Jurnali)
CREATE TABLE IF NOT EXISTS `coin_transactions` (
    `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `amount` INT NOT NULL,
    `balance_before` INT UNSIGNED NOT NULL,
    `balance_after` INT UNSIGNED NOT NULL,
    `transaction_type` VARCHAR(50) NOT NULL,
    `reference_id` VARCHAR(100) NOT NULL,
    `description` VARCHAR(255) NULL,
    `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_tx_user_created` (`user_id`, `created_at`),
    UNIQUE KEY `unique_reward` (`user_id`, `transaction_type`, `reference_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. STORE_ITEMS (Do'kondagi Rasmiy Tovarlar va Narxlar)
CREATE TABLE IF NOT EXISTS `store_items` (
    `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `item_key` VARCHAR(100) NOT NULL UNIQUE,
    `name` VARCHAR(150) NOT NULL,
    `category` VARCHAR(50) NOT NULL,
    `price` INT UNSIGNED NOT NULL,
    `rarity` VARCHAR(30) NOT NULL DEFAULT 'COMMON',
    `description` VARCHAR(255) NULL,
    `is_active` BOOLEAN NOT NULL DEFAULT TRUE,
    `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_store_category` (`category`, `is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. USER_INVENTORY (Foydalanuvchi Egalik Qiluvchi Buyumlar)
CREATE TABLE IF NOT EXISTS `user_inventory` (
    `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `item_id` BIGINT UNSIGNED NOT NULL,
    `is_equipped` BOOLEAN NOT NULL DEFAULT FALSE,
    `purchased_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `unique_owned_item` (`user_id`, `item_id`),
    INDEX `idx_user_equipped` (`user_id`, `is_equipped`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. GAME_MATCHES (Onlayn Matchlar)
CREATE TABLE IF NOT EXISTS `game_matches` (
    `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `room_code` VARCHAR(20) NOT NULL UNIQUE,
    `game_type` VARCHAR(50) NOT NULL,
    `player1_id` BIGINT UNSIGNED NOT NULL,
    `player2_id` BIGINT UNSIGNED NULL,
    `winner_id` BIGINT UNSIGNED NULL,
    `status` ENUM('WAITING', 'IN_PROGRESS', 'FINISHED', 'FORFEITED', 'CANCELLED') NOT NULL DEFAULT 'WAITING',
    `moves_count` INT UNSIGNED NOT NULL DEFAULT 0,
    `p1_claimed` BOOLEAN NOT NULL DEFAULT FALSE,
    `p2_claimed` BOOLEAN NOT NULL DEFAULT FALSE,
    `started_at` TIMESTAMP NULL,
    `finished_at` TIMESTAMP NULL,
    `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_matches_room` (`room_code`),
    INDEX `idx_matches_players` (`player1_id`, `player2_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. GAME_SESSIONS (Offline / Solo / Bot O'yin Sessiyalari)
CREATE TABLE IF NOT EXISTS `game_sessions` (
    `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `session_id` VARCHAR(100) NOT NULL UNIQUE,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `game_type` VARCHAR(50) NOT NULL,
    `seed` VARCHAR(100) NOT NULL,
    `status` ENUM('ACTIVE', 'FINISHED', 'EXPIRED') NOT NULL DEFAULT 'ACTIVE',
    `score` INT UNSIGNED NOT NULL DEFAULT 0,
    `level` INT UNSIGNED NOT NULL DEFAULT 1,
    `moves` INT UNSIGNED NOT NULL DEFAULT 0,
    `duration_seconds` INT UNSIGNED NOT NULL DEFAULT 0,
    `reward_coins` INT UNSIGNED NOT NULL DEFAULT 0,
    `reward_xp` INT UNSIGNED NOT NULL DEFAULT 0,
    `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    `expires_at` TIMESTAMP NULL,
    `finished_at` TIMESTAMP NULL,
    INDEX `idx_session_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. DAILY_QUESTS (Kunlik Vazifalar Qoliplari)
CREATE TABLE IF NOT EXISTS `daily_quests` (
    `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `quest_key` VARCHAR(100) NOT NULL UNIQUE,
    `game_key` VARCHAR(50) NOT NULL,
    `title` VARCHAR(255) NOT NULL,
    `target_count` INT UNSIGNED NOT NULL DEFAULT 1,
    `coin_reward` INT UNSIGNED NOT NULL DEFAULT 70,
    `xp_reward` INT UNSIGNED NOT NULL DEFAULT 100,
    `is_active` BOOLEAN NOT NULL DEFAULT TRUE,
    `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. USER_QUEST_PROGRESS (Foydalanuvchining Kunlik Quest Progressi)
CREATE TABLE IF NOT EXISTS `user_quest_progress` (
    `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `quest_id` BIGINT UNSIGNED NOT NULL,
    `quest_date` DATE NOT NULL,
    `current_progress` INT UNSIGNED NOT NULL DEFAULT 0,
    `is_claimed` BOOLEAN NOT NULL DEFAULT FALSE,
    `claimed_at` TIMESTAMP NULL,
    UNIQUE KEY `unique_user_quest_day` (`user_id`, `quest_id`, `quest_date`),
    INDEX `idx_user_quest_date` (`user_id`, `quest_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. WHEEL_SPINS (Omad G'ildiragi Jurnali)
CREATE TABLE IF NOT EXISTS `wheel_spins` (
    `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `spin_date` DATE NOT NULL,
    `segment_key` VARCHAR(50) NOT NULL,
    `reward_coins` INT UNSIGNED NOT NULL DEFAULT 0,
    `reward_xp` INT UNSIGNED NOT NULL DEFAULT 0,
    `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_wheel_date` (`user_id`, `spin_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. ECONOMY_SETTINGS (Dinamik Limitlar va Mukofotlar)
CREATE TABLE IF NOT EXISTS `economy_settings` (
    `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `setting_key` VARCHAR(100) NOT NULL UNIQUE,
    `setting_value` VARCHAR(255) NOT NULL,
    `description` VARCHAR(255) NULL,
    `updated_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. PERSONAL_ACCESS_TOKENS (Laravel Sanctum Bearer Tokenlari)
CREATE TABLE IF NOT EXISTS `personal_access_tokens` (
    `id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    `tokenable_type` VARCHAR(255) NOT NULL,
    `tokenable_id` BIGINT UNSIGNED NOT NULL,
    `name` VARCHAR(255) NOT NULL,
    `token` VARCHAR(64) NOT NULL UNIQUE,
    `abilities` TEXT NULL,
    `last_used_at` TIMESTAMP NULL,
    `expires_at` TIMESTAMP NULL,
    `created_at` TIMESTAMP NULL,
    `updated_at` TIMESTAMP NULL,
    INDEX `personal_access_tokens_tokenable_type_tokenable_id_index` (`tokenable_type`, `tokenable_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;

-- ====================================================================
-- SEED DATA: Dastlabki Ma'lumotlarni Joylashtirish
-- ====================================================================

-- 10.1. Economy Settings
INSERT INTO `economy_settings` (`setting_key`, `setting_value`, `description`) VALUES
('ONLINE_WIN_REWARD', '50', 'Onlayn 1v1 g\'alabasi uchun beriladigan tanga'),
('ONLINE_WIN_XP', '100', 'Onlayn 1v1 g\'alabasi uchun beriladigan XP'),
('ONLINE_DRAW_REWARD', '20', 'Onlayn durang tangasi'),
('ONLINE_DRAW_XP', '40', 'Onlayn durang XP si'),
('ONLINE_LOSS_REWARD', '10', 'Onlayn mag\'lubiyat rag\'bat tangasi'),
('ONLINE_LOSS_XP', '20', 'Onlayn mag\'lubiyat XP si'),
('BOT_DAILY_LIMIT', '100', 'Bot o\'yinlaridan bir kunda olinishi mumkin bo\'lgan maksimal tanga'),
('SOLO_DAILY_LIMIT', '150', 'Solo boshqotirmalardan bir kunda olinishi mumkin bo\'lgan maksimal tanga'),
('WHEEL_COOLDOWN_HOURS', '24', 'Omad g\'ildiragi qayta ochilish vaqti (soatda)'),
('INITIAL_WALLET_BALANCE', '100', 'Yangi ro\'yxatdan o\'tgan o\'yinchiga beriladigan dastlabki tanga')
ON DUPLICATE KEY UPDATE `setting_value`=VALUES(`setting_value`);

-- 10.2. Store Items (Shaxmat, Shashka, Emotelar, Temalar)
INSERT INTO `store_items` (`item_key`, `name`, `category`, `price`, `rarity`, `description`) VALUES
-- Chess Boards
('chess_wood', 'Klassik Yog\'och Doska', 'CHESS_BOARD', 0, 'COMMON', 'Klassik turnir shaxmat doskasi'),
('chess_marble', 'Marmar Doska', 'CHESS_BOARD', 100, 'RARE', 'Oq va to\'q kulrang hashamatli marmar'),
('chess_cyber', 'Kiber Neon Doska', 'CHESS_BOARD', 150, 'EPIC', 'Neon uslubidagi zamonaviy kiber doska'),
('chess_royal_gold', 'Qirollik Oltin Doska', 'CHESS_BOARD', 200, 'LEGENDARY', 'Oltin hoshiyali qirollik shaxmat doskasi'),
-- Chess Pieces
('pieces_classic', 'Standart Toshlar', 'CHESS_PIECES', 0, 'COMMON', 'Klassik yog\'och shaxmat donalari'),
('pieces_modern3d', '3D Minimal Toshlar', 'CHESS_PIECES', 150, 'RARE', 'Zamonaviy minimal 3D donalar'),
('pieces_neon_glow', 'Neon Toshlar', 'CHESS_PIECES', 250, 'EPIC', 'Yaltirab turuvchi kiber neon donalar'),
-- Checkers Boards
('checkers_classic', 'Klassik Shashka Doskasi', 'CHECKERS_BOARD', 0, 'COMMON', 'Standart qora va oq shashka doskasi'),
('checkers_tournament_blue', 'Turnir Moviy Doskasi', 'CHECKERS_BOARD', 80, 'RARE', 'Professional turnir moviy doskasi'),
('checkers_ruby_fire', 'Qizil Olov Doskasi', 'CHECKERS_BOARD', 120, 'EPIC', 'Olovli qizil va to\'q qizil doska'),
('checkers_emerald', 'Zumrad Yashil Doskasi', 'CHECKERS_BOARD', 150, 'LEGENDARY', 'Nafis zumrad yashil shashka maydoni'),
-- Checkers Pieces
('checkers_pieces_standard', 'Standart Shashka Toshlari', 'CHECKERS_PIECES', 0, 'COMMON', 'Klassik qora va oq shashkalar'),
('checkers_pieces_amber', 'Qahrabo Shashkalar', 'CHECKERS_PIECES', 100, 'RARE', 'Yaltiroq sariq va qahrabo shashkalar'),
('checkers_pieces_ruby', 'Yoqut Shashkalar', 'CHECKERS_PIECES', 160, 'EPIC', 'Qimmatbaho yoqut qizil shashkalar'),
('checkers_pieces_jade', 'Nefrit Shashkalar', 'CHECKERS_PIECES', 200, 'LEGENDARY', 'Imperator nefrit yashil shashkalari'),
-- Emote Packs
('emote_royal', 'Qirollik To\'plami (👑 💎 🏆 ⚡)', 'EMOTE_PACK', 150, 'EPIC', 'Qirollik unvoniga munosib reaksiyalar'),
('emote_fire', 'Olovli To\'plam (🔥 💥 🚀 💣)', 'EMOTE_PACK', 150, 'EPIC', 'Qizg\'in jangga mos olovli animatsiyalar'),
('emote_funny', 'Hazil & Reaksiya (😂 😎 🤔 😱)', 'EMOTE_PACK', 150, 'RARE', 'Do\'stlar bilan o\'ynaganda qiziqarli yuzlar'),
('emote_victory', 'G\'alaba & Provokatsiya (💀 👻 🤡 🫡)', 'EMOTE_PACK', 150, 'EPIC', 'Raqibni lol qoldiruvchi unikal emojilar'),
-- Game Themes
('theme_tictactoe_neon', 'Tic-Tac-Toe Neon Glow', 'GAME_THEME', 120, 'RARE', 'Yorqin kiber neon X va O mavzusi'),
('theme_dots_cyber', 'Dots & Boxes Dark Cyber', 'GAME_THEME', 120, 'RARE', 'Qorong\'u kiber nuqtalar va qutilar temasi'),
('theme_2048_vintage', '2048 Classic Vintage', 'GAME_THEME', 120, 'RARE', 'Klassik yog\'och bloklar mavzusi')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`), `price`=VALUES(`price`), `rarity`=VALUES(`rarity`);

-- 10.3. Daily Quests Templates
INSERT INTO `daily_quests` (`quest_key`, `game_key`, `title`, `target_count`, `coin_reward`, `xp_reward`) VALUES
('water_sort_2win', 'water_sort', '🧪 Yangi sinov: Water Sortda 2 bosqich yuting', 2, 75, 110),
('chess_1game', 'chess', '👑 Grossmeyster: Shaxmatda 1 ta o\'yin o\'ynang', 1, 80, 120),
('chess_1win', 'chess', '👑 Shaxmat: 1 marta mot qiling va yuting', 1, 100, 160),
('checkers_2game', 'checkers', '♟️ Shashka: 2 ta partiya o\'ynab ko\'ring', 2, 70, 100),
('checkers_1win', 'checkers', '♟️ Shashka: 1 marta g\'alaba qozoning', 1, 85, 130),
('connect4_2game', 'connect4', '🔴 Connect 4: 2 ta o\'yinda ishtirok eting', 2, 70, 100),
('connect4_1win', 'connect4', '🔴 Connect 4: 4 ta toshni qator terib yuting', 1, 85, 130),
('gomoku_1game', 'gomoku', '⚪ Gomoku: 5 ta tosh terishda 1 o\'yin o\'ynang', 1, 70, 100),
('gomoku_1win', 'gomoku', '⚪ Gomoku: Qatorda 5 ta tosh yig\'ib yuting', 1, 90, 140),
('dots_2game', 'dots_and_boxes', '📦 Dots & Boxes: 2 ta o\'yin o\'ynang', 2, 70, 100),
('dots_1win', 'dots_and_boxes', '📦 Dots & Boxes: Katakchalarni egallab yuting', 1, 85, 130),
('durak_2game', 'durak', '🃏 Durak: 2 ta karta jangi o\'ynang', 2, 70, 100),
('durak_1win', 'durak', '🃏 Durak: Barcha kartalardan qutulib yuting', 1, 90, 140),
('2048_1game', '2048', '🔢 2048 Classic: Bloklarni birlashtiring', 1, 65, 90),
('drop_1game', 'drop_number', '💧 Drop Number: Yangi rekord o\'rnating', 1, 65, 90),
('math_1game', 'math_game', '🧮 Matematika: 1 ta tezkor hisob-kitob bajaring', 1, 65, 90),
('color_1game', 'color_match', '🎨 Rang moslash: E\'tiborni sinang', 1, 65, 90),
('memory_2game', 'memory_game', '🧠 Xotira o\'yini: 2 ta kartalar juftligini toping', 2, 75, 110),
('tictactoe_2win', 'tictactoe', '⚡ Tic-Tac-Toe: Arenada 2 ta g\'alaba qozoning', 2, 75, 110)
ON DUPLICATE KEY UPDATE `title`=VALUES(`title`), `coin_reward`=VALUES(`coin_reward`), `xp_reward`=VALUES(`xp_reward`);
