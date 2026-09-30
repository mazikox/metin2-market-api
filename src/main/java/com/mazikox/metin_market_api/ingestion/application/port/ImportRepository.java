package com.mazikox.metin_market_api.ingestion.application.port;

import com.mazikox.metin_market_api.ingestion.api.ImportRequest;

import java.util.Optional;

public interface ImportRepository {

    record ExistingBatch(String hash, int runs, int observations, int listings) {}
    record ExistingObservation(String fingerprint, String payloadHash) {}

    int claimBatch(String sourceId, String batchId, String payloadHash);

    Optional<ExistingBatch> findBatch(String sourceId, String batchId);

    int upsertScanRun(String sourceId, ImportRequest.ScanRun run);

    Optional<Long> findScanRunId(String sourceId, String sourceRunId);

    Optional<Long> insertObservation(String sourceId, Long runPk, ImportRequest.Observation observation, String observationHash);

    Optional<ExistingObservation> findExistingObservation(String sourceId, String observationId);

    long insertListing(long observationPk, ImportRequest.Listing listing);

    void insertListingAttribute(long listingPk, ImportRequest.Attribute attribute);

    void insertListingSocket(long listingPk, ImportRequest.Socket socket);

    void updateBatchCounts(String sourceId, String batchId, int runs, int observations, int listings);
}
