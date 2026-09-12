package com.mazikox.metin_market_api.market;

import java.util.Map;

/**
 * Human-readable Metin2 EApplyTypes used by the Pandora market snapshot.
 * Names and formatting rules mirror Pandora's ItemBonusFormatter.
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
            entry(41, "APPLY_POISON_REDUCE", "Odporność na Trucizny", Format.PERCENT),
            entry(43, "APPLY_EXP_DOUBLE_BONUS", "Szansa na podwójną ilość Doświadczenia", Format.PERCENT),
            entry(44, "APPLY_GOLD_DOUBLE_BONUS", "Szansa na podwójną ilość Yang", Format.PERCENT),
            entry(45, "APPLY_ITEM_DROP_BONUS", "Szansa na podwójną ilość Przedmiotów", Format.PERCENT),
            entry(48, "APPLY_IMMUNE_STUN", "Niewrażliwy na Omdlenia", Format.FLAG),
            entry(49, "APPLY_IMMUNE_SLOW", "Niewrażliwy na Spowolnienie", Format.FLAG),
            entry(53, "APPLY_ATT_GRADE_BONUS", "Wartość Ataku", Format.FLAT_SIGNED),
            entry(54, "APPLY_DEF_GRADE_BONUS", "Obrona", Format.FLAT_SIGNED),
            entry(59, "APPLY_ATTBONUS_WARRIOR", "Silny przeciwko Wojownikom", Format.PERCENT_SIGNED),
            entry(60, "APPLY_ATTBONUS_ASSASSIN", "Silny przeciwko Ninja", Format.PERCENT_SIGNED),
            entry(61, "APPLY_ATTBONUS_SURA", "Silny przeciwko Sura", Format.PERCENT_SIGNED),
            entry(62, "APPLY_ATTBONUS_SHAMAN", "Silny przeciwko Szamanom", Format.PERCENT_SIGNED),
            entry(63, "APPLY_ATTBONUS_MONSTER", "Silny przeciwko Potworom", Format.PERCENT_SIGNED),
            entry(71, "APPLY_SKILL_DAMAGE_BONUS", "Obrażenia Umiejętności", Format.PERCENT_SIGNED),
            entry(72, "APPLY_NORMAL_HIT_DAMAGE_BONUS", "Średnie Obrażenia", Format.PERCENT_SIGNED),
            entry(74, "APPLY_NORMAL_HIT_DEFEND_BONUS", "Odporność na Średnie Obrażenia", Format.PERCENT),
            entry(78, "APPLY_RESIST_WARRIOR", "Odporność na Wojowników", Format.PERCENT),
            entry(79, "APPLY_RESIST_ASSASSIN", "Odporność na Ninja", Format.PERCENT),
            entry(80, "APPLY_RESIST_SURA", "Odporność na Sura", Format.PERCENT),
            entry(81, "APPLY_RESIST_SHAMAN", "Odporność na Szamanów", Format.PERCENT)
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
        FLAG {
            @Override String display(int value) { return null; }
        };

        abstract String display(int value);
    }
}
