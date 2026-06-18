package com.batmudcn.util

object Constants {
    // Default server
    const val DEFAULT_HOST = "batmud.bat.org"
    const val DEFAULT_PORT = 23

    // Baidu Translate API
    const val BAIDU_API_URL = "https://fanyi-api.baidu.com/ait/api/aiTextTranslate"
    const val DEFAULT_MODEL_TYPE = "llm"
    const val DEFAULT_TIMEOUT = 15
    const val DEFAULT_QPS = 5

    // Translation settings
    const val DEFAULT_ENABLED = true
    const val DEFAULT_CACHE_SIZE = 2000
    const val DEFAULT_MIN_CHARS = 4
    const val DEFAULT_MAX_BATCH = 3

    // Telnet protocol
    const val IAC: Byte = 0xFF.toByte()
    const val DONT: Byte = 0xFE.toByte()
    const val DO: Byte = 0xFD.toByte()
    const val WONT: Byte = 0xFC.toByte()
    const val WILL: Byte = 0xFB.toByte()
    const val SB: Byte = 0xFA.toByte()
    const val SE: Byte = 0xF0.toByte()

    // Telnet options
    const val TELOPT_ECHO: Byte = 1.toByte()
    const val TELOPT_SGA: Byte = 3.toByte()
    const val TELOPT_TTYPE: Byte = 24.toByte()
    const val TELOPT_NAWS: Byte = 31.toByte()
    const val TELOPT_COMPRESS: Byte = 85.toByte()
    const val TELOPT_COMPRESS2: Byte = 86.toByte()
    const val TELOPT_MSSP: Byte = 70.toByte()
    const val TELOPT_GMCP: Byte = 201.toByte()
    const val TELOPT_EOR: Byte = 25.toByte()
    const val TELOPT_CHARSET: Byte = 42.toByte()

    // ANSI
    const val ESC: Byte = 0x1B.toByte()
    const val ANSI_SGR_PATTERN = "\\x1b\\[[\\d;]*m"

    // MUD translation reference prompt (synced with PC version translator.py)
    const val MUD_REFERENCE = (
        "你是一个专业的 MUD (Multi-User Dungeon) 游戏翻译助手。请遵循以下规则：" +
        "1. 保留所有数字、标点符号和特殊格式" +
        "2. 游戏专有名词保持翻译一致性（种族、职业、技能、装备名）" +
        "3. 战斗描述要生动有力（如 slash→猛砍, dodge→闪避, critical→致命一击）" +
        "4. 保持原文氛围：探索的神秘感、战斗的紧张感、NPC对话的个性" +
        "5. 方向词简短（north→北, exit→出口）" +
        "6. 翻译要简洁，适合实时游戏阅读" +
        "7. 如果原文已是中文或无法翻译的内容，原样返回"
    )

    // Max output lines to keep in terminal buffer
    const val MAX_OUTPUT_LINES = 5000

    // ANSI foreground color → RGB mapping (matches Python ansi_html.py + style.css)
    val FG_COLORS: Map<Int, Long> = mapOf(
        30 to 0xFF1A1A1A, 31 to 0xFFCC3333, 32 to 0xFF33AA33, 33 to 0xFFCC9933,
        34 to 0xFF3366CC, 35 to 0xFF9933CC, 36 to 0xFF33AAAA, 37 to 0xFFD0D0D0,
        90 to 0xFF555555, 91 to 0xFFFF5555, 92 to 0xFF55CC55, 93 to 0xFFFFCC55,
        94 to 0xFF5588FF, 95 to 0xFFCC55FF, 96 to 0xFF55DDDD, 97 to 0xFFFFFFFF,
    )

    val BG_COLORS: Map<Int, Long> = mapOf(
        40 to 0xFF1A1A1A, 41 to 0xFFCC3333, 42 to 0xFF33AA33, 43 to 0xFFCC9933,
        44 to 0xFF3366CC, 45 to 0xFF9933CC, 46 to 0xFF33AAAA, 47 to 0xFFD0D0D0,
        100 to 0xFF555555, 101 to 0xFFFF5555, 102 to 0xFF55CC55, 103 to 0xFFFFCC55,
        104 to 0xFF5588FF, 105 to 0xFFCC55FF, 106 to 0xFF55DDDD, 107 to 0xFFFFFFFF,
    )
}
