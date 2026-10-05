package com.mazikox.metin_market_api.market.infrastructure.jdbc;

import com.mazikox.metin_market_api.market.api.ItemSearchResult;
import com.mazikox.metin_market_api.market.application.port.MarketRepository;
import com.mazikox.metin_market_api.market.domain.OfferSort;
import com.mazikox.metin_market_api.market.domain.ItemPriceStatisticsCalculator.RawListing;
import com.mazikox.metin_market_api.market.domain.ItemSuggestion;
import com.mazikox.metin_market_api.market.domain.MarketOverview;
import com.mazikox.metin_market_api.market.domain.MarketOverviewSort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import com.mazikox.metin_market_api.market.domain.BonusFilter;
import java.sql.Array;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import com.mazikox.metin_market_api.market.domain.ItemFilters;

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
    public MarketOverview findMarketOverview(int limit, MarketOverviewSort sort) {
        // Keep cards and scan metadata in the same database snapshot.
        // Imports arrive in batches, so caching by run ID could retain partial data.
        return jdbc.sql("""
                WITH latest_run AS (
                    SELECT id, ended_at FROM scan_run
                    WHERE state = 3 AND publishable = true
                    ORDER BY ended_at DESC NULLS LAST, started_at DESC, id DESC
                    LIMIT 1
                ), ranked_observations AS (
                    SELECT o.id,
                           ROW_NUMBER() OVER (
                               PARTITION BY COALESCE(o.shop_vid::text, 'obs:' || o.id::text)
                               ORDER BY o.observed_at DESC, o.id DESC
                           ) AS rn
                    FROM latest_run r
                    JOIN shop_observation o ON o.scan_run_id = r.id
                ), canonical_observations AS (
                    SELECT id FROM ranked_observations WHERE rn = 1
                ), item_totals AS (
                    SELECT l.item_vnum, l.item_name,
                           count(DISTINCT o.id) AS shop_count,
                           sum(l.quantity)::bigint AS total_quantity,
                           min(l.unit_price) AS minimum_price
                    FROM canonical_observations o
                    JOIN shop_listing l ON l.observation_id = o.id
                    GROUP BY l.item_vnum, l.item_name
                ), popular_items AS (
                    SELECT * FROM item_totals
                    ORDER BY CASE WHEN :byQuantity THEN total_quantity ELSE shop_count END DESC,
                             shop_count DESC, item_vnum, item_name
                    LIMIT :limit
                )
                SELECT r.id, r.ended_at,
                       (SELECT count(*) FROM canonical_observations) AS observed_shop_count,
                       COALESCE((SELECT jsonb_agg(jsonb_build_object(
                           'vnum', p.item_vnum, 'itemName', p.item_name,
                           'shopCount', p.shop_count, 'totalQuantity', p.total_quantity,
                           'minimumPrice', p.minimum_price)
                           ORDER BY CASE WHEN :byQuantity THEN p.total_quantity ELSE p.shop_count END DESC,
                                    p.shop_count DESC, p.item_vnum, p.item_name)
                           FROM popular_items p), '[]'::jsonb) AS items
                FROM latest_run r
                """).param("limit", limit).param("byQuantity", sort == MarketOverviewSort.QUANTITY).query((rs, rowNum) -> {
            try {
                return new MarketOverview(
                        rs.getLong("id"), rs.getObject("ended_at", OffsetDateTime.class),
                        rs.getLong("observed_shop_count"),
                        objectMapper.<List<MarketOverview.PopularItem>>readValue(
                                rs.getString("items"), new TypeReference<>() {}));
            } catch (Exception e) {
                throw new SQLException("Cannot decode market overview", e);
            }
        }).optional().orElseGet(MarketOverview::empty);
    }


    @Override
    public MarketListingPage searchListings(String query, List<Integer> vnums, int page, int size, OfferSort sort, List<BonusFilter> bonuses, ItemFilters itemFilters) {
        boolean hasQuery = query != null && !query.isBlank();
        String filter;
        if (vnums.isEmpty()) {
            filter = hasQuery ? "public.unaccent(lower(l.item_name)) LIKE public.unaccent(lower(:query))" : "TRUE";
        } else {
            filter = hasQuery
                    ? "l.item_vnum IN (:vnums) AND public.unaccent(lower(l.item_name)) LIKE public.unaccent(lower(:query))"
                    : "l.item_vnum IN (:vnums)";
        }
        for (int i = 0; i < bonuses.size(); i++) {
            // Conditions all belong to this listing, never different items in the same shop.
            filter += " AND EXISTS (SELECT 1 FROM shop_listing_attribute ba WHERE ba.listing_id = l.id"
                    + " AND ba.attr_type = :bonusType" + i
                    + (bonuses.get(i).minimum() == null ? "" : " AND ba.attr_value >= :bonusMin" + i) + ")";
        }
        if (itemFilters.active()) {
            filter += " AND EXISTS (SELECT 1 FROM item_definition d WHERE d.vnum = l.item_vnum"
                    + (itemFilters.category() == null ? "" : " AND d.item_type = :categoryType"
                        + (itemFilters.category().subtypes().isEmpty() ? "" : " AND d.item_subtype IN (:categorySubtypes)"))
                    + (itemFilters.minLevel() == null ? "" : " AND d.required_level >= :minLevel")
                    + (itemFilters.maxLevel() == null ? "" : " AND d.required_level <= :maxLevel") + ")";
        }
        String baseCte = """
                WITH latest_run AS (
                    SELECT id FROM scan_run
                    WHERE state = 3 AND publishable = true
                    ORDER BY ended_at DESC NULLS LAST, started_at DESC, id DESC
                    LIMIT 1
                ), ranked_observations AS (
                    SELECT o.*,
                           ROW_NUMBER() OVER (
                               PARTITION BY COALESCE(o.shop_vid::text, 'obs:' || o.id::text)
                               ORDER BY o.observed_at DESC, o.id DESC
                           ) AS rn
                    FROM latest_run r
                    JOIN shop_observation o ON o.scan_run_id = r.id
                ), canonical_observations AS (
                    SELECT * FROM ranked_observations WHERE rn = 1
                ), matching_listings AS (
                    SELECT l.id, l.observation_id, l.item_vnum, l.item_name, l.quantity,
                           l.price_raw, l.unit_price, l.tail_field,
                           o.shop_vid, o.shop_title, o.owner_name, o.map_id, o.channel,
                           o.x, o.y, o.z, o.observed_at
                    FROM canonical_observations o
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

        // Only fixed SQL clauses are selected; request values are never interpolated.
        String order = switch (sort) {
            case PRICE_ASC -> "unit_price ASC, observed_at DESC, listing_id DESC";
            case PRICE_DESC -> "unit_price DESC, observed_at DESC, listing_id DESC";
            case QUANTITY_DESC -> "total_quantity DESC, unit_price ASC, observed_at DESC, listing_id DESC";
        };
        var dataSpec = jdbc.sql(baseCte + """
                , scored AS (
                    SELECT *, count(*) OVER() AS total_count
                    FROM aggregated
                ), page AS (
                    SELECT * FROM scored
                    ORDER BY %s
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
                ORDER BY %s
                """.formatted(order, order));
        if (hasQuery) {
            dataSpec = dataSpec.param("query", "%" + query + "%");
        }
        if (!vnums.isEmpty()) {
            dataSpec = dataSpec.param("vnums", vnums);
        }
        dataSpec = bindItemFilters(bindBonuses(dataSpec, bonuses), itemFilters);
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
            total = bindItemFilters(bindBonuses(countSpec, bonuses), itemFilters).query(Long.class).single();
        }

        return new MarketListingPage(items, total);
    }

    private static JdbcClient.StatementSpec bindItemFilters(JdbcClient.StatementSpec spec, ItemFilters filters) {
        if (filters.category() != null) {
            spec = spec.param("categoryType", filters.category().type());
            if (!filters.category().subtypes().isEmpty()) spec = spec.param("categorySubtypes", filters.category().subtypes());
        }
        if (filters.minLevel() != null) spec = spec.param("minLevel", filters.minLevel());
        if (filters.maxLevel() != null) spec = spec.param("maxLevel", filters.maxLevel());
        return spec;
    }

    private static JdbcClient.StatementSpec bindBonuses(JdbcClient.StatementSpec spec, List<BonusFilter> bonuses) {
        for (int i = 0; i < bonuses.size(); i++) {
            spec = spec.param("bonusType" + i, bonuses.get(i).type());
            if (bonuses.get(i).minimum() != null) spec = spec.param("bonusMin" + i, bonuses.get(i).minimum());
        }
        return spec;
    }

    @Override
    public List<Integer> findAvailableBonusTypes() {
        return jdbc.sql("""
                WITH latest_run AS (
                    SELECT id FROM scan_run WHERE state = 3 AND publishable = true
                    ORDER BY ended_at DESC NULLS LAST, started_at DESC, id DESC LIMIT 1
                ), ranked AS (
                    SELECT o.id, ROW_NUMBER() OVER (
                        PARTITION BY COALESCE(o.shop_vid::text, 'obs:' || o.id::text)
                        ORDER BY o.observed_at DESC, o.id DESC) AS rn
                    FROM shop_observation o JOIN latest_run r ON o.scan_run_id = r.id
                )
                SELECT DISTINCT a.attr_type FROM ranked o
                JOIN shop_listing l ON l.observation_id = o.id
                JOIN shop_listing_attribute a ON a.listing_id = l.id
                WHERE o.rn = 1 AND a.attr_type > 0 ORDER BY a.attr_type
                """).query(Integer.class).list();
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
    public List<RawListing> findCanonicalListings(List<Integer> vnums) {
        if (vnums == null || vnums.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", vnums.stream().map(vnum -> ":vnum" + vnum).toList());
        var query = jdbc.sql("""
                WITH latest_run AS (
                    SELECT id FROM scan_run
                    WHERE state = 3 AND publishable = true
                    ORDER BY ended_at DESC NULLS LAST, started_at DESC, id DESC
                    LIMIT 1
                ), ranked_observations AS (
                    SELECT o.*,
                           ROW_NUMBER() OVER (
                               PARTITION BY COALESCE(o.shop_vid::text, 'obs:' || o.id::text)
                               ORDER BY o.observed_at DESC, o.id DESC
                           ) AS rn
                    FROM latest_run r
                    JOIN shop_observation o ON o.scan_run_id = r.id
                ), canonical_observations AS (
                    SELECT * FROM ranked_observations WHERE rn = 1
                )
                SELECT l.item_vnum, l.item_name, l.unit_price, l.quantity,
                       COALESCE(o.shop_vid::text, 'obs:' || o.id::text) AS shop_key
                FROM canonical_observations o
                JOIN shop_listing l ON l.observation_id = o.id
                WHERE l.item_vnum IN (%s)
                ORDER BY l.item_vnum, l.unit_price ASC
                """.formatted(placeholders));
        for (Integer vnum : vnums) {
            query = query.param("vnum" + vnum, vnum);
        }
        return query.query((rs, row) -> new RawListing(
                rs.getInt("item_vnum"),
                rs.getString("item_name"),
                rs.getLong("unit_price"),
                rs.getInt("quantity"),
                rs.getString("shop_key")
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
