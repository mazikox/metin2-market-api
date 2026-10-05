package com.mazikox.metin_market_api.catalog.infrastructure;

import com.mazikox.metin_market_api.catalog.domain.ItemDefinition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class JdbcItemDefinitions {
    private final JdbcTemplate jdbc;
    private final JdbcClient client;
    public JdbcItemDefinitions(JdbcTemplate jdbc, JdbcClient client) {
        this.jdbc = jdbc;
        this.client = client;
    }

    @Transactional
    public void upsert(List<ItemDefinition> items) {
        // Serialize imports within this server; readers still see an atomic catalog update.
        jdbc.execute("LOCK TABLE item_definition IN SHARE ROW EXCLUSIVE MODE");
        jdbc.batchUpdate("""
                INSERT INTO item_definition (vnum, item_name, item_type, item_subtype, inventory_size,
                    anti_flags, required_level, defense, min_attack, max_attack,
                    min_magic_attack, max_magic_attack, socket_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (vnum) DO UPDATE SET item_name = EXCLUDED.item_name,
                    item_type = EXCLUDED.item_type, item_subtype = EXCLUDED.item_subtype,
                    inventory_size = EXCLUDED.inventory_size, anti_flags = EXCLUDED.anti_flags,
                    required_level = EXCLUDED.required_level, defense = EXCLUDED.defense,
                    min_attack = EXCLUDED.min_attack, max_attack = EXCLUDED.max_attack,
                    min_magic_attack = EXCLUDED.min_magic_attack, max_magic_attack = EXCLUDED.max_magic_attack,
                    socket_count = EXCLUDED.socket_count, updated_at = now()
                """, items.stream().map(i -> new Object[]{i.vnum(), i.name(), i.type(), i.subtype(), i.size(),
                i.antiFlags(), i.requiredLevel(), i.defense(), i.minAttack(), i.maxAttack(),
                i.minMagicAttack(), i.maxMagicAttack(), i.socketCount()}).toList());
        client.sql("DELETE FROM item_definition_bonus WHERE item_vnum IN (:vnums)")
                .param("vnums", items.stream().map(ItemDefinition::vnum).toList()).update();
        var bonuses = new ArrayList<Object[]>();
        for (var item : items) for (var b : item.builtInBonuses())
            bonuses.add(new Object[]{item.vnum(), b.slotIndex(), b.type(), b.value()});
        if (!bonuses.isEmpty()) jdbc.batchUpdate("""
                INSERT INTO item_definition_bonus (item_vnum, slot_index, bonus_type, bonus_value)
                VALUES (?, ?, ?, ?)
                """, bonuses);
    }

    public Map<Integer, ItemDefinition> findByVnums(List<Integer> vnums) {
        if (vnums.isEmpty()) return Map.of();
        var definitions = new LinkedHashMap<Integer, ItemDefinition>();
        var bonuses = new LinkedHashMap<Integer, List<ItemDefinition.BaseBonus>>();
        client.sql("""
                SELECT d.*, b.slot_index, b.bonus_type, b.bonus_value
                FROM item_definition d LEFT JOIN item_definition_bonus b ON b.item_vnum = d.vnum
                WHERE d.vnum IN (:vnums) ORDER BY d.vnum, b.slot_index
                """).param("vnums", vnums).query((rs, rowNum) -> {
            int vnum = rs.getInt("vnum");
            var list = bonuses.computeIfAbsent(vnum, ignored -> new ArrayList<>());
            definitions.putIfAbsent(vnum, new ItemDefinition(vnum, rs.getString("item_name"),
                    rs.getInt("item_type"), rs.getInt("item_subtype"), rs.getInt("inventory_size"),
                    rs.getLong("anti_flags"), rs.getInt("required_level"), rs.getInt("defense"),
                    rs.getInt("min_attack"), rs.getInt("max_attack"), rs.getInt("min_magic_attack"),
                    rs.getInt("max_magic_attack"), rs.getInt("socket_count"), list));
            if (rs.getObject("bonus_type") != null) list.add(new ItemDefinition.BaseBonus(
                    rs.getInt("slot_index"), rs.getInt("bonus_type"), rs.getInt("bonus_value")));
            return vnum;
        }).list();
        return definitions;
    }
}
