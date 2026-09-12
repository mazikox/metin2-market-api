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
        String groupedListings = """
                WITH matching AS (
                    SELECT l.id, l.observation_id, l.item_vnum, l.item_name, l.quantity,
                           l.price_raw, l.unit_price, l.tail_field,
                           o.shop_vid, o.shop_title, o.owner_name, o.map_id, o.channel,
                           o.x, o.y, o.z, o.observed_at,
                           COALESCE((SELECT jsonb_agg(jsonb_build_object(
                               'slotIndex', a.slot_index, 'type', a.attr_type, 'value', a.attr_value)
                               ORDER BY a.slot_index)
                               FROM shop_listing_attribute a WHERE a.listing_id = l.id), '[]'::jsonb) AS attributes,
                           COALESCE((SELECT jsonb_agg(jsonb_build_object(
                               'socketIndex', s.socket_index, 'value', s.socket_value)
                               ORDER BY s.socket_index)
                               FROM shop_listing_socket s WHERE s.listing_id = l.id), '[]'::jsonb) AS sockets
                    FROM shop_listing l
                    JOIN shop_observation o ON o.id = l.observation_id
                    WHERE %s
                ), aggregated AS (
                    SELECT min(id) AS listing_id, item_vnum, item_name, quantity, price_raw, unit_price,
                           tail_field, shop_vid, shop_title, owner_name, map_id, channel, x, y, z,
                           observed_at, attributes, sockets,
                           sum(quantity)::bigint AS total_quantity,
                           sum(price_raw)::bigint AS total_price,
                           count(*)::integer AS listing_count
                    FROM matching
                    GROUP BY observation_id, item_vnum, item_name, quantity, price_raw, unit_price,
                             tail_field, shop_vid, shop_title, owner_name, map_id, channel, x, y, z,
                             observed_at, attributes, sockets
                )
                """.formatted(filter);
        var countSpec = jdbc.sql(groupedListings + "SELECT count(*) FROM aggregated")
                .param("query", "%" + query + "%");
        var dataSpec = jdbc.sql(groupedListings + """
                SELECT * FROM aggregated
                ORDER BY unit_price ASC, observed_at DESC, listing_id DESC
                LIMIT :size OFFSET :offset
                """)
                .param("query", "%" + query + "%").param("size", size).param("offset", (long) page * size);
        if (vnum != null) {
            countSpec = countSpec.param("vnum", vnum);
            dataSpec = dataSpec.param("vnum", vnum);
        }
        long total = countSpec.query(Long.class).single();
        List<ItemSearchResult> items = dataSpec.query((rs, rowNum) -> {
            try {
                return new ItemSearchResult(rs.getLong("listing_id"), rs.getInt("item_vnum"), rs.getString("item_name"),
                        rs.getInt("quantity"), rs.getLong("price_raw"), rs.getLong("unit_price"),
                        rs.getLong("total_quantity"), rs.getLong("total_price"), rs.getInt("listing_count"),
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
