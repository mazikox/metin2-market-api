package com.mazikox.metin_market_api.market.domain;

import com.mazikox.metin_market_api.server.domain.GameServer;

import java.util.Map;

/**
 * Human-readable Metin2 EApplyTypes used by market snapshots across supported servers.
 * Names and formatting rules mirror ElderSuite's ItemBonusFormatter and EquipmentBonusNames.
 */
public final class ItemBonusCatalog {
    private static final Map<Integer, Metadata> BONUSES = Map.ofEntries(
            entry(1, "APPLY_MAX_HP", "Max PŻ", Format.FLAT_SIGNED),
            entry(2, "APPLY_MAX_SP", "Max PE", Format.FLAT_SIGNED),
            entry(3, "APPLY_CON", "Witalność", Format.FLAT_SIGNED),
            entry(4, "APPLY_INT", "Inteligencja", Format.FLAT_SIGNED),
            entry(5, "APPLY_STR", "Siła", Format.FLAT_SIGNED),
            entry(6, "APPLY_DEX", "Zręczność", Format.FLAT_SIGNED),
            entry(7, "APPLY_ATT_SPEED", "Szybkość Ataku", Format.PERCENT_SIGNED),
            entry(8, "APPLY_MOV_SPEED", "Szybkość Ruchu", Format.PERCENT_SIGNED),
            entry(9, "APPLY_CAST_SPEED", "Szybkość Zaklęcia", Format.PERCENT_SIGNED),
            entry(10, "APPLY_HP_REGEN", "Regeneracja PŻ", Format.PERCENT_SIGNED),
            entry(11, "APPLY_SP_REGEN", "Regeneracja PE", Format.PERCENT_SIGNED),
            entry(12, "APPLY_POISON_PCT", "Szansa na Otrucie", Format.PERCENT),
            entry(13, "APPLY_STUN_PCT", "Szansa na Omdlenie", Format.PERCENT),
            entry(14, "APPLY_SLOW_PCT", "Szansa na Spowolnienie", Format.PERCENT),
            entry(15, "APPLY_CRITICAL_PCT", "Szansa na cios krytyczny", Format.PERCENT_SIGNED),
            entry(16, "APPLY_PENETRATE_PCT", "Szansa na przeszywające uderzenie", Format.PERCENT_SIGNED),
            entry(17, "APPLY_ATTBONUS_HUMAN", "Silny przeciwko Ludziom", Format.PERCENT_SIGNED),
            entry(18, "APPLY_ATTBONUS_ANIMAL", "Silny przeciwko Zwierzętom", Format.PERCENT_SIGNED),
            entry(19, "APPLY_ATTBONUS_ORC", "Silny przeciwko Orkom", Format.PERCENT_SIGNED),
            entry(20, "APPLY_ATTBONUS_MILGYO", "Silny przeciwko Mistykom", Format.PERCENT_SIGNED),
            entry(21, "APPLY_ATTBONUS_UNDEAD", "Silny przeciwko Nieumarłym", Format.PERCENT_SIGNED),
            entry(22, "APPLY_ATTBONUS_DEVIL", "Silny przeciwko Diabłom", Format.PERCENT_SIGNED),
            entry(23, "APPLY_STEAL_HP", "Obrażenia dodane do PŻ", Format.PERCENT),
            entry(24, "APPLY_STEAL_SP", "Obrażenia dodane do PE", Format.PERCENT),
            entry(25, "APPLY_MANA_BURN_PCT", "Szansa na kradzież PE", Format.PERCENT),
            entry(26, "APPLY_DAMAGE_SP_RECOVER", "Szansa na odzyskanie PE", Format.PERCENT),
            entry(27, "APPLY_BLOCK", "Szansa na zablokowanie ciosu", Format.PERCENT),
            entry(28, "APPLY_DODGE", "Szansa na uniknięcie strzały", Format.PERCENT),
            entry(29, "APPLY_RESIST_SWORD", "Odporność na Miecze", Format.PERCENT),
            entry(30, "APPLY_RESIST_TWOHAND", "Odporność na Broń Dwuręczną", Format.PERCENT),
            entry(31, "APPLY_RESIST_DAGGER", "Odporność na Sztylety", Format.PERCENT),
            entry(32, "APPLY_RESIST_BELL", "Odporność na Dzwony", Format.PERCENT),
            entry(33, "APPLY_RESIST_FAN", "Odporność na Wachlarze", Format.PERCENT),
            entry(34, "APPLY_RESIST_BOW", "Odporność na Strzały", Format.PERCENT),
            entry(35, "APPLY_RESIST_FIRE", "Odporność na Ogień", Format.PERCENT),
            entry(36, "APPLY_RESIST_ELEC", "Odporność na Błyskawice", Format.PERCENT),
            entry(37, "APPLY_RESIST_MAGIC", "Odporność na Magię", Format.PERCENT),
            entry(38, "APPLY_RESIST_WIND", "Odporność na Wiatr", Format.PERCENT),
            entry(39, "APPLY_REFLECT_MELEE", "Szansa na odbicie ciosu", Format.PERCENT),
            entry(40, "APPLY_REFLECT_CURSE", "Szansa na odbicie klątwy", Format.PERCENT),
            entry(41, "APPLY_POISON_REDUCE", "Odporność na Trucizny", Format.PERCENT),
            entry(42, "APPLY_KILL_SP_RECOVER", "Szansa na odzyskanie PE po zabiciu", Format.PERCENT),
            entry(43, "APPLY_EXP_DOUBLE_BONUS", "Szansa na podwójną ilość Doświadczenia", Format.PERCENT),
            entry(44, "APPLY_GOLD_DOUBLE_BONUS", "Szansa na podwójną ilość Yang", Format.PERCENT),
            entry(45, "APPLY_ITEM_DROP_BONUS", "Szansa na podwójną ilość Przedmiotów", Format.PERCENT),
            entry(46, "APPLY_POTION_BONUS", "Efekt mikstury", Format.PERCENT_SIGNED),
            entry(47, "APPLY_KILL_HP_RECOVER", "Szansa na odzyskanie PŻ po zabiciu", Format.PERCENT),
            entry(48, "APPLY_IMMUNE_STUN", "Niewrażliwy na Omdlenia", Format.FLAG),
            entry(49, "APPLY_IMMUNE_SLOW", "Niewrażliwy na Spowolnienie", Format.FLAG),
            entry(50, "APPLY_IMMUNE_FALL", "Niewrażliwy na Upadek", Format.FLAG),
            entry(52, "APPLY_BOW_DISTANCE", "Zasięg łuku", Format.METERS_FLAT_SIGNED),
            entry(53, "APPLY_ATT_GRADE_BONUS", "Wartość Ataku", Format.FLAT_SIGNED),
            entry(54, "APPLY_DEF_GRADE_BONUS", "Obrona", Format.FLAT_SIGNED),
            entry(55, "APPLY_MAGIC_ATT_GRADE", "Wartość Magicznego Ataku", Format.FLAT_SIGNED),
            entry(56, "APPLY_MAGIC_DEF_GRADE", "Magiczna Obrona", Format.FLAT_SIGNED),
            entry(57, "APPLY_CURSE_PCT", "Szansa na rzucenie klątwy", Format.PERCENT),
            entry(58, "APPLY_MAX_STAMINA", "Max Wytrzymałość", Format.FLAT_SIGNED),
            entry(59, "APPLY_ATTBONUS_WARRIOR", "Silny przeciwko Wojownikom", Format.PERCENT_SIGNED),
            entry(60, "APPLY_ATTBONUS_ASSASSIN", "Silny przeciwko Ninja", Format.PERCENT_SIGNED),
            entry(61, "APPLY_ATTBONUS_SURA", "Silny przeciwko Sura", Format.PERCENT_SIGNED),
            entry(62, "APPLY_ATTBONUS_SHAMAN", "Silny przeciwko Szamanom", Format.PERCENT_SIGNED),
            entry(63, "APPLY_ATTBONUS_MONSTER", "Silny przeciwko Potworom", Format.PERCENT_SIGNED),
            entry(71, "APPLY_SKILL_DAMAGE_BONUS", "Obrażenia Umiejętności", Format.PERCENT_SIGNED),
            entry(72, "APPLY_NORMAL_HIT_DAMAGE_BONUS", "Średnie Obrażenia", Format.PERCENT_SIGNED),
            entry(73, "APPLY_SKILL_DEFEND_BONUS", "Odporność na Umiejętności", Format.PERCENT_SIGNED),
            entry(74, "APPLY_NORMAL_HIT_DEFEND_BONUS", "Odporność na Średnie Obrażenia", Format.PERCENT),
            entry(78, "APPLY_RESIST_WARRIOR", "Odporność na Wojowników", Format.PERCENT),
            entry(79, "APPLY_RESIST_ASSASSIN", "Odporność na Ninja", Format.PERCENT),
            entry(80, "APPLY_RESIST_SURA", "Odporność na Sura", Format.PERCENT),
            entry(81, "APPLY_RESIST_SHAMAN", "Odporność na Szamanów", Format.PERCENT),
            entry(87, "APPLY_RESIST_ICE", "Odporność na Lód", Format.PERCENT),
            entry(88, "APPLY_RESIST_EARTH", "Odporność na Ziemię", Format.PERCENT),
            entry(89, "APPLY_RESIST_DARK", "Odporność na Mrok", Format.PERCENT),
            entry(90, "APPLY_ANTI_CRITICAL_PCT", "Odporność na Ciosy Krytyczne", Format.PERCENT),
            entry(91, "APPLY_ANTI_PENETRATE_PCT", "Odporność na Przeszywające Ciosy", Format.PERCENT),
            entry(92, "APPLY_ATTBONUS_BOSS", "Silny przeciwko Bossom", Format.PERCENT_SIGNED),
            entry(93, "APPLY_RESIST_BOSS", "Odporność na Bossy", Format.PERCENT),
            entry(94, "APPLY_ATTBONUS_METIN", "Silny przeciwko Kamieniom Metin", Format.PERCENT_SIGNED),
            entry(95, "APPLY_RESIST_MONSTER", "Odporność na Potwory", Format.PERCENT),
            entry(96, "APPLY_RESIST_WARRIOR_ASSASSIN", "Odporność na Wojownik/Ninja", Format.PERCENT),
            entry(97, "APPLY_RESIST_SURA_SHAMAN", "Odporność na Sura/Szaman", Format.PERCENT),
            entry(98, "APPLY_RESIST_ALL_CLASSES", "Odporność na Wszystkie Klasy", Format.PERCENT),
            entry(117, "APPLY_ATTBONUS_LEADERS", "Silny przeciwko Władcom", Format.PERCENT_SIGNED),
            entry(118, "APPLY_ATTBONUS_LEGENDS", "Silny przeciwko Legendom", Format.PERCENT_SIGNED),
            entry(119, "APPLY_MINING_EXTRACT_PCT", "Szansa na wydobycie rudy", Format.PERCENT_SIGNED),
            entry(120, "APPLY_MINING_DOUBLE_ORE_PCT", "Szansa na podwójną rudę", Format.PERCENT),
            entry(121, "APPLY_FISHING_SUCCESS_PCT", "Szansa na pomyślne łowienie", Format.PERCENT_SIGNED),
            entry(122, "APPLY_FISHING_DOUBLE_FISH_PCT", "Szansa na podwójną rybę", Format.PERCENT),
            entry(123, "APPLY_FISHING_RARE_PCT", "Szansa na rzadki połów", Format.PERCENT_SIGNED),
            entry(124, "APPLY_BUFF_VALUE_BOOST_PCT", "Zwiększenie Wartości Buffów", Format.PERCENT_SIGNED),
            entry(125, "APPLY_FIRE_POWER", "Moc Ognia", Format.PERCENT_SIGNED),
            entry(126, "APPLY_RESIST_FIRE_ELEMENT", "Odporność na Ogień", Format.PERCENT),
            entry(127, "APPLY_MINING_TIME_REDUCE", "Czas kopania rudy", Format.SECONDS_FLAT_SIGNED),
            entry(128, "APPLY_FISHING_TIME_REDUCE", "Czas wyciągania wędki", Format.SECONDS_FLAT_SIGNED),
            entry(129, "APPLY_FISHING_DOUBLE_POINTS_PCT", "Podwójne punkty wędkarstwa", Format.PERCENT),
            entry(130, "APPLY_ALL_STATS", "Wszystkie Statystyki", Format.FLAT_SIGNED),
            entry(131, "APPLY_ATTBONUS_PVP", "Silny przeciwko Postaciom (PvP)", Format.PERCENT_SIGNED),
            entry(132, "APPLY_RESIST_PVP", "Odporność na Postacie (PvP)", Format.PERCENT),
            entry(133, "APPLY_ATTBONUS_EXPEDITIONS", "Silny na Wyprawach", Format.PERCENT_SIGNED),
            entry(134, "APPLY_RESIST_EXPEDITIONS", "Odporność na Wyprawach", Format.PERCENT),
            entry(135, "APPLY_CRIT_ATT_GRADE", "Wartość Ataku Krytycznego", Format.FLAT_SIGNED),
            entry(136, "APPLY_RUNE_BOOST_PCT", "Wzmocnienie Run", Format.PERCENT_SIGNED),
            entry(137, "APPLY_DAMAGE_PVE_PCT", "Obrażenia PvE", Format.PERCENT_SIGNED),
            entry(138, "APPLY_DAMAGE_PVP_PCT", "Obrażenia PvP", Format.PERCENT_SIGNED),
            entry(139, "APPLY_ATT_CELESTIAL_PCT", "Atak Niebiański", Format.PERCENT_SIGNED),
            entry(140, "APPLY_DEF_CELESTIAL_PCT", "Obrona Niebiańska", Format.PERCENT),
            entry(141, "APPLY_PENETRATE_BLOCK_PCT", "Przebicie Bloku", Format.PERCENT),
            entry(142, "APPLY_PENETRATE_DODGE_PCT", "Przebicie Uniku", Format.PERCENT),
            entry(143, "APPLY_BOSS_DOUBLE_DROP_PCT", "Podwójny Drop z Bossa", Format.PERCENT),
            entry(144, "APPLY_ATTBONUS_EVENTS_PCT", "Silny na Wydarzeniach", Format.PERCENT_SIGNED),
            entry(149, "APPLY_PET_DAMAGE_PCT", "Obrażenia Zwierzaka", Format.PERCENT_SIGNED),
            entry(150, "APPLY_AUTO_PICKUP", "Automatyczne Podnoszenie", Format.FLAG)
    );

    private ItemBonusCatalog() {
    }

    public static Details describe(int type, int value) {
        Metadata metadata = BONUSES.get(type);
        if (metadata == null) {
            return new Details("UNKNOWN", "Nieznany bonus", signed(value));
        }
        return new Details(metadata.code(), metadata.name(), metadata.format().display(value));
    }

    public static Details describe(GameServer server, int type, int value) {
        if (server == GameServer.PANDORA || server == GameServer.BEAVIUM || server == GameServer.ELDER) {
            return describe(type, value);
        }
        return new Details("UNKNOWN", "Nieznany bonus", signed(value));
    }

    private static Map.Entry<Integer, Metadata> entry(int type, String code, String name, Format format) {
        return Map.entry(type, new Metadata(code, name, format));
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    public record Details(String code, String name, String displayValue) {
    }

    private record Metadata(String code, String name, Format format) {
    }

    private enum Format {
        PERCENT_SIGNED {
            @Override String display(int value) { return signed(value) + "%"; }
        },
        PERCENT {
            @Override String display(int value) { return value + "%"; }
        },
        FLAT_SIGNED {
            @Override String display(int value) { return signed(value); }
        },
        SECONDS_FLAT_SIGNED {
            @Override String display(int value) { return signed(value) + "s"; }
        },
        METERS_FLAT_SIGNED {
            @Override String display(int value) { return signed(value) + "m"; }
        },
        FLAG {
            @Override String display(int value) { return null; }
        };

        abstract String display(int value);
    }
}
