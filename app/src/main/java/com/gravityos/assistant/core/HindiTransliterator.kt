package com.gravityos.assistant.core

object HindiTransliterator {

    private val commonWords = mapOf(
        "ग्रैविटी" to "gravity",
        "ग्रेविटी" to "gravity",
        "हे" to "hey",
        "ओके" to "ok",
        "कॉल" to "call",
        "कौल" to "call",
        "फ़ोन" to "phone",
        "फोन" to "phone",
        "लगाओ" to "lagao",
        "लगा" to "laga",
        "करो" to "karo",
        "दो" to "do",
        "को" to "ko",
        "पे" to "pe",
        "पर" to "par",
        "खोलो" to "kholo",
        "खोल" to "khol",
        "चलाओ" to "chalao",
        "चालू" to "chalu",
        "बंद" to "band",
        "टॉर्च" to "torch",
        "फ्लैशलाइट" to "flashlight",
        "वॉल्यूम" to "volume",
        "आवाज" to "awaz",
        "आवाज़" to "awaz",
        "बढ़ाओ" to "badhao",
        "बढाओ" to "badhao",
        "कम" to "kam",
        "म्यूट" to "mute",
        "फुल" to "full",
        "वाईफाई" to "wifi",
        "वाई-फाई" to "wifi",
        "ब्लूटूथ" to "bluetooth",
        "हॉटस्पॉट" to "hotspot",
        "कैमरा" to "camera",
        "सेटिंग" to "settings",
        "सेटिंग्स" to "settings",
        "यूट्यूब" to "youtube",
        "व्हाट्सएप" to "whatsapp",
        "व्हाट्सऐप" to "whatsapp",
        "इंस्टाग्राम" to "instagram",
        "फेसबुक" to "facebook",
        "टेलीग्राम" to "telegram",
        "गूगल" to "google",
        "क्रोम" to "chrome",
        "जीमेल" to "gmail",
        "मैप्स" to "maps",
        "गैलरी" to "gallery",
        "कैलकुलेटर" to "calculator",
        "हाँ" to "haan",
        "हां" to "haan",
        "नहीं" to "nahi",
        "कैंसल" to "cancel",
        "मम्मी" to "mummy",
        "पापा" to "papa",
        "भाई" to "bhai",
        "भैया" to "bhaiya",
        "दीदी" to "didi"
    )

    private val independentVowels = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee",
        'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri", 'ए' to "e",
        'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o", 'ॲ' to "a"
    )

    private val consonants = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "ng",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "ny",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
        'ळ' to "l", 'क़' to "q", 'ख़' to "kh", 'ग़' to "g",
        'ज़' to "z", 'ड़' to "r", 'ढ़' to "rh", 'फ़' to "f"
    )

    private val matras = mapOf(
        'ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "u",
        'ृ' to "ri", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au",
        'ॉ' to "o", 'ॅ' to "e"
    )

    private val digits = mapOf(
        '०' to '0', '१' to '1', '२' to '2', '३' to '3', '४' to '4',
        '५' to '5', '६' to '6', '७' to '7', '८' to '8', '९' to '9'
    )

    fun containsDevanagari(text: String): Boolean {
        return text.any { it in '\u0900'..'\u097F' }
    }

    fun transliterate(input: String): String {
        if (!containsDevanagari(input)) return input

        return input.split(Regex("\\s+")).joinToString(" ") { rawWord ->
            val stripped = rawWord.trim(',', '.', '?', '!', '।')
            commonWords[stripped] ?: transliterateWord(stripped)
        }
    }

    private fun transliterateWord(word: String): String {
        if (!containsDevanagari(word)) return word
        val sb = StringBuilder()
        var i = 0
        while (i < word.length) {
            val ch = word[i]
            when {
                ch in digits -> {
                    sb.append(digits[ch])
                    i++
                }
                ch in independentVowels -> {
                    sb.append(independentVowels[ch])
                    i++
                }
                ch in consonants -> {
                    var base = consonants[ch] ?: ""
                    // Handle nukta (़) after consonant
                    if (i + 1 < word.length && word[i + 1] == '़') {
                        base = when (ch) {
                            'क' -> "q"
                            'ख' -> "kh"
                            'ग' -> "g"
                            'ज' -> "z"
                            'ड' -> "r"
                            'ढ' -> "rh"
                            'फ' -> "f"
                            else -> base
                        }
                        i++
                    }
                    sb.append(base)
                    val next = if (i + 1 < word.length) word[i + 1] else null
                    when {
                        next == '्' -> {
                            // Halant suppresses inherent 'a'
                            i += 2
                        }
                        next != null && next in matras -> {
                            sb.append(matras[next])
                            i += 2
                        }
                        else -> {
                            // Add inherent 'a' except at word end
                            if (i < word.length - 1) {
                                sb.append('a')
                            }
                            i++
                        }
                    }
                }
                ch == 'ं' || ch == 'ँ' -> {
                    sb.append('n')
                    i++
                }
                ch == 'ः' -> {
                    sb.append('h')
                    i++
                }
                ch == '्' || ch == '़' -> {
                    i++
                }
                else -> {
                    sb.append(ch)
                    i++
                }
            }
        }
        return sb.toString()
    }
}
