package com.mazikox.metin_market_api.market.infrastructure.jdbc;

import com.mazikox.metin_market_api.market.api.ItemSearchResult;
import com.mazikox.metin_market_api.market.application.port.MarketRepository;
import com.mazikox.metin_market_api.market.domain.ItemSuggestion;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.Array;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class JdbcMarketRepository implements MarketRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public JdbcMarketRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    private record RowWithTotal(MarketListingRecord record, long totalCount) {}

    @Override
    public MarketListingPage searchListings(String query, List<Integer> vnums, int page, int size) {
        boolean hasQuery = query != null && !query.isBlank();
        String filter;
        if (vnums.isEmpty()) {
            filter = hasQuery ? "public.unaccent(lower(l.item_name)) LIKE public.unaccent(lower(:query))" : "TRUE";
        } else {
            filter = hasQuery
                    ? "l.item_vnum IN (:vnums) AND public.unaccent(lower(l.item_name)) LIKE public.unaccent(lower(:query))"
                    : "l.item_vnum IN (:vnums)";
        }
        String baseCte = """
                WITH latest_run AS (
                    SELECT id FROM scan_run
                    WHERE state = 3 AND publishable = true
                    ORDER BY ended_at DESC NULLS LAST, started_at DESC, id DESC
                    LIMIT 1
                ), matching_listings AS (
                    SELECT l.id, l.observation_id, l.item_vnum, l.item_name, l.quantity,
                           l.price_raw, l.unit_price, l.tail_field,
                           o.shop_vid, o.shop_title, o.owner_name, o.map_id, o.channel,
                           o.x, o.y, o.z, o.observed_at
                    FROM latest_run r
                    JOIN shop_observation o ON o.scan_run_id = r.id
                    JOIN shop_listing l ON l.observation_id = o.id
                    WHERE %s
                ), attr_agg AS (
                    SELECT a.listing_id,
                           array_agg(a.slot_index || ':' || a.attr_type || ':' || a.attr_value ORDER BY a.slot_index) AS attr_sig
                    FROM shop_listing_attribute a
                    WHERE a.listing_id IN (SELECT id FROM matching_listings)
                    GROUP BY a.listing_id
                ), sock_agg AS (
                    SELECT s.listing_id,
                           array_agg(s.socket_index || ':' || s.socket_value ORDER BY s.socket_index) AS sock_sig
                    FROM shop_listing_socket s
                    WHERE s.listing_id IN (SELECT id FROM matching_listings)
                    GROUP BY s.listing_id
                ), matching AS (
                    SELECT l.*,
                           COALESCE(a.attr_sig, ARRAY[]::text[]) AS attr_sig,
                           COALESCE(s.sock_sig, ARRAY[]::text[]) AS sock_sig
                    FROM matching_listings l
                    LEFT JOIN attr_agg a ON a.listing_id = l.id
                    LEFT JOIN sock_agg s ON s.listing_id = l.id
                ), aggregated AS (
                    SELECT min(id) AS listing_id, item_vnum, item_name, quantity, price_raw, unit_price,
                           tail_field, shop_vid, shop_title, owner_name, map_id, channel, x, y, z,
                           observed_at,
                           sum(quantity)::bigint AS total_quantity,
                           sum(price_raw)::bigint AS total_price,
                           count(*)::integer AS listing_count
                    FROM matching
                    GROUP BY observation_id, item_vnum, item_name, quantity, price_raw, unit_price,
                             tail_field, shop_vid, shop_title, owner_name, map_id, channel, x, y, z,
                             observed_at, attr_sig, sock_sig
                )
                """.formatted(filter);

        var dataSpec = jdbc.sql(baseCte + """
                , scored AS (
                    SELECT *, count(*) OVER() AS total_count
                    FROM aggregated
                ), page AS (
                    SELECT * FROM scored
                    ORDER BY unit_price ASC, observed_at DESC, listing_id DESC
                    LIMIT :size OFFSET :offset
                )
                SELECT p.*,
                       COALESCE((SELECT jsonb_agg(jsonb_build_object(
                           'slotIndex', a.slot_index, 'type', a.attr_type, 'value', a.attr_value)
                           ORDER BY a.slot_index)
                           FROM shop_listing_attribute a WHERE a.listing_id = p.listing_id), '[]'::jsonb) AS attributes,
                       COALESCE((SELECT jsonb_agg(jsonb_build_object(
                           'socketIndex', s.socket_index, 'value', s.socket_value)
                           ORDER BY s.socket_index)
                           FROM shop_listing_socket s WHERE s.listing_id = p.listing_id), '[]'::jsonb) AS sockets
                FROM page p
                """);
        if (hasQuery) {
            dataSpec = dataSpec.param("query", "%" + query + "%");
        }
        if (!vnums.isEmpty()) {
            dataSpec = dataSpec.param("vnums", vnums);
        }
        dataSpec = dataSpec.param("size", size).param("offset", (long) page * size);

        List<RowWithTotal> rows = dataSpec.query((rs, rowNum) -> {
            try {
                long totalCount = rs.getLong("total_count");
                OffsetDateTime observedAt = rs.getObject("observed_at", OffsetDateTime.class);
                MarketListingRecord record = new MarketListingRecord(
                        rs.getLong("listing_id"),
                        rs.getInt("item_vnum"),
                        rs.getString("item_name"),
                        rs.getInt("quantity"),
                        rs.getLong("price_raw"),
                        rs.getLong("unit_price"),
                        rs.getLong("total_quantity"),
                        rs.getLong("total_price"),
                        rs.getInt("listing_count"),
                        objectMapper.<List<RawItemAttribute>>readValue(
                                rs.getString("attributes"), new TypeReference<>() {}),
                        objectMapper.<List<ItemSearchResult.ItemSocket>>readValue(
                                rs.getString("sockets"), new TypeReference<>() {}),
                        new ItemSearchResult.Shop(
                                (Long) rs.getObject("shop_vid"),
                                rs.getString("shop_title"),
                                rs.getString("owner_name"),
                                rs.getString("map_id"),
                                (Integer) rs.getObject("channel"),
                                rs.getDouble("x"),
                                rs.getDouble("y"),
                                rs.getDouble("z")),
                        observedAt != null ? observedAt.toLocalDate() : null
                );
                return new RowWithTotal(record, totalCount);
            } catch (Exception e) {
                throw new SQLException("Cannot decode listing details", e);
            }
        }).list();

        List<MarketListingRecord> items = rows.stream().map(RowWithTotal::record).toList();
        long total;
        if (!rows.isEmpty()) {
            total = rows.getFirst().totalCount();
        } else if (page == 0) {
            total = 0;
        } else {
            var countSpec = jdbc.sql(baseCte + "SELECT count(*) FROM aggregated");
            if (hasQuery) {
                countSpec = countSpec.param("query", "%" + query + "%");
            }
            if (!vnums.isEmpty()) {
                countSpec = countSpec.param("vnums", vnums);
            }
            total = countSpec.query(Long.class).single();
        }

        return new MarketListingPage(items, total);
    }

    @Override
    public long countCatalogItems(String query, Integer vnum) {
        String filter = vnum == null
                ? "public.unaccent(lower(c.item_name)) LIKE public.unaccent(lower(:query))"
                : "c.item_vnum = :vnum AND public.unaccent(lower(c.item_name)) LIKE public.unaccent(lower(:query))";
        String catalog = "WITH catalog AS (SELECT DISTINCT item_vnum, item_name FROM shop_listing) ";
        var count = jdbc.sql(catalog + "SELECT count(*) FROM catalog c WHERE " + filter)
                .param("query", "%" + query + "%");
        if (vnum != null) {
            count = count.param("vnum", vnum);
        }
        return count.query(Long.class).single();
    }

    @Override
    public List<ItemSuggestion> findCatalogItems(String query, Integer vnum, int limit) {
        String filter = vnum == null
                ? "public.unaccent(lower(c.item_name)) LIKE public.unaccent(lower(:query))"
                : "c.item_vnum = :vnum AND public.unaccent(lower(c.item_name)) LIKE public.unaccent(lower(:query))";
        String catalog = "WITH catalog AS (SELECT DISTINCT item_vnum, item_name FROM shop_listing) ";
        var items = jdbc.sql(catalog + "SELECT item_name, item_vnum FROM catalog c WHERE " + filter
                        + " ORDER BY lower(item_name), item_vnum LIMIT :limit")
                .param("query", "%" + query + "%")
                .param("limit", limit);
        if (vnum != null) {
            items = items.param("vnum", vnum);
        }
        return items.query((rs, row) ->
                ItemSuggestion.item(rs.getString("item_name"), rs.getInt("item_vnum"))).list();
    }

    @Override
    public List<ItemSuggestion> findUpgradeFamilies(String query, Integer vnum, int limit) {
        String filter = vnum == null
                ? "public.unaccent(lower(c.item_name)) LIKE public.unaccent(lower(:query))"
                : "c.item_vnum = :vnum AND public.unaccent(lower(c.item_name)) LIKE public.unaccent(lower(:query))";
        String catalog = "WITH catalog AS (SELECT DISTINCT item_vnum, item_name FROM shop_listing) ";
        String familiesSql = catalog + ", " + """
                matching AS (
                    SELECT item_name FROM catalog c WHERE %s
                ), candidate_bases AS (
                    SELECT DISTINCT regexp_replace(item_name, '\\+[0-9]$', '') AS base_name
                    FROM matching WHERE item_name ~ '\\+[0-9]$'
                )
                SELECT b.base_name, array_agg(c.item_vnum ORDER BY c.item_vnum) AS member_vnums,
                       array_agg(c.item_name ORDER BY c.item_name) AS member_names
                FROM candidate_bases b
                JOIN catalog c ON regexp_replace(c.item_name, '\\+[0-9]$', '') = b.base_name
                WHERE c.item_name ~ '\\+[0-9]$'
                GROUP BY b.base_name
                HAVING count(*) >= 2
                ORDER BY lower(b.base_name)
                LIMIT :limit
                """.formatted(filter);
        var families = jdbc.sql(familiesSql)
                .param("query", "%" + query + "%")
                .param("limit", limit);
        if (vnum != null) {
            families = families.param("vnum", vnum);
        }
        return families.query((rs, row) -> {
            try {
                return ItemSuggestion.upgradeFamily(
                        rs.getString("base_name") + " +0-9",
                        integerList(rs.getArray("member_vnums")),
                        stringList(rs.getArray("member_names"))
                );
            } catch (SQLException e) {
                throw new RuntimeException("Cannot decode upgrade family", e);
            }
        }).list();
    }

    @Override
    public List<ShopPrice> findShopPrices(List<Integer> vnums) {
        String placeholders = String.join(", ", vnums.stream().map(vnum -> ":vnum" + vnum).toList());
        var query = jdbc.sql("""
                WITH latest_run AS (
                    SELECT id FROM scan_run
                    WHERE state = 3 AND publishable = true
                    ORDER BY ended_at DESC NULLS LAST, started_at DESC, id DESC
                    LIMIT 1
                ), per_shop AS (
                    SELECT l.item_vnum, min(l.item_name) AS item_name, l.observation_id,
                           min(l.unit_price) AS cheapest_unit_price, count(*) AS raw_offer_count,
                           sum(l.quantity)::bigint AS total_quantity
                    FROM latest_run r
                    JOIN shop_observation o ON o.scan_run_id = r.id
                    JOIN shop_listing l ON l.observation_id = o.id
                    WHERE l.item_vnum IN (%s)
                    GROUP BY l.item_vnum, l.observation_id
                )
                SELECT item_vnum, item_name, cheapest_unit_price, raw_offer_count, total_quantity
                FROM per_shop
                ORDER BY item_vnum, cheapest_unit_price
                """.formatted(placeholders));
        for (Integer vnum : vnums) {
            query = query.param("vnum" + vnum, vnum);
        }
        return query.query((rs, row) -> new ShopPrice(
                rs.getInt("item_vnum"),
                rs.getString("item_name"),
                rs.getLong("cheapest_unit_price"),
                rs.getLong("raw_offer_count"),
                rs.getLong("total_quantity")
        )).list();
    }

    private static List<Integer> integerList(Array array) throws SQLException {
        Integer[] values = (Integer[]) array.getArray();
        return List.of(values);
    }

    private static List<String> stringList(Array array) throws SQLException {
        String[] values = (String[]) array.getArray();
        return List.of(values);
    }
}
