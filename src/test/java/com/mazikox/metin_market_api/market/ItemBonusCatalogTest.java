package com.mazikox.metin_market_api.market;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItemBonusCatalogTest {
    @Test
    void describesPercentageBonus() {
        ItemBonusCatalog.Details bonus = ItemBonusCatalog.describe(17, 10);

        assertThat(bonus.code()).isEqualTo("APPLY_ATTBONUS_HUMAN");
        assertThat(bonus.name()).isEqualTo("Silny przeciwko Ludziom");
        assertThat(bonus.displayValue()).isEqualTo("+10%");
    }

    @Test
    void describesFlatHpBonus() {
        ItemBonusCatalog.Details bonus = ItemBonusCatalog.describe(1, 2000);

        assertThat(bonus.code()).isEqualTo("APPLY_MAX_HP");
        assertThat(bonus.name()).isEqualTo("Max PŻ");
        assertThat(bonus.displayValue()).isEqualTo("+2000");
    }

    @Test
    void preservesNegativeSkillAndAverageDamage() {
        assertThat(ItemBonusCatalog.describe(71, -15).displayValue()).isEqualTo("-15%");
        assertThat(ItemBonusCatalog.describe(72, 48).displayValue()).isEqualTo("+48%");
    }

    @Test
    void describesImmunityAsFlagWithoutNumericValue() {
        ItemBonusCatalog.Details bonus = ItemBonusCatalog.describe(48, 1);

        assertThat(bonus.code()).isEqualTo("APPLY_IMMUNE_STUN");
        assertThat(bonus.name()).isEqualTo("Niewrażliwy na Omdlenia");
        assertThat(bonus.displayValue()).isNull();
    }

    @Test
    void returnsSafeFallbackForUnknownTypes() {
        ItemBonusCatalog.Details bonus = ItemBonusCatalog.describe(999, -7);

        assertThat(bonus.code()).isEqualTo("UNKNOWN");
        assertThat(bonus.name()).isEqualTo("Nieznany bonus");
        assertThat(bonus.displayValue()).isEqualTo("-7");
    }

    @Test
    void coversEveryApplyTypePresentInThePandoraSnapshot() {
        int[] pandoraTypes = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
                17, 18, 19, 20, 21, 22, 23, 24, 25, 29, 30, 31, 32, 33, 34, 35, 36,
                37, 38, 39, 41, 43, 44, 45, 48, 49, 53, 54, 59, 60, 61, 62, 63, 71,
                72, 74, 78, 79, 80, 81};

        for (int type : pandoraTypes) {
            assertThat(ItemBonusCatalog.describe(type, 1).code())
                    .as("Pandora attr_type %s", type)
                    .isNotEqualTo("UNKNOWN");
        }
    }
}
