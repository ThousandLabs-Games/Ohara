package com.thousandlabs.ohara.translate

import com.thousandlabs.ohara.data.Prefs

/**
 * Noms à ne pas traduire : personnages, îles, villages, techniques…
 *
 * Avant la traduction, chaque nom protégé est remplacé par un numéro que le traducteur recopie tel quel ;
 * après, le nom d'origine est remis à sa place. « Drum Island » reste « Drum Island », « Usopp » reste « Usopp ».
 * Un nom écrit en minuscules dans une liste (chakra, jutsu…) est reconnu aussi avec une majuscule ;
 * sinon la casse compte, pour ne pas toucher aux mots ordinaires.
 * Les suffixes japonais collés au nom (-san, -sensei, -chan…) restent avec lui.
 */
object Names {

    class Fandom(val key: String, val label: String, val names: List<String>) {
        var enabled: Boolean
            get() = Prefs.getBool("names_$key", true)
            set(v) { Prefs.putBool("names_$key", v) }
    }

    /** Protection activée (interrupteur général). */
    var enabled: Boolean
        get() = Prefs.getBool("names_on", true)
        set(v) { Prefs.putBool("names_on", v) }

    val ONE_PIECE = Fandom("onepiece", "One Piece", listOf(
        // Équipage et proches
        "Monkey D. Luffy", "Luffy", "Roronoa Zoro", "Zoro", "Zolo", "Nami", "Usopp", "Sogeking", "Vinsmoke Sanji", "Sanji",
        "Tony Tony Chopper", "Chopper", "Nico Robin", "Robin", "Franky", "Cutty Flam", "Brook", "Jinbe", "Jinbei", "Jimbei",
        "Nefertari Vivi", "Vivi", "Karoo", "Carue", "Portgas D. Ace", "Ace", "Sabo", "Monkey D. Garp", "Garp", "Monkey D. Dragon",
        "Makino", "Dadan", "Kaya", "Nojiko", "Bell-mère", "Bellemere", "Genzo", "Zeff", "Laboon", "Crocus", "Dalton", "Kureha", "Hiluluk",
        "Iceburg", "Paulie", "Tom", "Yokozuna", "Camie", "Hatchan", "Pappag", "Shakky", "Shirahoshi", "Fukaboshi", "Otohime",
        // Pirates, Empereurs, Grands Corsaires
        "Shanks", "Benn Beckman", "Yasopp", "Lucky Roux", "Buggy", "Dracule Mihawk", "Mihawk", "Sir Crocodile", "Kaido", "Kaidou",
        "Charlotte Linlin", "Linlin", "Big Mom", "Charlotte Katakuri", "Katakuri", "Charlotte Pudding", "Charlotte Cracker",
        "Donquixote Doflamingo", "Doflamingo", "Donquixote Rosinante", "Rosinante", "Corazon", "Trafalgar D. Water Law", "Trafalgar Law", "Law",
        "Eustass Kid", "Eustass", "Boa Hancock", "Hancock", "Edward Newgate", "Newgate", "Marco", "Thatch", "Vista", "Izo",
        "Marshall D. Teach", "Teach", "Gecko Moria", "Moria", "Perona", "Bartholomew Kuma", "Kuma", "Jewelry Bonney", "Bonney",
        "Gol D. Roger", "Gold Roger", "Roger", "Silvers Rayleigh", "Rayleigh", "Scopper Gaban", "Kozuki Oden", "Oden", "Kozuki Momonosuke",
        "Momonosuke", "Kozuki Hiyori", "Hiyori", "Yamato", "Kin'emon", "Kinemon", "Kanjuro", "Raizo", "Kiku", "Kikunojo", "Denjiro", "Ashura Doji",
        "Inuarashi", "Nekomamushi", "Pedro", "Pekoms", "Bartolomeo", "Cavendish", "Rebecca", "Viola", "Kyros", "Riku Doldo", 
        "Capone Bege", "Bege", "X Drake", "Drake", "Urouge", "Scratchmen Apoo", "Apoo", "Basil Hawkins", "Hawkins", "Bepo", "Arlong",
        "Hody Jones", "Kuro", "Don Krieg", "Krieg", "Alvida", "Wapol", "Enel", "Eneru", "Gan Fall", "Wyper", "Conis", "Foxy", "Absalom",
        "Caesar Clown", "Monet", "Vergo", "Magellan", "Hannyabal", "Emporio Ivankov", "Ivankov", "Inazuma", "Bon Clay", "Bentham",
        "Mr. 0", "Mr. 1", "Mr. 2", "Mr. 3", "Mr. 4", "Mr. 5", "Miss All Sunday", "Miss Doublefinger", "Miss Goldenweek", "Miss Valentine",
        "Miss Merry Christmas", "Daz Bones", "Lafitte", "Van Augur", "Shiryu", "Jesus Burgess", "Burgess", "Catarina Devon", "Vasco Shot",
        // Marine et Gouvernement
        "Sakazuki", "Akainu", "Kuzan", "Aokiji", "Borsalino", "Kizaru", "Issho", "Fujitora", "Ryokugyu", "Aramaki", "Sengoku", "Tsuru",
        "Koby", "Coby", "Helmeppo", "Smoker", "Tashigi", "Hina", "Morgan", "Nezumi", "Fullbody", "Jango", "Kong", "Momousagi", "Garp",
        "Rob Lucci", "Lucci", "Kaku", "Kalifa", "Jabra", "Blueno", "Fukurou", "Kumadori", "Spandam", "Spandine", "Vegapunk", "Saint Charlos",
        "Gorosei", "Imu", "Saturn", "Jaygarcia Saturn", "Figarland Garling", "Shamrock", "Cipher Pol", "CP9", "CP0", "SWORD",
        // Révolutionnaires
        "Koala", "Belo Betty", "Morley", "Lindbergh", "Karasu", "Emporio Ivankov",
        // Lieux
        "Grand Line", "East Blue", "West Blue", "North Blue", "South Blue", "Red Line", "Calm Belt", "Foosha Village", "Fuschia Village",
        "Windmill Village", "Dawn Island", "Goa Kingdom", "Gray Terminal", "Mt. Colubo", "Shells Town", "Orange Town", "Syrup Village", "Baratie",
        "Arlong Park", "Cocoyasi Village", "Cocoyashi", "Loguetown", "Reverse Mountain", "Twin Capes", "Whisky Peak", "Little Garden",
        "Drum Island", "Drum Kingdom", "Sakura Kingdom", "Drum Rockies", "Alabasta", "Arabasta", "Alubarna", "Rainbase", "Nanohana", "Yuba", "Erumalu",
        "Jaya", "Mock Town", "Skypiea", "Angel Island", "Upper Yard", "Shandora", "Long Ring Long Land", "Water 7", "Water Seven", "Galley-La",
        "Enies Lobby", "Thriller Bark", "Sabaody Archipelago", "Sabaody", "Amazon Lily", "Kuja", "Impel Down", "Marineford", "Fish-Man Island",
        "Fishman Island", "Ryugu Kingdom", "Ryugu Palace", "Punk Hazard", "Dressrosa", "Green Bit", "Zou", "Mokomo Dukedom", "Whole Cake Island",
        "Whole Cake Chateau", "Totto Land", "Wano Country", "Wano", "Onigashima", "Kuri", "Flower Capital", "Ringo", "Udon", "Hakumai", "Kibi",
        "Egghead", "Elbaf", "Raftel", "Laugh Tale", "Mary Geoise", "Mariejois", "Marie Geoise", "God Valley", "Ohara", "Baltigo", "Kuraigana",
        "Momoiro Island", "Boin Archipelago", "Torino Kingdom", "Kamabakka Kingdom", "Namakura Island", "Weatheria", "Karakuri Island", "Rusukaina",
        "Lvneel", "Tequila Wolf", "Lulusia", "Sphinx", "Hachinosu", "Fullalead", "Thousand Sunny", "Going Merry", "Oro Jackson", "Red Force",
        "Moby Dick", "Polar Tang", "Saber of Xebec", "Rocks Pirates", "Xebec", "Rocks D. Xebec",
        // Fruits, Haki, techniques
        "Gomu Gomu no Mi", "Gomu Gomu", "Hito Hito no Mi", "Mera Mera no Mi", "Mera Mera", "Hie Hie no Mi", "Pika Pika no Mi", "Magu Magu no Mi",
        "Yami Yami no Mi", "Gura Gura no Mi", "Ope Ope no Mi", "Hana Hana no Mi", "Bara Bara no Mi", "Suna Suna no Mi", "Goro Goro no Mi",
        "Ito Ito no Mi", "Uo Uo no Mi", "Mochi Mochi no Mi", "Yomi Yomi no Mi", "Moku Moku no Mi", "Hobi Hobi no Mi", "Nikyu Nikyu no Mi",
        "Logia", "Paramecia", "Zoan", "Mythical Zoan", "Haki", "Busoshoku", "Busoshoku Haki", "Kenbunshoku", "Kenbunshoku Haki", "Haoshoku",
        "Haoshoku Haki", "Ryuo", "Gear Second", "Gear Third", "Gear Fourth", "Gear Fifth", "Gear 2", "Gear 3", "Gear 4", "Gear 5", "Boundman",
        "Snakeman", "Tankman", "Nika", "Joy Boy", "Joyboy", "Sun God Nika", "Gomu Gomu no Pistol", "Gomu Gomu no Gatling", "Gomu Gomu no Bazooka",
        "Gomu Gomu no Red Hawk", "Gomu Gomu no Jet Pistol", "Santoryu", "Nitoryu", "Ittoryu", "Kyutoryu", "Oni Giri", "Onigiri", "Asura",
        "Shishi Sonson", "Sanzen Sekai", "Diable Jambe", "Ifrit Jambe", "Concasse", "Hiken", "Enjomo", "Rokushiki", "Soru", "Geppo", "Tekkai",
        "Shigan", "Rankyaku", "Kami-e", "Rokuogan", "Fishman Karate", "Shambles", "Gamma Knife", "Wado Ichimonji", "Sandai Kitetsu",
        "Kitetsu", "Shusui", "Enma", "Yoru", "Murakumogiri", "Kabuto", "Clima-Tact",
        // Objets, organisations, titres
        "One Piece", "Poneglyph", "Poneglyphs", "Road Poneglyph", "Rio Poneglyph", "Den Den Mushi", "Log Pose", "Eternal Pose", "Vivre Card",
        "Sea Prism Stone", "Kairoseki", "Pluton", "Poseidon", "Uranus", "Shichibukai", "Yonko", "Yonkou", "Tenryuubito", "Tenryubito",
        "Baroque Works", "Germa 66", "Vinsmoke", "Kozuki", "Donquixote", "Charlotte", "Minks", "Kuja Pirates", "Heart Pirates",
        "Kid Pirates", "Beasts Pirates", "Tobiroppo", "Kozuki clan", "Mugiwara", "Mugiwara no Luffy"
    ))

    val NARUTO = Fandom("naruto", "Naruto", listOf(
        // Personnages
        "Uzumaki Naruto", "Naruto Uzumaki", "Naruto", "Uchiha Sasuke", "Sasuke Uchiha", "Sasuke", "Haruno Sakura", "Sakura Haruno", "Sakura",
        "Hatake Kakashi", "Kakashi Hatake", "Kakashi", "Hatake Sakumo", "Sakumo", "Uchiha Itachi", "Itachi Uchiha", "Itachi", "Uchiha Madara",
        "Madara Uchiha", "Madara", "Uchiha Obito", "Obito Uchiha", "Obito", "Uchiha Shisui", "Shisui", "Uchiha Fugaku", "Fugaku", "Uchiha Mikoto",
        "Mikoto", "Uchiha Izuna", "Izuna", "Uchiha Sarada", "Sarada", "Hyuga Hinata", "Hyuuga Hinata", "Hinata Hyuga", "Hinata Hyuuga", "Hinata",
        "Hyuga Neji", "Hyuuga Neji", "Neji", "Hiashi", "Hizashi", "Hanabi", "Rock Lee", "Lee", "Might Guy", "Maito Gai", "Might Gai", "Gai", "Guy",
        "Might Duy", "Tenten", "Nara Shikamaru", "Shikamaru Nara", "Shikamaru", "Shikaku", "Akimichi Choji", "Akimichi Chouji", "Choji", "Chouji",
        "Choza", "Yamanaka Ino", "Ino Yamanaka", "Ino", "Inoichi", "Inuzuka Kiba", "Kiba", "Akamaru", "Tsume", "Hana Inuzuka", "Aburame Shino",
        "Shino", "Shibi", "Yuhi Kurenai", "Kurenai", "Sarutobi Asuma", "Asuma", "Sarutobi Hiruzen", "Hiruzen", "Sarutobi", "Konohamaru",
        "Jiraiya", "Tsunade", "Orochimaru", "Kabuto", "Yakushi Kabuto", "Shizune", "Tonton", "Gaara", "Temari", "Kankuro", "Kankurou", "Baki",
        "Chiyo", "Rasa", "Sai", "Yamato", "Tenzo", "Tenzou", "Namikaze Minato", "Minato Namikaze", "Minato", "Uzumaki Kushina", "Kushina",
        "Senju Hashirama", "Hashirama Senju", "Hashirama", "Senju Tobirama", "Tobirama Senju", "Tobirama", "Senju Nawaki", "Nawaki", "Dan Kato",
        "Shimura Danzo", "Danzo", "Danzou", "Umino Iruka", "Iruka", "Mizuki", "Ebisu", "Mitarashi Anko", "Anko", "Morino Ibiki", "Ibiki",
        "Genma", "Shiranui Genma", "Raido", "Izumo", "Kotetsu", "Hayate", "Yugao", "Homura", "Koharu", "Teuchi", "Ayame", "Tazuna", "Inari",
        "Momochi Zabuza", "Zabuza", "Haku", "Hoshigaki Kisame", "Kisame", "Deidara", "Sasori", "Hidan", "Kakuzu", "Nagato", "Pein", "Konan",
        "Yahiko", "Zetsu", "Black Zetsu", "White Zetsu", "Tobi", "Otsutsuki Kaguya", "Kaguya", "Otsutsuki Hagoromo", "Hagoromo", "Hamura",
        "Indra", "Asura Otsutsuki", "Killer Bee", "Killer B", "Darui", "Mei Terumi", "Terumi Mei", "Onoki", "Oonoki", "Kurotsuchi", "Akatsuchi",
        "Chojuro", "Ao", "Yagura", "Utakata", "Fu", "Roshi", "Han", "Yugito", "Yugito Nii", "Kurama", "Kyuubi", "Kyubi", "Kyuubi no Kitsune",
        "Shukaku", "Matatabi", "Isobu", "Son Goku", "Kokuo", "Saiken", "Chomei", "Gyuki", "Juubi", "Jubi", "Karin", "Uzumaki Karin", "Hozuki Suigetsu",
        "Suigetsu", "Jugo", "Juugo", "Kimimaro", "Tayuya", "Kidomaru", "Sakon", "Ukon", "Jirobo", "Kabuto", "Hanzo", "Hanzou", "Nohara Rin", "Rin",
        "Hatake", "Uchiha", "Uzumaki", "Senju", "Hyuga", "Hyuuga", "Nara", "Akimichi", "Yamanaka", "Inuzuka", "Aburame", "Namikaze", "Kurama clan",
        "Boruto", "Uzumaki Boruto", "Himawari", "Mitsuki", "Kawaki", "Shikadai", "Inojin", "Chocho", "Metal Lee", "Isshiki", "Momoshiki", "Kinshiki",
        "Gamabunta", "Gamakichi", "Gamatatsu", "Fukasaku", "Shima", "Manda", "Katsuyu", "Enma", "Pakkun", "Iruka-sensei",
        // Lieux
        "Konoha", "Konohagakure", "Konohagakure no Sato", "Sunagakure", "Sunagakure no Sato", "Suna", "Kirigakure", "Kiri", "Kumogakure", "Kumo",
        "Iwagakure", "Iwa", "Amegakure", "Ame", "Otogakure", "Oto", "Takigakure", "Taki", "Kusagakure", "Kusa", "Uzushiogakure", "Uzushio",
        "Hoshigakure", "Yugakure", "Myoboku", "Mount Myoboku", "Myobokuzan", "Ryuchi Cave", "Shikkotsu Forest", "Hi no Kuni", "Kaze no Kuni",
        "Mizu no Kuni", "Kaminari no Kuni", "Tsuchi no Kuni", "Nami no Kuni", "Ichiraku", "Ichiraku Ramen", "Shusha",
        // Titres, rangs, organisations
        "Hokage", "Yondaime", "Sandaime", "Nidaime", "Shodaime", "Godaime", "Rokudaime", "Nanadaime", "Kazekage", "Mizukage", "Raikage",
        "Tsuchikage", "Kage", "Sannin", "Densetsu no Sannin", "Jinchuriki", "Jinchuuriki", "Bijuu", "Biju", "Bijudama", "ANBU", "Anbu", "Akatsuki",
        "Kumo nin", "Hunter-nin", "Oinin", "Hyuga clan", "Uchiha clan", "Senju clan", "Uzumaki clan", "Sharingan no Kakashi",
        "genin", "chunin", "chuunin", "jonin", "jounin", "tokubetsu jonin", "tokubetsu jounin", "shinobi", "kunoichi", "ninja", "missing-nin",
        "nukenin", "sensei", "shishou", "hitai-ate", "kunai", "shuriken", "senbon", "chakra", "jutsu", "ninjutsu", "taijutsu", "genjutsu",
        "fuinjutsu", "fuuinjutsu", "senjutsu", "kenjutsu", "kekkei genkai", "kekkei tota", "hiden", "dattebayo", "dattebane", 
        // Pouvoirs et techniques
        "Sharingan", "Mangekyo Sharingan", "Mangekyou Sharingan", "Mangekyo", "Mangekyou", "Eternal Mangekyo Sharingan", "Byakugan", "Rinnegan",
        "Rinne Sharingan", "Tenseigan", "Jogan", "Rasengan", "Oodama Rasengan", "Rasenshuriken", "Fuuton: Rasenshuriken", "Chidori", "Raikiri",
        "Kage Bunshin", "Kage Bunshin no Jutsu", "Tajuu Kage Bunshin no Jutsu", "Bunshin no Jutsu", "Henge", "Henge no Jutsu", "Kawarimi",
        "Kawarimi no Jutsu", "Oiroke no Jutsu", "Sexy no Jutsu", "Hiraishin", "Hiraishin no Jutsu", "Shiki Fujin", "Edo Tensei", "Kuchiyose",
        "Kuchiyose no Jutsu", "Amaterasu", "Tsukuyomi", "Infinite Tsukuyomi", "Mugen Tsukuyomi", "Susanoo", "Susano'o", "Izanagi", "Izanami",
        "Kamui", "Kotoamatsukami", "Shinra Tensei", "Chibaku Tensei", "Banshou Ten'in", "Gokakyu no Jutsu", "Katon", "Suiton", "Fuuton", "Futon",
        "Raiton", "Doton", "Mokuton", "Hyoton", "Shoton", "Yoton", "Ranton", "Jinton", "Katon: Gokakyu no Jutsu", "Kaiten", "Hakkeshou Kaiten",
        "Juken", "Jyuken", "Hakke Rokujuyon Sho", "Hachimon", "Hachimon Tonko", "Kagemane no Jutsu", "Shintenshin no Jutsu", "Baika no Jutsu",
        "Sabaku Kyu", "Sabaku Soso", "Kyubi Chakra Mode", "Kurama Mode", "Baryon Mode", "Sage Mode", "Senin Mode", "Sennin Mode", "Six Paths Sage Mode",
        "Chakra no Mesu", "Byakugo", "Byakugo no In", "Kubikiribocho", "Samehada", "Totsuka", "Yata Mirror", "Gunbai", "Hiraishin kunai",
        "Kirin", "Sozo Saisei", "Rashomon", "Chunin Exams", "Chuunin Exams"
    ))

    val FANDOMS = listOf(ONE_PIECE, NARUTO)

    // ---------- Vos noms ----------

    val userNames: List<String> get() = Prefs.getList("names_user")

    /** Ajoute un nom à votre liste ; renvoie le nom retenu, ou null s'il n'y a rien à garder. */
    fun addUser(raw: String): String? {
        val n = clean(raw) ?: return null
        if (userNames.any { it == n }) return n
        Prefs.putList("names_user", (userNames + n).sortedBy { it.lowercase() })
        return n
    }

    fun removeUser(n: String) { Prefs.putList("names_user", userNames - n) }

    /** Enlève la ponctuation autour du mot sélectionné (« Usopp! » → « Usopp », « Luffy's » → « Luffy »). */
    fun clean(raw: String): String? {
        var s = raw.replace(Regex("\\s+"), " ").trim()
        s = s.trim { !it.isLetterOrDigit() && it != '\'' && it != '’' && it != '.' }
        s = s.replace(Regex("['’]s$"), "").trimEnd('.', '\'', '’').trim()
        if (s.isEmpty() || s.length > 60 || !s.any { it.isLetter() }) return null
        return s
    }

    /** Petit texte pour une sélection : un nom fait au plus quelques mots. */
    fun looksLikeName(sel: String): Boolean {
        val s = clean(sel) ?: return false
        return s.split(' ').size <= 5
    }

    // ---------- Protection ----------

    private val SUFFIX = "(?:-(?:san|sama|kun|chan|sensei|senpai|sempai|dono|tan|chi|nii|nee|nii-san|nee-san|niisan|neesan|nii-chan|nee-chan|" +
        "aniki|nee-sama|nii-sama|hime|shishou|shisho|baa-chan|jiji|jii-san|taichou|taicho|senchou|sencho))?"

    /** Liste active, pour savoir si les traductions déjà faites sont encore valables. */
    fun active(): List<String> {
        if (!enabled) return emptyList()
        val all = LinkedHashSet<String>()
        FANDOMS.filter { it.enabled }.forEach { all += it.names }
        all += userNames
        return all.toList()
    }

    /** Signature de la liste active : change dès qu'un nom ou un fandom est ajouté ou retiré. */
    fun signature(): String {
        val v = Prefs.version.value
        sigCache.let { if (it.first == v) return it.second }
        val sig = active().let { if (it.isEmpty()) "0" else it.hashCode().toString(36) + "." + it.size }
        sigCache = v to sig
        return sig
    }

    @Volatile private var sigCache: Pair<Int, String> = -1 to ""

    @Volatile private var compiled: Pair<String, Regex?> = "" to null

    private fun regex(): Regex? {
        val sig = signature()
        compiled.let { if (it.first == sig) return it.second }
        val list = active()
        val rx = if (list.isEmpty()) null else {
            val alts = list.flatMap { n ->
                val c = n.first()
                if (c.isLowerCase()) listOf(n, c.uppercaseChar() + n.substring(1)) else listOf(n)
            }.distinct().sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
            Regex("(?<![\\p{L}\\p{N}])(?:$alts)$SUFFIX(?![\\p{L}\\p{N}])")
        }
        compiled = sig to rx
        return rx
    }

    /** Texte prêt pour le traducteur : les noms sont remplacés par des numéros. */
    class Protected(val text: String, val names: List<String>, private val prefix: Char) {
        val isEmpty get() = names.isEmpty()

        /** Remet les noms ; null si le traducteur a perdu un numéro (on traduit alors le texte tel quel). */
        fun restore(translated: String): String? {
            if (names.isEmpty()) return translated
            val seen = BooleanArray(names.size)
            val out = tokenRx(prefix).replace(translated) { m ->
                val i = m.groupValues[1].toInt()
                if (i < names.size) { seen[i] = true; names[i] } else m.value
            }
            return if (seen.all { it }) out else null
        }
    }

    private fun tokenRx(p: Char) = Regex("(?<!\\d)$p[ \\u00A0\\u202F.,]?1(\\d\\d)(?!\\d)")

    private fun token(p: Char, i: Int) = "$p" + "1" + i.toString().padStart(2, '0')

    fun protect(s: String): Protected {
        val rx = regex() ?: return Protected(s, emptyList(), '9')
        if (!rx.containsMatchIn(s)) return Protected(s, emptyList(), '9')
        // Un préfixe de numéro qui n'apparaît pas déjà dans le texte.
        val p = "98765432".firstOrNull { !tokenRx(it).containsMatchIn(s) } ?: return Protected(s, emptyList(), '9')
        val names = ArrayList<String>()
        val text = rx.replace(s) { m ->
            if (names.size >= 99 && m.value !in names) return@replace m.value
            var i = names.indexOf(m.value)
            if (i < 0) { names += m.value; i = names.size - 1 }
            token(p, i)
        }
        return Protected(text, names, p)
    }
}
