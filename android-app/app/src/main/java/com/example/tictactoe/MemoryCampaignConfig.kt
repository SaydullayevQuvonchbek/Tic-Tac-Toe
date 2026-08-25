package com.example.tictactoe

object MemoryCampaignConfig {

    const val MAX_LEVELS = 30

    data class LevelConfig(
        val level: Int,
        val rows: Int,
        val cols: Int,
        val timeSeconds: Int,
        val themeName: String,
        val pool: List<String>
    ) {
        val pairsCount: Int get() = (rows * cols) / 2
    }

    private val FRUITS = listOf("🍎", "🍌", "🍇", "🍊", "🍓", "🍉", "🍒", "🍍", "🥝", "🥑", "🍑", "🍋", "🥭", "🍐", "🥥", "🫐", "🍏", "🍈")
    private val ANIMALS = listOf("🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔", "🐧", "🦉")
    private val SPORTS = listOf("⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🏉", "🎱", "🏓", "🏸", "🥊", "🥋", "🏆", "🥇", "🎯", "⛳", "🛹", "⛸️")
    private val NATURE = listOf("🌲", "🌻", "🌸", "🌺", "🌹", "🌷", "🌴", "🌵", "🍁", "🍂", "🍃", "🍄", "⭐", "🌙", "☀️", "⚡", "🔥", "💧")
    private val FLAGS = listOf("🇺🇿", "🇺🇸", "🇬🇧", "🇩🇪", "🇫🇷", "🇯🇵", "🇰🇷", "🇧🇷", "🇦🇷", "🇮🇹", "🇪🇸", "🇨🇦", "🇹🇷", "🇦🇺", "🇮🇳", "🇨🇭", "🇸🇪", "🇲🇽")
    private val TECH = listOf("💻", "📱", "🎮", "🕹️", "🎧", "📷", "🔋", "💡", "🚀", "🛸", "🛰️", "🤖", "💎", "🔮", "🕹️", "⌚", "🎙️", "📡")

    fun getLevelConfig(level: Int): LevelConfig {
        return when (level) {
            1 -> LevelConfig(1, 2, 2, 20, "Fruits Intro", FRUITS)
            2 -> LevelConfig(2, 2, 3, 25, "Fruits Easy", FRUITS)
            3 -> LevelConfig(3, 2, 4, 30, "Fruits Grid", FRUITS)
            4 -> LevelConfig(4, 2, 4, 25, "Animals Quick", ANIMALS)
            5 -> LevelConfig(5, 3, 4, 40, "Animals Challenge", ANIMALS)

            6 -> LevelConfig(6, 3, 4, 32, "Sports Rush", SPORTS)
            7 -> LevelConfig(7, 4, 4, 55, "Sports Stadium", SPORTS)
            8 -> LevelConfig(8, 4, 4, 45, "Nature Walk", NATURE)
            9 -> LevelConfig(9, 4, 4, 38, "Nature Sprint", NATURE)
            10 -> LevelConfig(10, 4, 4, 30, "Tech Matrix", TECH)

            11 -> LevelConfig(11, 5, 4, 60, "Fruits Expert", FRUITS)
            12 -> LevelConfig(12, 5, 4, 52, "Wild Jungle", ANIMALS)
            13 -> LevelConfig(13, 5, 4, 45, "Olympic Games", SPORTS)
            14 -> LevelConfig(14, 5, 4, 38, "Botanical Garden", NATURE)
            15 -> LevelConfig(15, 5, 4, 32, "Flags of the World", FLAGS)

            16 -> LevelConfig(16, 6, 4, 70, "World Tour", FLAGS)
            17 -> LevelConfig(17, 6, 4, 60, "Safari Expedition", ANIMALS)
            18 -> LevelConfig(18, 6, 4, 52, "Tropical Orchard", FRUITS)
            19 -> LevelConfig(19, 6, 4, 45, "Cyber Dimension", TECH)
            20 -> LevelConfig(20, 6, 4, 38, "Mega Mix Matrix", TECH + FRUITS)

            21 -> LevelConfig(21, 6, 5, 85, "Animal Kingdom", ANIMALS)
            22 -> LevelConfig(22, 6, 5, 75, "Enchanted Forest", NATURE)
            23 -> LevelConfig(23, 6, 5, 65, "Champions League", SPORTS)
            24 -> LevelConfig(24, 6, 5, 55, "United Nations", FLAGS)
            25 -> LevelConfig(25, 6, 5, 48, "Speed Demon", TECH + ANIMALS)

            26 -> LevelConfig(26, 6, 6, 100, "Grandmaster 36", TECH + FLAGS)
            27 -> LevelConfig(27, 6, 6, 85, "Fruit Frenzy 36", FRUITS + NATURE)
            28 -> LevelConfig(28, 6, 6, 75, "Ultimate Wildlife", ANIMALS + NATURE)
            29 -> LevelConfig(29, 6, 6, 65, "Memory Prodigy", SPORTS + TECH)
            30 -> LevelConfig(30, 6, 6, 50, "Memory God", FRUITS + ANIMALS + FLAGS + TECH)

            else -> LevelConfig(level, 4, 4, 40, "Bonus Stage", FRUITS)
        }
    }
}
