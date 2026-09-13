package com.mazikox.metin_market_api.market;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MarketCatalogService {
    private final JdbcClient jdbc;

    public MarketCatalogService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public ItemSuggestionsResponse suggestions(String query, Integer vnum) {
        String filter = vnum == null
                ? "lower(c.item_name) LIKE lower(:query)"
                : "c.item_vnum = :vnum AND lower(c.item_name) LIKE lower(:query)";
        String catalog = "WITH catalog AS (SELECT DISTINCT item_vnum, item_name FROM shop_listing) ";
        var count = jdbc.sql(catalog + "SELECT count(*) FROM catalog c WHERE " + filter)
                .param("query", "%" + query + "%");
        var items = jdbc.sql(catalog + "SELECT item_name, item_vnum FROM catalog c WHERE " + filter
                        + " ORDER BY lower(item_name), item_vnum LIMIT 20")
                .param("query", "%" + query + "%");
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
                LIMIT 20
                """.formatted(filter);
        var families = jdbc.sql(familiesSql).param("query", "%" + query + "%");
        if (vnum != null) {
            count = count.param("vnum", vnum);
            items = items.param("vnum", vnum);
            families = families.param("vnum", vnum);
        }

        List<ItemSuggestion> suggestions = new ArrayList<>();
        suggestions.addAll(families.query((rs, row) -> ItemSuggestion.upgradeFamily(
                rs.getString("base_name") + " +0-9", integerList(rs.getArray("member_vnums")),
                stringList(rs.getArray("member_names")))).list());
        for (ItemSuggestion item : items.query((rs, row) ->
                ItemSuggestion.item(rs.getString("item_name"), rs.getInt("item_vnum"))).list()) {
            if (suggestions.size() == 20) {
                break;
            }
            suggestions.add(item);
        }
        return new ItemSuggestionsResponse(count.query(Long.class).single(), List.copyOf(suggestions));
    }

    public ItemPriceStatisticsResponse statistics(List<Integer> vnums) {
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

        Map<Integer, List<ShopPrice>> byVnum = new LinkedHashMap<>();
        query.query((rs, row) -> new ShopPrice(rs.getInt("item_vnum"), rs.getString("item_name"),
                rs.getLong("cheapest_unit_price"), rs.getLong("raw_offer_count"), rs.getLong("total_quantity")))
                .list().forEach(price -> byVnum.computeIfAbsent(price.vnum(), ignored -> new ArrayList<>()).add(price));

        List<ItemPriceStatistics> items = new ArrayList<>();
        for (List<ShopPrice> prices : byVnum.values()) {
            prices.sort(Comparator.comparingLong(ShopPrice::price));
            BigInteger sum = prices.stream().map(price -> BigInteger.valueOf(price.price()))
                    .reduce(BigInteger.ZERO, BigInteger::add);
            BigDecimal mean = new BigDecimal(sum).divide(BigDecimal.valueOf(prices.size()), MathContext.DECIMAL128);
            int middle = prices.size() / 2;
            BigDecimal median = prices.size() % 2 == 1
                    ? BigDecimal.valueOf(prices.get(middle).price())
                    : BigDecimal.valueOf(prices.get(middle - 1).price())
                            .add(BigDecimal.valueOf(prices.get(middle).price()))
                            .divide(BigDecimal.TWO);
            ShopPrice first = prices.getFirst();
            items.add(new ItemPriceStatistics(first.vnum(), first.itemName(), first.price(), mean, median,
                    prices.size(), prices.stream().mapToLong(ShopPrice::rawOfferCount).sum(),
                    prices.stream().mapToLong(ShopPrice::totalQuantity).sum()));
        }
        return new ItemPriceStatisticsResponse(List.copyOf(items));
    }

    private static List<Integer> integerList(Array array) throws SQLException {
        Integer[] values = (Integer[]) array.getArray();
        return List.of(values);
    }

    private static List<String> stringList(Array array) throws SQLException {
        String[] values = (String[]) array.getArray();
        return List.of(values);
    }

    private record ShopPrice(int vnum, String itemName, long price, long rawOfferCount, long totalQuantity) {
    }
}
