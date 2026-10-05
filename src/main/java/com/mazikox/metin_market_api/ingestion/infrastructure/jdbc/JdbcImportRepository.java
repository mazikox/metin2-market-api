package com.mazikox.metin_market_api.ingestion.infrastructure.jdbc;

import com.mazikox.metin_market_api.ingestion.api.ImportRequest;
import com.mazikox.metin_market_api.ingestion.application.port.ImportRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcImportRepository implements ImportRepository {
    private final JdbcClient jdbc;

    public JdbcImportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int claimBatch(String sourceId, String batchId, String payloadHash) {
        return jdbc.sql("""
                INSERT INTO synchronization_batch
                    (source_id, external_batch_id, payload_sha256, imported_runs,
                     imported_observations, imported_listings)
                VALUES (:sourceId, :batchId, :hash, 0, 0, 0)
                ON CONFLICT (source_id, external_batch_id) DO NOTHING
                """).params(Map.of("sourceId", sourceId, "batchId", batchId,
                        "hash", payloadHash)).update();
    }

    @Override
    public Optional<ExistingBatch> findBatch(String sourceId, String batchId) {
        return jdbc.sql("""
                SELECT payload_sha256, imported_runs, imported_observations, imported_listings
                FROM synchronization_batch WHERE source_id = :sourceId AND external_batch_id = :batchId
                """).param("sourceId", sourceId).param("batchId", batchId)
                .query((rs, row) -> new ExistingBatch(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)))
                .optional();
    }

    @Override
    public int upsertScanRun(String sourceId, ImportRequest.ScanRun run) {
        return jdbc.sql("""
                INSERT INTO scan_run
                    (source_id, source_run_id, started_at, ended_at, state, map_id, channel,
                     total_targets, visited_targets, failed_targets, publishable, expected_observations)
                VALUES (:sourceId, :runId, :startedAt, :endedAt, :state, :mapId, :channel,
                        :totalTargets, :visitedTargets, :failedTargets, :publishable, :expectedObservations)
                ON CONFLICT (source_id, source_run_id) DO UPDATE SET
                    ended_at = EXCLUDED.ended_at,
                    state = EXCLUDED.state,
                    map_id = EXCLUDED.map_id,
                    channel = EXCLUDED.channel,
                    total_targets = EXCLUDED.total_targets,
                    visited_targets = EXCLUDED.visited_targets,
                    failed_targets = EXCLUDED.failed_targets,
                    publishable = EXCLUDED.publishable,
                    expected_observations = EXCLUDED.expected_observations
                WHERE scan_run.map_id = EXCLUDED.map_id
                """)
                .params(params(
                        "sourceId", sourceId, "runId", run.runId(),
                        "startedAt", run.startedAt(), "endedAt", run.endedAt(),
                        "state", run.state(), "mapId", run.mapId(),
                        "channel", run.channel(), "totalTargets", run.totalTargets(),
                        "visitedTargets", run.visitedTargets(), "failedTargets", run.failedTargets(),
                        "publishable", run.isPublishable(), "expectedObservations", run.expectedObservations()))
                .update();
    }

    @Override
    public Optional<ExistingScanRun> findScanRun(String sourceId, String sourceRunId) {
        return jdbc.sql("""
                SELECT id, map_id FROM scan_run WHERE source_id = :sourceId AND source_run_id = :runId
                """).param("sourceId", sourceId).param("runId", sourceRunId)
                .query((rs, row) -> new ExistingScanRun(rs.getLong(1), rs.getString(2))).optional();
    }

    @Override
    public Optional<Long> insertObservation(String sourceId, Long runPk, ImportRequest.Observation observation, String observationHash) {
        return jdbc.sql("""
                INSERT INTO shop_observation
                    (source_id, source_observation_id, scan_run_id, shop_vid, shop_title, owner_name,
                     map_id, channel, x, y, z, observed_at, content_fingerprint,
                     source_payload_sha256, reported_item_count)
                VALUES (:sourceId, :observationId, :runPk, :shopVid, :shopTitle, :ownerName,
                        :mapId, :channel, :x, :y, :z, :observedAt, :contentFingerprint,
                        :observationHash, :itemCount)
                ON CONFLICT (source_id, source_observation_id) DO NOTHING
                RETURNING id
                """).params(params(
                        "sourceId", sourceId,
                        "observationId", observation.observationId(),
                        "runPk", runPk, "shopVid", observation.shopVid(),
                        "shopTitle", observation.shopTitle(),
                        "ownerName", observation.ownerName(),
                        "mapId", observation.mapId(),
                        "channel", observation.channel(), "x", observation.x(),
                        "y", observation.y(), "z", observation.z(),
                        "observedAt", observation.observedAt(),
                        "contentFingerprint", observation.contentFingerprint(),
                        "observationHash", observationHash,
                        "itemCount", observation.itemCount()))
                .query(Long.class).optional();
    }

    @Override
    public Optional<ExistingObservation> findExistingObservation(String sourceId, String observationId) {
        return jdbc.sql("""
                SELECT content_fingerprint, source_payload_sha256 FROM shop_observation
                WHERE source_id = :sourceId AND source_observation_id = :observationId
                """).param("sourceId", sourceId).param("observationId", observationId)
                .query((rs, row) -> new ExistingObservation(rs.getString(1), rs.getString(2))).optional();
    }

    @Override
    public long insertListing(long observationPk, ImportRequest.Listing listing) {
        return jdbc.sql("""
                INSERT INTO shop_listing
                    (observation_id, source_listing_id, slot_index, item_vnum, item_name,
                     quantity, price_raw, unit_price, tail_field)
                VALUES (:observationPk, :listingId, :slotIndex, :vnum, :itemName,
                        :count, :priceRaw, :unitPrice, :tailField)
                RETURNING id
            """).params(Map.of(
                    "observationPk", observationPk, "listingId", listing.listingId(),
                    "slotIndex", listing.slotIndex(), "vnum", listing.vnum(),
                    "itemName", listing.itemName(), "count", listing.count(),
                    "priceRaw", listing.priceRaw(), "unitPrice", listing.unitPrice(),
                    "tailField", listing.tailField())).query(Long.class).single();
    }

    @Override
    public void insertListingAttribute(long listingPk, ImportRequest.Attribute attribute) {
        jdbc.sql("""
                INSERT INTO shop_listing_attribute (listing_id, slot_index, attr_type, attr_value)
                VALUES (:listingPk, :slotIndex, :attrType, :attrValue)
                """).params(Map.of(
                        "listingPk", listingPk, "slotIndex", attribute.slotIndex(),
                        "attrType", attribute.attrType(), "attrValue", attribute.attrValue())).update();
    }

    @Override
    public void insertListingSocket(long listingPk, ImportRequest.Socket socket) {
        jdbc.sql("""
                INSERT INTO shop_listing_socket (listing_id, socket_index, socket_value)
                VALUES (:listingPk, :socketIndex, :socketValue)
                """).params(Map.of(
                        "listingPk", listingPk, "socketIndex", socket.socketIndex(),
                        "socketValue", socket.socketValue())).update();
    }

    @Override
    public void updateBatchCounts(String sourceId, String batchId, int runs, int observations, int listings) {
        jdbc.sql("""
                UPDATE synchronization_batch
                SET imported_runs = :runs, imported_observations = :observations, imported_listings = :listings
                WHERE source_id = :sourceId AND external_batch_id = :batchId
                """).params(Map.of("sourceId", sourceId, "batchId", batchId,
                        "runs", runs, "observations", observations,
                        "listings", listings)).update();
    }

    private Map<String, Object> params(Object... keyValues) {
        Map<String, Object> parameters = new HashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            parameters.put((String) keyValues[index], keyValues[index + 1]);
        }
        return parameters;
    }
}
