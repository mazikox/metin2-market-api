package com.mazikox.metin_market_api.market.application;

import com.mazikox.metin_market_api.market.api.ItemPriceStatisticsResponse;
import com.mazikox.metin_market_api.market.application.port.MarketRepository;
import com.mazikox.metin_market_api.market.domain.ItemPriceStatistics;
import com.mazikox.metin_market_api.market.domain.ItemPriceStatisticsCalculator;
import com.mazikox.metin_market_api.market.domain.ItemPriceStatisticsCalculator.RawListing;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
        return getStatistics(vnums, List.of());
    }
    public ItemPriceStatisticsResponse getStatistics(List<Integer> vnums, List<String> maps) {
        if (vnums == null || vnums.isEmpty()) {
            return new ItemPriceStatisticsResponse(List.of());
        }

        List<RawListing> rawListings = maps.isEmpty() ? marketRepository.findCanonicalListings(vnums) : marketRepository.findCanonicalListings(vnums, maps);

        Map<Integer, List<RawListing>> byVnum = new LinkedHashMap<>();
        for (RawListing listing : rawListings) {
            byVnum.computeIfAbsent(listing.vnum(), ignored -> new ArrayList<>()).add(listing);
        }

        List<ItemPriceStatistics> items = new ArrayList<>();
        for (Integer vnum : vnums) {
            List<RawListing> listingsForVnum = byVnum.get(vnum);
            if (listingsForVnum != null && !listingsForVnum.isEmpty()) {
                ItemPriceStatistics stats = ItemPriceStatisticsCalculator.calculate(vnum, listingsForVnum);
                if (stats != null) {
                    items.add(stats);
                }
            }
        }
        return new ItemPriceStatisticsResponse(List.copyOf(items));
    }
}
