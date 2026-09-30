package com.mazikox.metin_market_api.market.application;

import com.mazikox.metin_market_api.market.api.ItemPriceStatisticsResponse;
import com.mazikox.metin_market_api.market.application.port.MarketRepository;
import com.mazikox.metin_market_api.market.application.port.MarketRepository.ShopPrice;
import com.mazikox.metin_market_api.market.domain.ItemPriceStatistics;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GetItemStatistics {
    private final MarketRepository marketRepository;

    public GetItemStatistics(MarketRepository marketRepository) {
        this.marketRepository = marketRepository;
    }

    public ItemPriceStatisticsResponse getStatistics(List<Integer> vnums) {
        List<ShopPrice> rawPrices = marketRepository.findShopPrices(vnums);

        Map<Integer, List<ShopPrice>> byVnum = new LinkedHashMap<>();
        for (ShopPrice price : rawPrices) {
            byVnum.computeIfAbsent(price.vnum(), ignored -> new ArrayList<>()).add(price);
        }

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
}
