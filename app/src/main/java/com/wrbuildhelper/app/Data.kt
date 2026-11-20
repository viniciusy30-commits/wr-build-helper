package com.wrbuildhelper.app

// Gerado a partir do HTML original (patch 7.3f). Dados de campeões, itens e dicas.
object Data {
    const val PATCH = "7.3f"
    const val PATCH_DATE = "11/11/2026"
    const val RAW = "Aatrox:Fh,Ahri:M,Akali:P,Akshan:A,Alistar:Tc,Ambessa:F,Amumu:Tc,Annie:Mc,Ashe:Ac,Aurelion Sol:M,Aurora:M,Bard:Ec,Blitzcrank:Tc,Brand:M,Braum:Tc,Caitlyn:A,Camille:F,Cho’Gath:T,Corki:A,Darius:F,Diana:P,Dr. Mundo:Th,Draven:A,Ekko:P,Evelynn:P,Ezreal:A,Fiddlesticks:Mch,Fiora:Fh,Fizz:P,Galio:Tc,Garen:Fh,Gnar:Fc,Gragas:Fc,Graves:A,Gwen:Ph,Hecarim:Fc,Heimerdinger:M,Hwei:Mc,Irelia:F,Janna:Ec,Jarvan IV:Fc,Jax:F,Jayce:F,Jhin:A,Jinx:A,K’Sante:T,Kai’Sa:O,Kalista:A,Karma:E,Kassadin:P,Katarina:P,Kayle:F,Kayn:F,Kennen:Mc,Kha’Zix:S,Kindred:A,Kog’Maw:O,Lee Sin:F,Leona:Tc,Lillia:P,Lissandra:Mc,Lucian:A,Lulu:Ec,Lux:Mc,Malphite:Tc,Maokai:Tc,Master Yi:Sh,Mel:M,Milio:E,Miss Fortune:A,Mordekaiser:F,Morgana:Mc,Nami:Ec,Nasus:Fh,Nautilus:Tc,Nidalee:Ph,Nilah:Fh,Nocturne:S,Norra:Mc,Nunu & Willump:Tc,Olaf:Fh,Orianna:Mc,Ornn:Tc,Pantheon:F,Poppy:Tc,Pyke:S,Rakan:Ec,Rammus:Tc,Rell:Tc,Renekton:Fh,Rengar:S,Riven:F,Rumble:M,Ryze:M,Samira:A,Senna:Ah,Seraphine:E,Sett:F,Shen:T,Shyvana:F,Singed:T,Sion:Tc,Sivir:A,Skarner:Tc,Smolder:A,Sona:Eh,Soraka:Eh,Swain:Mh,Sylas:Ph,Syndra:Mc,Taliyah:Mc,Talon:S,Teemo:M,Thresh:Tc,Tristana:A,Tryndamere:Fh,Twisted Fate:Mc,Twitch:A,Urgot:Fh,Varus:O,Vayne:O,Veigar:M,Vel’Koz:M,Vex:M,Vi:Fc,Viego:Fh,Viktor:M,Vladimir:Mh,Volibear:Fc,Warwick:Fh,Wukong:F,Xayah:A,Xin Zhao:F,Yasuo:F,Yone:F,Yunara:O,Yuumi:E,Zed:S,Zeri:A,Ziggs:M,Zilean:Ec,Zoe:M,Zyra:Mc"
    val ROLE: Map<Char, String> = linkedMapOf(
        'A' to "Atirador crit",
        'O' to "Atirador on-hit",
        'M' to "Mago",
        'S' to "Assassino",
        'P' to "Assassino AP",
        'F' to "Lutador",
        'T' to "Tanque",
        'E' to "Suporte"
    )
    val LANES = listOf("Topo", "Selva", "Meio", "Dragão", "Suporte")
    val DEFAULT_CLASS = listOf('F', 'F', 'M', 'A', 'E')
    val MOBILE: Set<String> = setOf(
        "Ahri",
        "Akali",
        "Akshan",
        "Ambessa",
        "Aurora",
        "Camille",
        "Corki",
        "Diana",
        "Ekko",
        "Evelynn",
        "Ezreal",
        "Fiora",
        "Fizz",
        "Galio",
        "Gnar",
        "Gragas",
        "Graves",
        "Gwen",
        "Hecarim",
        "Irelia",
        "Jarvan IV",
        "Jax",
        "Jayce",
        "Kai’Sa",
        "Kalista",
        "Kassadin",
        "Katarina",
        "Kayn",
        "Kha’Zix",
        "K’Sante",
        "Lee Sin",
        "Leona",
        "Alistar",
        "Lucian",
        "Master Yi",
        "Nidalee",
        "Nilah",
        "Nocturne",
        "Pantheon",
        "Poppy",
        "Pyke",
        "Rakan",
        "Renekton",
        "Rengar",
        "Riven",
        "Samira",
        "Shen",
        "Sylas",
        "Taliyah",
        "Talon",
        "Tristana",
        "Tryndamere",
        "Vayne",
        "Vi",
        "Viego",
        "Wukong",
        "Yasuo",
        "Yone",
        "Zed",
        "Zeri"
    )
    val PT: Map<String, String> = mapOf(
        "Berserker’s Greaves" to "Grevas do Berserker",
        "Sorcerer’s Shoes" to "Sapatos do Feiticeiro",
        "Ionian Boots of Lucidity" to "Botas da Lucidez Jônica",
        "Plated Steelcaps" to "Grevas Revestidas de Aço",
        "Mercury’s Treads" to "Botas de Mercúrio",
        "Yun Tal Wildarrows" to "Flechas Selvagens de Yun Tal",
        "Rapid Firecannon" to "Canhão de Disparo Rápido",
        "Infinity Edge" to "Gume do Infinito",
        "Bloodthirster" to "Sede de Sangue",
        "Kraken Slayer" to "Matador de Kraken",
        "Guinsoo’s Rageblade" to "Lâmina da Fúria de Guinsoo",
        "Blade of the Ruined King" to "Lâmina do Rei Arruinado",
        "Luden’s Echo" to "Eco de Luden",
        "Shadowflame" to "Chama Sombria",
        "Rabadon’s Deathcap" to "Chapéu da Morte de Rabadon",
        "Rod of Ages" to "Bastão das Eras",
        "Youmuu’s Ghostblade" to "Lâmina Fantasma de Youmuu",
        "Duskblade of Draktharr" to "Lâmina do Crepúsculo de Draktharr",
        "Edge of Night" to "Fio da Noite",
        "Serylda’s Grudge" to "Rancor de Serylda",
        "Lich Bane" to "Ruína do Lich",
        "Trinity Force" to "Força da Trindade",
        "Sterak’s Gage" to "Manopla de Sterak",
        "Death’s Dance" to "Dança da Morte",
        "Sunfire Aegis" to "Égide Solar",
        "Frozen Heart" to "Coração Congelado",
        "Dead Man’s Plate" to "Placa do Homem Morto",
        "Amaranth’s Twinguard" to "Guarda Gêmea de Amaranto",
        "Ardent Censer" to "Incensário Ardente",
        "Staff of Flowing Waters" to "Cajado das Águas Correntes",
        "Harmonic Echo" to "Eco Harmônico",
        "Shurelya’s Battlesong" to "Canção de Batalha de Shurelya",
        "Guardian Angel" to "Anjo Guardião",
        "Zhonya’s Hourglass" to "Ampulheta de Zhonya",
        "Randuin’s Omen" to "Presságio de Randuin",
        "Locket of Iron Solari" to "Medalhão de Ferro Solari",
        "Immortal Shieldbow" to "Arco-escudo Imortal",
        "Mantle of the Twelfth Hour" to "Manto da Décima Segunda Hora",
        "Mercurial Scimitar" to "Cimitarra Mercurial",
        "At Wit’s End" to "No Fim da Razão",
        "Banshee’s Veil" to "Véu da Banshee",
        "Maw of Malmortius" to "Bocarra de Malmortius",
        "Abyssal Mask" to "Máscara Abissal",
        "Lord Dominik’s Regards" to "Saudações de Lord Dominik",
        "Void Staff" to "Cajado do Vazio",
        "Black Cleaver" to "Cutelo Negro",
        "Mortal Reminder" to "Lembrete Mortal",
        "Executioner’s Calling" to "Chamado do Carrasco",
        "Thornmail" to "Cota de Espinhos"
    )
    val CORE: Map<Char, List<String>> = mapOf(
        'A' to listOf("Yun Tal Wildarrows", "Rapid Firecannon", "Infinity Edge", "Bloodthirster"),
        'O' to listOf("Kraken Slayer", "Guinsoo’s Rageblade", "Blade of the Ruined King", "Terminus"),
        'M' to listOf("Luden’s Echo", "Shadowflame", "Rabadon’s Deathcap", "Rod of Ages"),
        'S' to listOf("Youmuu’s Ghostblade", "Duskblade of Draktharr", "Edge of Night", "Serylda’s Grudge"),
        'P' to listOf("Luden’s Echo", "Shadowflame", "Rabadon’s Deathcap", "Lich Bane"),
        'F' to listOf("Trinity Force", "Eclipse", "Sterak’s Gage", "Death’s Dance"),
        'T' to listOf("Sunfire Aegis", "Frozen Heart", "Dead Man’s Plate", "Amaranth’s Twinguard"),
        'E' to listOf("Ardent Censer", "Staff of Flowing Waters", "Harmonic Echo", "Shurelya’s Battlesong")
    )
    val BOOT: Map<Char, String> = mapOf(
        'A' to "Berserker’s Greaves",
        'O' to "Berserker’s Greaves",
        'M' to "Sorcerer’s Shoes",
        'S' to "Ionian Boots of Lucidity",
        'P' to "Sorcerer’s Shoes",
        'F' to "Plated Steelcaps",
        'T' to "Plated Steelcaps",
        'E' to "Ionian Boots of Lucidity"
    )
    val ARM: Map<Char, String> = mapOf(
        'A' to "Guardian Angel",
        'O' to "Guardian Angel",
        'M' to "Zhonya’s Hourglass",
        'S' to "Guardian Angel",
        'P' to "Zhonya’s Hourglass",
        'F' to "Death’s Dance",
        'T' to "Randuin’s Omen",
        'E' to "Locket of Iron Solari"
    )
    val BURST: Map<Char, String> = mapOf(
        'A' to "Immortal Shieldbow",
        'O' to "Guardian Angel",
        'M' to "Zhonya’s Hourglass",
        'S' to "Guardian Angel",
        'P' to "Zhonya’s Hourglass",
        'F' to "Guardian Angel",
        'T' to "Mantle of the Twelfth Hour",
        'E' to "Locket of Iron Solari"
    )
    val MR: Map<Char, String> = mapOf(
        'A' to "Mercurial Scimitar",
        'O' to "At Wit’s End",
        'M' to "Banshee’s Veil",
        'S' to "Maw of Malmortius",
        'P' to "Banshee’s Veil",
        'F' to "Maw of Malmortius",
        'T' to "Abyssal Mask",
        'E' to "Banshee’s Veil"
    )
    val PEN: Map<Char, String> = mapOf(
        'A' to "Lord Dominik’s Regards",
        'O' to "Blade of the Ruined King",
        'M' to "Void Staff",
        'S' to "Serylda’s Grudge",
        'P' to "Void Staff",
        'F' to "Black Cleaver"
    )
    val HEAL: Map<Char, String> = mapOf(
        'A' to "Mortal Reminder",
        'O' to "Mortal Reminder",
        'S' to "Executioner’s Calling",
        'F' to "Executioner’s Calling",
        'M' to "Morellonomicon",
        'P' to "Morellonomicon",
        'E' to "Morellonomicon",
        'T' to "Thornmail"
    )
    val TIP: Map<Char, String> = mapOf(
        'A' to "Patch 7.3: crit base 200%. Infinity Edge é item final; Yun Tal escala crit com o tempo (Stormrazor se quiser poder cedo).",
        'O' to "On-hit: Rageblade não exige mais abrir mão de crit. Statikk Shiv é opção para teamfight.",
        'M' to "7.3f: Rod of Ages e Lich Bane buffados; Liandry’s perdeu AP. Imperial Mandate se for mago de CC.",
        'P' to "7.3f: Lich Bane e Luden’s Echo (só alvo único) favorecem burst. Void Staff contra tanques.",
        'S' to "Serylda’s agora dá 35% de penetração de armadura: ótimo contra frontline.",
        'F' to "7.3f: Eclipse nerfado e Goredrinker buffado (boa troca se precisar de sustain).",
        'T' to "Sunfire escala com vida; Frozen Heart é para tank que entra no meio do time inimigo.",
        'E' to "7.3: itens de suporte mudaram. Ardent Censer com atirador, Harmonic Echo para curas em cadeia."
    )
    val CT: Map<Char, String> = mapOf(
        'A' to "Fique atrás do seu tanque e faça kite; só entre na luta depois que o CC inimigo for usado.",
        'O' to "Fique atrás do seu tanque e faça kite; só entre na luta depois que o CC inimigo for usado.",
        'M' to "Mantenha distância, use o CC primeiro e guarde seu escape contra assassinos.",
        'S' to "Espere o CC inimigo ser gasto e mate o alvo mais frágil (atirador ou mago).",
        'P' to "Espere o CC inimigo ser gasto e mate o alvo mais frágil (atirador ou mago).",
        'F' to "Procure duelos laterais e entre depois do engage; não fique na frente sem um tanque.",
        'T' to "Seja o iniciador: engaje no carry inimigo e proteja o seu atirador.",
        'E' to "Proteja seu carry e guarde escudo/cura para o engage inimigo."
    )
    val LANE_TIPS = listOf(
        "Topo: empurre a wave antes de rotacionar para Arauto/Barão; a lane isolada é alvo fácil de gank, mantenha visão.",
        "Selva: acumule cargas do Smite (8 cargas = 1000 de dano, 20 = 1400) para garantir Dragão, Arauto e Barão.",
        "Meio: seja o primeiro a rotacionar (selva e Dragão), mas empurre a wave antes de sair.",
        "Dragão: fique atrás do suporte, farme com segurança e use o primeiro item para pressionar o Dragão.",
        "Suporte: dê visão ao seu selva e fique perto do atirador nas lutas."
    )
}
