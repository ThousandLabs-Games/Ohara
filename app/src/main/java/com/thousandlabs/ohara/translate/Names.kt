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

    /**
     * names : gardés tels quels. terms : termes remplacés par leur équivalent officiel en français
     * (« Devil Fruit » → « Fruit du démon »), au lieu d'être traduits mot à mot.
     * patterns : familles de noms reconnues par leur forme (« … no Mi », « Gomu Gomu no … », « … no Jutsu »).
     */
    class Fandom(val key: String, val label: String, val names: List<String>,
                 val terms: Map<String, String> = emptyMap(), val patterns: List<String> = emptyList()) {
        var enabled: Boolean
            get() = Prefs.getBool("names_$key", true)
            set(v) { Prefs.putBool("names_$key", v) }
    }

    /** Version du traitement : la changer refait les traductions gardées. */
    private const val VERSION = "t3"

    /** Termes officiels en français (Fruit du démon, Grand Corsaire, Barbe Blanche…). */
    var termsEnabled: Boolean
        get() = Prefs.getBool("names_terms", true)
        set(v) { Prefs.putBool("names_terms", v) }

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
        "Kid Pirates", "Beasts Pirates", "Tobiroppo", "Kozuki clan", "Mugiwara", "Mugiwara no Luffy",
        // Famille Vinsmoke, Baratie
        "Vinsmoke Judge", "Vinsmoke Reiju", "Reiju", "Vinsmoke Ichiji", "Ichiji", "Vinsmoke Niji", "Niji", "Vinsmoke Yonji", "Yonji",
        "Vinsmoke Sora", "Patty", "Carne", "Germa",
        // East Blue, enfance de Luffy
        "Gaimon", "Boodle", "Chouchou", "Mohji", "Richie", "Cabaji", "Gin", "Pearl", "Klahadore", "Ninjin", "Tamanegi", "Piiman",
        "Sham", "Buchi", "Kuroobi", "Hachi", "Curly Dadan", "Dogra", "Magra", "Sterry", "Outlook III", "Porchemy", "Bluejam", "Higuma",
        "Woop Slap", "Kuina", "Koshiro", "Portgas D. Rouge", "Rouge",
        // Grand Line, première moitié
        "Nefertari Cobra", "Cobra", "Igaram", "Chaka", "Pell", "Kohza", "Koza", "Toto", "Terracotta", "Miss Wednesday", "Miss Friday", "Mr. 9",
        "Mr. 7", "Dorry", "Brogy", "Chess", "Kuromarimo", "Hiriluk", "Dr. Hiriluk", "Aisa", "Laki", "Kamakiri", "Mont Blanc Noland", "Noland",
        "Mont Blanc Cricket", "Cricket", "Masira", "Shoujou", "Satori", "Shura", "Gedatsu", "Ohm", "Kalgara", "Calgara", "Bellamy", "Sarquiss",
        "Kokoro", "Chimney", "Gonbe", "Zambai", "Kiwi", "Mozu", "Peepley Lulu", "Tilestone", "Franky Family", "Square Sisters", "Hogback",
        "Victoria Cindry", "Cindry", "Oars", "Lola", "Shimotsuki Ryuma", "Ryuma", "Nico Olvia", "Olvia", "Professor Clover", "Clover",
        "Jaguar D. Saul", "Saul", "Duval", "Disco", "Saint Roswald", "Saint Shalria", "Shalria", "Saint Mjosgard", "Mjosgard",
        "Boa Sandersonia", "Sandersonia", "Boa Marigold", "Gloriosa", "Nyon", "Marguerite", "Salome", "Shiliew", "Domino", "Saldeath", "Sadi",
        "Minotaurus", "Fisher Tiger", "Neptune", "Ryuboshi", "Manboshi", "Vander Decken", "Decken", "Keimi", "Madam Shyarly", "Shyarly",
        "Wadatsumi", "Hammond", "Megalo",
        // Barbe Blanche, Barbe Noire, le Roux, Roger, Rocks
        "Jozu", "Atmos", "Squard", "Curiel", "Haruta", "Blamenco", "Rakuyo", "Namule", "Fossa", "Kingdew", "Edward Weevil", "Weevil",
        "Doc Q", "Avalo Pizarro", "Pizarro", "San Juan Wolf", "Sanjuan Wolf", "Limejuice", "Building Snake", "Hongo", "Bonk Punch",
        "Howling Gab", "Uta", "Shiki", "Douglas Bullet", "Wang Zhi", "Captain John", "Stussy", "Killer", "Jean Bart", "Shachi",
        // Nouveau Monde : Punk Hazard, Dressrosa, Zou, Whole Cake
        "Brownbeard", "Smiley", "Mocha", "Riku Dold III", "Leo", "Mansherry", "Gancho", "Sugar", "Trebol", "Diamante", "Pica", "Gladius",
        "Machvise", "Senor Pink", "Señor Pink", "Dellinger", "Lao G", "Baby 5", "Buffalo", "Hajrudin", "Ideo", "Orlumbus", "Elizabello",
        "Dagama", "Chinjao", "Don Chinjao", "Kelly Funk", "Bobby Funk", "Jora", "Violet", "Wanda", "Carrot", "Shishilian", "Bariete",
        "Charlotte Perospero", "Perospero", "Charlotte Smoothie", "Charlotte Oven", "Charlotte Daifuku", "Daifuku", "Charlotte Brulee", "Brulee",
        "Charlotte Galette", "Charlotte Chiffon", "Charlotte Lola", "Charlotte Praline", "Charlotte Amande", "Charlotte Mont-d'Or",
        "Charlotte Opera", "Charlotte Flampe", "Tamago", "Bobbin", "Streusen", "Mother Carmel", "Capone", "Vito", "Gotti",
        // Wano
        "Kurozumi Orochi", "Orochi", "Kurozumi Kanjuro", "Kurozumi Higurashi", "Kurozumi Semimaru", "Who's-Who", "Black Maria", "Sasaki",
        "Ulti", "Page One", "Holdem", "Hyogoro", "Kawamatsu", "Shinobu", "Tama", "Otama", "Toko", "Komurasaki", "Kozuki Toki", "Toki",
        "Kozuki Sukiyaki", "Shimotsuki Kozaburo", "Shimotsuki Ushimaru", "Shimotsuki Yasuie", "Yasuie", "Zunesha", "Zunisha", "Kyoshiro",
        "Onimaru", "Kawamatsu", "Okiku",
        // Egghead, Elbaf, Gouvernement, Marine
        "Lilith", "Shaka", "Pythagoras", "Edison", "Atlas", "Seraphim", "Pacifista", "S-Snake", "S-Hawk", "S-Bear", "S-Shark",
        "Sentomaru", "Topman Warcury", "Warcury", "Marcus Mars", "Ethanbaron V. Nusjuro", "Nusjuro", "Shepherd Ju Peter", "Ju Peter",
        "Garling", "Figarland Shamrock", "Gunko", "Killingham", "Sommers", "Rimoshifu", "Loki", "Harald", "Jarul", "Jorul", "Gerd", "Ida",
        "Oimo", "Kashii", "Bogard", "Momonga", "Onigumo", "Doberman", "Yamakaji", "Comil", "Kujaku", "Prince Grus", "Grus", "Hibari",
        "Tokikake", "Gion", "Zephyr", "Ain", "Binz", "Stainless", "Brannew", "T-Bone", "Isuka", "Dalmatian", "Lacroix", "Mozambia",
        "Wanze", "Nero", "Hattori", "Hack", "Bunny Joe", "Ahiru", "Pandaman",
        // Lieux et navires
        "Gecko Islands", "Conomi Islands", "Organ Islands", "Cactus Island", "Whiskey Peak", "Sandy Island", "Banaro Island", "Pucci",
        "St. Poplar", "San Faldo", "Tottoland", "Cacao Island", "Foodvalten", "Lodestar Island", "Pangaea Castle", "Red Port", "Wa no Kuni",
        "Ebisu Town", "Okobore Town", "Bakura Town", "Amigasa Village", "Kuraigana Island", "Kenzan", "Gyoverly Hills", "Coral Hill",
        "Prodence Kingdom", "Kano Country", "Germa Kingdom", "Sorbet Kingdom", "Ilusia Kingdom", "Future Island", "Labophase", "Warland",
        "Treasure Tree Adam", "Yggdrasil", "Victoria Punk", "Queen Mama Chanter", "Perfume Yuda", "Ark Maxim", "Mini Merry", "Shark Submerge",
        "Noah", "Sunny", "Davy Back Fight",
        // Fruits du démon (forme courte comprise)
        "Hito Hito no Mi, Model: Nika", "Gura Gura", "Yami Yami", "Pika Pika", "Hie Hie", "Magu Magu", "Ope Ope", "Ito Ito", "Goro Goro",
        "Suna Suna", "Hana Hana", "Bara Bara", "Mochi Mochi", "Yomi Yomi", "Moku Moku", "Hobi Hobi", "Nikyu Nikyu", "Mero Mero", "Doku Doku",
        "Horo Horo", "Kage Kage", "Toshi Toshi", "Soru Soru", "Bari Bari", "Ori Ori", "Gasu Gasu", "Numa Numa", "Yuki Yuki", "Mane Mane",
        "Doru Doru", "Sube Sube", "Bomu Bomu", "Supa Supa", "Jiki Jiki", "Hiso Hiso", "Ishi Ishi", "Uta Uta", "Nagi Nagi", "Zushi Zushi",
        "Tori Tori", "Inu Inu", "Ryu Ryu", "Hebi Hebi", "Neko Neko", "Zou Zou", "Model: Nika", "Model: Phoenix", "Model: Azure Dragon",
        // Techniques
        "Santoryu Ogi", "Santoryu Ougi", "Mutoryu", "Tatsumaki", "Rengoku Onigiri", "Ashura", "Ichidai Sanzen Daisen Sekai", "King of Hell",
        "Enma Kitetsu", "Wado", "Collier Shoot", "Poitrine Shoot", "Mouton Shot", "Party Table Kick Course", "Hell Memories", "Sky Walk",
        "Anti-Manner Kick Course", "Veau Shot", "Hiken no Ace", "Dai Enkai", "Entei", "Kamusari", "Kamuzari", "Haoshoku Haki",
        "Ryusoken",
        // Mots japonais courants dans les fics
        "nakama", "Oyaji", "Ossan", "Jiji", "Kaizoku Ou", "Mugiwara no Ichimi", "Mugiwara-ya", "Mellorine"
    ), terms = mapOf(
        // Équipages et surnoms : leur nom officiel en français
        "Straw Hat Pirates" to "Pirates du Chapeau de paille", "Straw Hat crew" to "équipage du Chapeau de paille",
        "Straw Hat Crew" to "équipage du Chapeau de paille", "Straw Hat Grand Fleet" to "Grande Flotte du Chapeau de paille",
        "Straw Hats" to "Chapeaux de paille", "Straw Hat Luffy" to "Luffy au Chapeau de paille", "Straw Hat" to "Chapeau de paille",
        "Whitebeard Pirates" to "Pirates de Barbe Blanche", "Blackbeard Pirates" to "Pirates de Barbe Noire",
        "Red Hair Pirates" to "Pirates du Roux", "Red-Haired Pirates" to "Pirates du Roux",
        "Whitebeard" to "Barbe Blanche", "Blackbeard" to "Barbe Noire",
        "Red-Haired Shanks" to "Shanks le Roux", "Red Hair Shanks" to "Shanks le Roux",
        "Fire Fist Ace" to "Ace aux poings ardents", "Fire Fist" to "Poings ardents",
        "Pirate Hunter Zoro" to "Zoro le chasseur de pirates", "Black Leg Sanji" to "Sanji la Jambe noire",
        "Cat Burglar Nami" to "Nami la Chatte voleuse", "Hawk-Eye Mihawk" to "Mihawk Œil de faucon", "Hawkeye Mihawk" to "Mihawk Œil de faucon",
        "Hawk-Eyes Mihawk" to "Mihawk Œil de faucon", "Surgeon of Death" to "Chirurgien de la mort",
        // Titres et institutions
        "King of the Pirates" to "Roi des pirates", "Pirate King" to "Roi des pirates",
        "Seven Warlords of the Sea" to "sept Grands Corsaires", "Warlords of the Sea" to "Grands Corsaires", "Warlord of the Sea" to "Grand Corsaire",
        "Four Emperors" to "quatre Empereurs",
        "Fleet Admiral" to "Amiral en chef", "Vice Admiral" to "Vice-amiral", "Vice-Admiral" to "Vice-amiral", "Rear Admiral" to "Contre-amiral",
        "World Government" to "Gouvernement mondial", "Celestial Dragons" to "Dragons célestes", "Celestial Dragon" to "Dragon céleste",
        "World Nobles" to "Nobles mondiaux", "World Noble" to "Noble mondial", "Five Elders" to "Cinq Doyens",
        "Revolutionary Army" to "Armée révolutionnaire", "Worst Generation" to "Pire Génération", "New World" to "Nouveau Monde",
        // Pouvoirs et objets
        "Devil Fruits" to "Fruits du démon", "Devil Fruit" to "Fruit du démon", "devil fruits" to "fruits du démon", "devil fruit" to "fruit du démon",
        "Devil Fruit user" to "utilisateur de Fruit du démon", "Devil Fruit users" to "utilisateurs de Fruit du démon",
        "Conqueror's Haki" to "Haki des rois", "Conqueror’s Haki" to "Haki des rois", "Armament Haki" to "Haki de l'armement",
        "Observation Haki" to "Haki de l'observation", "Sea Prism Stone" to "granit marin", "Seastone" to "granit marin", "seastone" to "granit marin",
        "Sea Kings" to "Rois des mers", "Sea King" to "Roi des mers", "Void Century" to "Siècle oublié", "Ancient Weapons" to "armes antiques",
        "Ancient Weapon" to "arme antique", "Will of D." to "volonté du D.", "Fish-Men" to "Hommes-poissons", "Fishmen" to "Hommes-poissons",
        "Fish-Man" to "Homme-poisson", "Fishman" to "Homme-poisson", "Mermaid" to "Sirène", "Mermaids" to "Sirènes"
    ), patterns = listOf(
        "Gomu Gomu no(?: [A-Z][A-Za-z'-]+)+",
        "[A-Z][a-z]+ [A-Z][a-z]+ no Mi(?:,? Model: [A-Z][A-Za-z'-]*(?: [A-Z][A-Za-z'-]*)*)?",
        "(?:Santoryu|Nitoryu|Ittoryu|Kyutoryu|Mutoryu)(?: Ogi| Ougi)?: (?:[A-Z][A-Za-z'-]+)(?: [A-Z][A-Za-z'-]+)*"
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
        "Kirin", "Sozo Saisei", "Rashomon",
        "Ero-sennin", "Baa-chan", "Tsunade-baachan", "Hokage-sama", "Sandaime Hokage", "Yondaime Hokage", "Ichibi", "Nibi", "Sanbi",
        "Yonbi", "Gobi", "Rokubi", "Nanabi", "Hachibi", "Kyuubi Chakra", "Root", "Ne", "Anbu Ne", "Team 7", "Team Seven", "Team Kakashi",
        "Team Minato", "Team Gai", "Team Guy", "Team 8", "Team 10", "Hebi", "Taka", "Rikudo Sennin", "Rikudou Sennin", "Ninshu", "Ninshuu"
    ), terms = mapOf(
        "Hidden Leaf Village" to "village caché de Konoha", "Village Hidden in the Leaves" to "village caché de Konoha",
        "Hidden Leaf" to "Konoha", "Leaf Village" to "village de Konoha", "Hidden Sand Village" to "village caché de Suna",
        "Hidden Mist Village" to "village caché de Kiri", "Hidden Cloud Village" to "village caché de Kumo",
        "Hidden Stone Village" to "village caché d'Iwa", "Hidden Rain Village" to "village caché d'Ame", "Hidden Sound Village" to "village caché d'Oto",
        "Nine-Tailed Fox" to "Renard à neuf queues", "Nine-Tails" to "Kyûbi", "Nine Tails" to "Kyûbi",
        "Tailed Beasts" to "démons à queues", "Tailed Beast" to "démon à queue", "Will of Fire" to "Volonté du Feu",
        "Sage of the Six Paths" to "Ermite Rikudô", "Sage of Six Paths" to "Ermite Rikudô", "Sage Mode" to "Mode Ermite",
        "Shadow Clone Jutsu" to "Multiclonage", "Copy Ninja" to "Ninja copieur", "Pervy Sage" to "Ermite pas net",
        "Curse Mark" to "sceau maudit", "Cursed Seal" to "sceau maudit", "Eight Gates" to "huit Portes",
        "Chunin Exams" to "examens chûnin", "Chuunin Exams" to "examens chûnin", "Chunin Exam" to "examen chûnin", "Chuunin Exam" to "examen chûnin"
    ), patterns = listOf(
        "(?:[A-Z][a-z']+:? )+no Jutsu"
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
        "aniki|nee-sama|nii-sama|hime|shishou|shisho|baa-chan|jiji|jii-san|taichou|taicho|senchou|sencho|ya|swan|chwan|bro|aniki|kouhai|hakase))?"

    /** Liste active, pour savoir si les traductions déjà faites sont encore valables. */
    fun active(): List<String> {
        if (!enabled) return emptyList()
        val all = LinkedHashSet<String>()
        FANDOMS.filter { it.enabled }.forEach { all += it.names }
        all += userNames
        return all.toList()
    }

    /** Termes remplacés par leur équivalent français (vide si l'option est coupée). */
    fun activeTerms(): Map<String, String> {
        if (!enabled || !termsEnabled) return emptyMap()
        val m = LinkedHashMap<String, String>()
        FANDOMS.filter { it.enabled }.forEach { m.putAll(it.terms) }
        return m
    }

    private fun activePatterns(): List<String> = if (!enabled) emptyList() else FANDOMS.filter { it.enabled }.flatMap { it.patterns }

    /** Signature de la liste active : change dès qu'un nom ou un fandom est ajouté ou retiré. */
    fun signature(): String {
        val v = Prefs.version.value
        sigCache.let { if (it.first == v) return it.second }
        val a = active(); val t = activeTerms(); val pt = activePatterns()
        val sig = VERSION + "." + if (a.isEmpty() && t.isEmpty()) "0" else (a.hashCode() * 31 + t.hashCode() * 7 + pt.hashCode()).toString(36) + "." + (a.size + t.size)
        sigCache = v to sig
        return sig
    }

    @Volatile private var sigCache: Pair<Int, String> = -1 to ""

    private class Compiled(val rx: Regex?, val terms: Map<String, String>, val ci: Regex?, val canon: Map<String, String>)

    @Volatile private var compiled: Pair<String, Compiled> = "" to Compiled(null, emptyMap(), null, emptyMap())

    private fun withCap(n: String): List<String> {
        val c = n.first()
        return if (c.isLowerCase()) listOf(n, c.uppercaseChar() + n.substring(1)) else listOf(n)
    }

    private fun compiled(): Compiled {
        val sig = signature()
        compiled.let { if (it.first == sig) return it.second }
        val terms = activeTerms()
        val literals = (active() + terms.keys).flatMap { withCap(it) }.distinct().sortedByDescending { it.length }
        val pats = activePatterns()
        val alts = (literals.map { Regex.escape(it) } + pats.map { "(?:$it)" })
        val rx = if (alts.isEmpty()) null else Regex("(?<![\\p{L}\\p{N}])(" + alts.joinToString("|") + ")($SUFFIX)(?![\\p{L}\\p{N}])")
        // Pour remettre les majuscules d'une phrase criée (« LUFFY ! » → « Luffy ! » avant traduction).
        val canon = HashMap<String, String>()
        literals.forEach { n -> val k = n.lowercase(); val old = canon[k]; if (old == null || (n.first().isUpperCase() && !old.first().isUpperCase())) canon[k] = n }
        val ci = if (literals.isEmpty()) null else Regex("(?<![\\p{L}\\p{N}])(?:" + literals.joinToString("|") { Regex.escape(it) } + ")(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        val c = Compiled(rx, terms, ci, canon)
        compiled = sig to c
        return c
    }

    /** Remet les majuscules des noms connus dans un texte passé en minuscules. */
    fun recase(s: String): String {
        val c = compiled()
        val ci = c.ci ?: return s
        return ci.replace(s) { m -> c.canon[m.value.lowercase()] ?: m.value }
    }

    /** Texte prêt pour le traducteur : les noms sont remplacés par des numéros. */
    class Protected(val text: String, private val out: List<String>, private val prefix: Char, private val word: Boolean = false) {
        val isEmpty get() = out.isEmpty()

        /** Remet les noms ; null si le traducteur a perdu un numéro (on traduit alors le texte tel quel). */
        fun restore(translated: String): String? {
            if (out.isEmpty()) return translated
            val seen = BooleanArray(out.size)
            val res = (if (word) WORD_RX else tokenRx(prefix)).replace(translated) { m ->
                val i = if (word) wordIndex(m.groupValues[1]) else m.groupValues[1].toInt()
                if (i < out.size) { seen[i] = true; out[i] } else m.value
            }
            return if (seen.all { it }) res else null
        }

        /** Le texte sans les numéros : reste-t-il quelque chose à traduire ? */
        fun bare(): String = (if (word) WORD_RX else tokenRx(prefix)).replace(text, " ")

        /** Remet les noms sans rien traduire (paragraphe fait seulement de noms). */
        fun fill(): String = restore(text) ?: text
    }

    private fun tokenRx(p: Char) = Regex("(?<!\\d)$p[ \\u00A0\\u202F.,]?1(\\d\\d)(?!\\d)")

    private fun token(p: Char, i: Int) = "$p" + "1" + i.toString().padStart(2, '0')

    /*
     * Repères en forme de nom inventé (« Zqab », « Zqac »…) pour la traduction en ligne : un numéro y était pris pour
     * une quantité (« Luffy sourit » devenait « 9100 personnes sourient », puis « Luffy personnes »).
     * Un nom inconnu, lui, est recopié tel quel, et le reste de la phrase s'accorde comme avec un prénom.
     */
    private val WORD_RX = Regex("(?<![\\p{L}\\d])[Zz][Qq]([a-zA-Z]{2})(?![\\p{L}\\d])")
    private fun wordToken(i: Int) = "Zq" + ('a' + i / 26) + ('a' + i % 26)
    private fun wordIndex(s: String): Int { val l = s.lowercase(); return (l[0] - 'a') * 26 + (l[1] - 'a') }

    fun protect(s: String, word: Boolean = false): Protected {
        val c = compiled()
        val rx = c.rx ?: return Protected(s, emptyList(), '9')
        if (!rx.containsMatchIn(s)) return Protected(s, emptyList(), '9')
        if (word && WORD_RX.containsMatchIn(s)) return protect(s, false)
        // Un préfixe de numéro qui n'apparaît pas déjà dans le texte.
        val p = if (word) 'z' else "98765432".firstOrNull { !tokenRx(it).containsMatchIn(s) } ?: return Protected(s, emptyList(), '9')
        val keys = ArrayList<String>()
        val out = ArrayList<String>()
        val text = rx.replace(s) { m ->
            if (keys.size >= (if (word) 600 else 99) && m.value !in keys) return@replace m.value
            var i = keys.indexOf(m.value)
            if (i < 0) {
                val base = m.groupValues[1]; val suffix = m.groupValues[2]
                val fr = c.terms[base] ?: c.terms[base.replaceFirstChar { it.lowercaseChar() }]
                    ?.let { t -> if (base.first().isUpperCase()) t.replaceFirstChar { it.uppercaseChar() } else t }
                keys += m.value; out += (fr ?: base) + suffix; i = keys.size - 1
            }
            if (word) wordToken(i) else token(p, i)
        }
        return Protected(text, out, p, word)
    }
}
