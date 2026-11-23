package com.thousandlabs.ohara.translate

import com.thousandlabs.ohara.data.Prefs

/**
 * Argot, mèmes et langage d'internet.
 *
 * Un traducteur lit « no cap », « sus », « skill issue » ou « OOC » mot à mot. Avant la traduction, ces expressions sont
 * donc remplacées par leur sens en anglais courant (« no lie », « suspicious », « that's your own lack of skill »,
 * « out of character »), que le traducteur sait rendre en français. Les mots étirés (« Sooooo », « heyyyy ») sont ramenés à
 * leur forme normale. Chaque règle tient compte du contexte : « salty » ne change que pour parler d'une personne (« so salty about »),
 * pas d'une soupe ; « lit » seulement dans « it's lit », pas dans « she lit a candle » ; « OC » et « AU » seulement en majuscules.
 *
 * Interrupteur : Réglages › Traduction en français › « Comprendre l'argot et le langage d'internet ».
 */
object Slang {
    var enabled: Boolean
        get() = Prefs.getBool("tr_slang", true)
        set(v) { Prefs.putBool("tr_slang", v) }

    /**
     * Garder l'argot en version originale : les expressions de la liste (« skill issue », « womp womp », « rizz »…) ne sont ni
     * remplacées par leur sens ni traduites ; elles restent en anglais au milieu de la phrase française.
     * Interrupteur : Réglages › Traduction en français › « Garder l'argot et les mèmes en version originale ».
     */
    var keepOriginal: Boolean
        get() = Prefs.getBool("tr_slang_vo", false)
        set(v) { Prefs.putBool("tr_slang_vo", v) }

    /** Expressions gardées telles quelles quand « version originale » est activé. En minuscules : reconnues avec ou sans majuscule. */
    val VO: List<String> = listOf(
        "skill issue", "skill diff", "womp womp", "womp womp womp", "no cap", "capping", "sus", "sussy", "rizz", "rizzed up", "rizzler",
        "touch grass", "bruh", "goated", "GOAT", "copium", "hopium", "sheesh", "yeet", "yeeted", "yeeting", "deadass", "lowkey", "highkey",
        "fr fr", "bussin", "simp", "simping", "vibe check", "main character energy", "git gud", "get gud", "gg", "ez", "rekt", "pwned",
        "noob", "noobs", "poggers", "pog", "kekw", "oof", "yikes", "welp", "ayo", "L + ratio", "glow up", "glow-up", "big brain",
        "galaxy brain", "sigma", "mewing", "aura farming", "lol", "lmao", "lmfao", "rofl", "omg", "wtf", "smh", "ngl", "tbh", "fml", "yolo"
    )

    /** Vos expressions, ajoutées dans les réglages. */
    val userVO: List<String> get() = Prefs.getList("slang_vo_user")

    fun addUserVO(raw: String): String? {
        val n = raw.replace(Regex("\\s+"), " ").trim().trim('"', '«', '»', '.', ',', '!', '?')
        if (n.isEmpty() || n.length > 60 || !n.any { it.isLetter() }) return null
        if (userVO.any { it.equals(n, true) }) return n
        Prefs.putList("slang_vo_user", (userVO + n).sortedBy { it.lowercase() })
        return n
    }

    fun removeUserVO(n: String) { Prefs.putList("slang_vo_user", userVO - n) }

    /** Liste à protéger pendant la traduction (vide si l'option est coupée). */
    fun voList(): List<String> = if (!keepOriginal) emptyList() else (VO + userVO).distinct()

    @Volatile private var voCache: Pair<List<String>, Regex?> = emptyList<String>() to null

    /** Repère les expressions à garder, pour que les règles de sens ne les touchent pas. */
    private fun voRegex(): Regex? {
        val l = voList()
        voCache.let { if (it.first == l) return it.second }
        val alts = l.sortedByDescending { it.length }.map { e ->
            // la dernière lettre peut être étirée (« bruhhh », « oooof »)
            val body = Regex.escape(e) + (if (e.last().isLetter()) Regex.escape(e.last().toString()) + "*" else "")
            if (e.any { it.isUpperCase() }) body else "(?i:$body)"
        }
        val rx = if (alts.isEmpty()) null else Regex(BEFORE + "(?:" + alts.joinToString("|") + ")" + AFTER)
        voCache = l to rx
        return rx
    }

    /** ci : sans tenir compte de la casse ; cs : casse respectée ; ac : sigle en majuscules (majuscule au résultat seulement en début de phrase). */
    private class Rule(val rx: Regex, val to: String, val acronym: Boolean)

    private const val BEFORE = "(?<![\\p{L}\\p{N}'’])"
    private const val AFTER = "(?![\\p{L}\\p{N}])"
    private fun ci(p: String, to: String) = Rule(Regex(BEFORE + "(?:" + p + ")" + AFTER, RegexOption.IGNORE_CASE), to, false)
    private fun cs(p: String, to: String) = Rule(Regex(BEFORE + "(?:" + p + ")" + AFTER), to, false)
    private fun ac(p: String, to: String) = Rule(Regex(BEFORE + "(?:" + p + ")" + AFTER), to, true)

    private val RULES: List<Rule> by lazy {
        listOf(
        ci("lol|lolol|lmao|lmfao|rofl|roflmao", "haha"),
        ci("omfg|omg", "oh my God"),
        ci("wtf", "what the hell"),
        ci("wth", "what the heck"),
        ci("tbh|ngl", "honestly"),
        ci("imo|imho", "in my opinion"),
        ci("idk", "I don't know"),
        ci("idc", "I don't care"),
        ci("ikr", "I know, right"),
        ci("irl", "in real life"),
        ci("btw", "by the way"),
        ci("fyi", "for your information"),
        ci("brb", "be right back"),
        ci("afk", "away from the keyboard"),
        ci("smh", "shaking my head"),
        ci("fml", "my life is terrible"),
        ci("ily", "I love you"),
        ci("tfw", "that feeling when"),
        ci("mfw", "my face when"),
        ci("ftw", "for the win"),
        ci("tl;dr|tldr", "in short"),
        ci("pls|plz", "please"),
        ci("dunno", "don't know"),
        ci("yolo", "you only live once"),
        ci("fomo", "fear of missing out"),
        ci("wass?up|wh?ats?up", "what's up"),
        ci("no cap", "no lie"),
        ci("(?:that|this|it)(?:'?s| is) cap", "that's a lie"),
        ci("capping", "lying"),
        ci("on god", "I swear"),
        ci("fr fr", "for real"),
        ci("deadass", "seriously"),
        ci("lowkey", "kind of"),
        ci("highkey", "really"),
        cs("sus|sussy", "suspicious"),
        ci("(?<=(?:he|she|you|they|we|i|he's|she's|you're|they're|we're|i'm|he was|she was|you were|they were) (?:so |too |really |very |still |kinda |just |a bit |a little )?)salty|salty(?= (?:about|over|because|that|since)\\b)", "bitter and resentful"),
        cs("(?<=(?:was|were|is|are|be|been|got|gets|get) )yeeted", "thrown"),
        cs("yeeted", "threw"),
        cs("yeeting", "throwing"),
        cs("yeet", "throw"),
        ci("rizzed up", "charmed"),
        ci("rizz", "charm"),
        ci("(?<=(?:that'?s|it'?s|(?:that|it|this) is|so|pretty|really|kinda) )mid(?!-)", "mediocre"),
        cs("bussin'?", "delicious"),
        ci("(?<=(?:that'?s|it'?s|(?:that|it|this) is|so|pretty|really) )lit(?! up)", "amazing"),
        ci("(?<=(?:song|track|beat|banger|album|food|jam|one|this|that|it|these|those) )slaps", "is amazing"),
        ci("goated", "the greatest"),
        ac("(?:the )?GOAT", "the greatest of all time"),
        ci("bruh+", "dude"),
        ci("sheee*sh", "wow"),
        ci("oof+", "ouch"),
        ci("yikes", "oh no"),
        ci("welp", "well"),
        cs("ayo", "hey"),
        ci("touch grass", "go outside"),
        ci("glow[- ]up", "transformation"),
        ci("nooo+", "no, no, no"),
        ac("(?:Press|press) F(?: to pay respects)?", "pay respects"),
        ac("F in (?:the )?chat", "pay respects"),
        ci("rick-?rolled", "tricked with the Rick Astley video"),
        ci("rick-?roll", "trick with the Rick Astley video"),
        ci("skill issue", "that's your own lack of skill"),
        ci("git gud|get gud", "get better at it"),
        cs("poggers|pogchamp|pog", "awesome"),
        cs("kekw?|kekeke", "haha"),
        ci("copium", "denial"),
        ci("hopium", "wishful thinking"),
        cs("noobs|n00bs|newbs", "newbies"),
        cs("noob|n00b|newb", "newbie"),
        cs("pwned", "crushed"),
        cs("pwn", "crush"),
        cs("rekt", "destroyed"),
        ci("rage[- ]?quit(?:ted|s|ting)?", "quit in anger"),
        ci("try-?hards?", "someone trying way too hard"),
        ac("(?<=(?:so|too|really|totally|completely|way|is|are|be|being|that'?s|it'?s) )OP", "overpowered"),
        ac("OOC", "out of character"),
        ac("OCs", "original characters"),
        ac("OC", "original character"),
        ac("AUs", "alternate universes"),
        ac("AU", "alternate universe"),
        ac("A/N", "Author's note")
        )
    }

    /** Termes de fanfiction à mettre directement en français (comme les termes officiels de One Piece ou de Naruto). */
    val TERMS: Map<String, String> = mapOf(
        "plot armor" to "bouclier scénaristique", "plot armour" to "bouclier scénaristique",
        "Author's note" to "Note de l'auteur", "Author\u2019s note" to "Note de l'auteur",
        "Author's Note" to "Note de l'auteur", "Author\u2019s Note" to "Note de l'auteur"
    )

    /** Mots de fanfiction et d'internet que les lecteurs français emploient tels quels : jamais traduits. */
    val KEEP: List<String> = listOf(
        "headcanon", "headcanons", "self-insert", "self-inserts", "Self-Insert", "Mary Sue", "Gary Stu", "one-shot", "oneshot",
        "cliffhanger", "slow burn", "crackfic", "fanfic", "fanfics", "fanfiction", "uwu", "owo", "UwU", "OwO", "NPC", "NPCs"
    )

    private val KEPT = Regex("\uE000(\\d+)\uE001")
    private val STRETCH = Regex("(\\p{L})\\1{2,}", RegexOption.IGNORE_CASE)
    private val WORD = Regex("\\p{L}+")

    /** « Sooooo » → « So », mais « Sssss » et « Zzzzz » (un bruit, pas un mot étiré) restent tels quels. */
    private fun unstretch(w: String): String {
        val m = STRETCH.find(w) ?: return w
        if (m.value.length == w.length) return w
        return STRETCH.replace(w) { it.groupValues[1] }
    }

    private fun sentenceStart(s: String, i: Int): Boolean {
        var k = i - 1
        while (k >= 0 && s[k].isWhitespace()) k--
        return k < 0 || s[k] in ".!?\u2026\"\u201c\u00ab("
    }

    /** Le texte avec l'argot remplacé par son sens courant ; inchangé si la fonction est coupée. */
    fun rewrite(s: String): String {
        if (!enabled || s.length < 2) return s
        // expressions gardées en version originale : mises de côté pendant que les règles s'appliquent
        val kept = ArrayList<String>()
        var t = voRegex()?.replace(s) { m -> kept += m.value; "\uE000" + (kept.size - 1) + "\uE001" } ?: s
        for (r in RULES) {
            if (!r.rx.containsMatchIn(t)) continue
            val cur = t
            t = r.rx.replace(cur) { m ->
                val up = m.value.first().isUpperCase()
                if ((!r.acronym && up) || sentenceStart(cur, m.range.first)) r.to.replaceFirstChar { it.uppercaseChar() } else r.to
            }
        }
        if (STRETCH.containsMatchIn(t)) t = WORD.replace(t) { unstretch(it.value) }
        if (kept.isNotEmpty()) t = KEPT.replace(t) { m -> kept.getOrNull(m.groupValues[1].toInt()) ?: m.value }
        return t
    }
}
