package com.mazikox.metin_market_api.market;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ItemSearchService {
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public ItemSearchService(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public SearchPage search(String query, Integer vnum, int page, int size) {
        String filter = vnum == null
                ? "lower(l.item_name) LIKE lower(:query)"
                : "l.item_vnum = :vnum AND lower(l.item_name) LIKE lower(:query)";
        var countSpec = jdbc.sql("SELECT count(*) FROM shop_listing l WHERE " + filter)
                .param("query", "%" + query + "%");
        var dataSpec = jdbc.sql("""
                SELECT l.id, l.item_vnum, l.item_name, l.quantity, l.price_raw, l.unit_price,
                       o.shop_vid, o.shop_title, o.owner_name, o.map_id, o.channel,
                       o.x, o.y, o.z, o.observed_at,
                       COALESCE((SELECT jsonb_agg(jsonb_build_object(
                           'slotIndex', a.slot_index, 'type', a.attr_type, 'value', a.attr_value)
                           ORDER BY a.slot_index) FROM shop_listing_attribute a WHERE a.listing_id = l.id), '[]') AS attributes,
                       COALESCE((SELECT jsonb_agg(jsonb_build_object(
                           'socketIndex', s.socket_index, 'value', s.socket_value)
                           ORDER BY s.socket_index) FROM shop_listing_socket s WHERE s.listing_id = l.id), '[]') AS sockets
                FROM shop_listing l
                JOIN shop_observation o ON o.id = l.observation_id
                WHERE %s
                ORDER BY o.observed_at DESC, l.id DESC
                LIMIT :size OFFSET :offset
                """.formatted(filter))
                .param("query", "%" + query + "%").param("size", size).param("offset", (long) page * size);
        if (vnum != null) {
            countSpec = countSpec.param("vnum", vnum);
            dataSpec = dataSpec.param("vnum", vnum);
        }
        long total = countSpec.query(Long.class).single();
        List<ItemSearchResult> items = dataSpec.query((rs, rowNum) -> {
            try {
                return new ItemSearchResult(rs.getLong("id"), rs.getInt("item_vnum"), rs.getString("item_name"),
                        rs.getInt("quantity"), rs.getLong("price_raw"), rs.getLong("unit_price"),
                        objectMapper.<List<RawItemAttribute>>readValue(rs.getString("attributes"), new TypeReference<>() {})
                                .stream().map(attribute -> {
                                    ItemBonusCatalog.Details details = ItemBonusCatalog.describe(
                                            attribute.type(), attribute.value());
                                    return new ItemSearchResult.ItemAttribute(attribute.slotIndex(), attribute.type(),
                                            details.code(), details.name(), attribute.value(), details.displayValue());
                                }).toList(),
                        objectMapper.readValue(rs.getString("sockets"), new TypeReference<>() {}),
                        new ItemSearchResult.Shop((Long) rs.getObject("shop_vid"), rs.getString("shop_title"),
                                rs.getString("owner_name"), rs.getString("map_id"), (Integer) rs.getObject("channel"),
                                rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z")),
                        rs.getObject("observed_at", OffsetDateTime.class));
            } catch (Exception e) {
                throw new SQLException("Cannot decode listing details", e);
            }
        }).list();
        return new SearchPage(items, page, size, total);
    }

    public record SearchPage(List<ItemSearchResult> items, int page, int size, long totalElements) {}

    private record RawItemAttribute(int slotIndex, int type, int value) {}
}
