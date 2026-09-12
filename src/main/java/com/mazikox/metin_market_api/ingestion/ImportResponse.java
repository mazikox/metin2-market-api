package com.mazikox.metin_market_api.ingestion;

public record ImportResponse(
        String sourceId,
        String batchId,
        boolean alreadyProcessed,
        int importedRuns,
        int importedObservations,
        int importedListings) {}
