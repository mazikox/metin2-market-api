package com.mazikox.metin_market_api.ingestion;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

@Service
public class ImportService {
    private final JdbcClient jdbc;
    private final ObjectMapper canonicalMapper;

    public ImportService(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.canonicalMapper = objectMapper;
    }

    @Transactional
    public ImportResponse importBatch(ImportRequest request) {
        String payloadHash = hash(request);
        int claimed = jdbc.sql("""
                INSERT INTO synchronization_batch
                    (source_id, external_batch_id, payload_sha256, imported_runs,
                     imported_observations, imported_listings)
                VALUES (:sourceId, :batchId, :hash, 0, 0, 0)
                ON CONFLICT (source_id, external_batch_id) DO NOTHING
                """).params(Map.of("sourceId", request.sourceId(), "batchId", request.batchId(),
                        "hash", payloadHash)).update();
        if (claimed == 0) {
            return replayOrReject(request, payloadHash,
                    findBatch(request.sourceId(), request.batchId()));
        }

        int importedRuns = 0;
        int importedObservations = 0;
        int importedListings = 0;

        for (ImportRequest.ScanRun run : request.runs()) {
            importedRuns += jdbc.sql("""
                    INSERT INTO scan_run
                        (source_id, source_run_id, started_at, ended_at, state, map_id, channel,
                         total_targets, visited_targets, failed_targets, publishable)
                    VALUES (:sourceId, :runId, :startedAt, :endedAt, :state, :mapId, :channel,
                            :totalTargets, :visitedTargets, :failedTargets, :publishable)
                    ON CONFLICT (source_id, source_run_id) DO UPDATE SET
                        ended_at = EXCLUDED.ended_at,
                        state = EXCLUDED.state,
                        map_id = EXCLUDED.map_id,
                        channel = EXCLUDED.channel,
                        total_targets = EXCLUDED.total_targets,
                        visited_targets = EXCLUDED.visited_targets,
                        failed_targets = EXCLUDED.failed_targets,
                        publishable = EXCLUDED.publishable
                    """)
                    .params(params(
                            "sourceId", request.sourceId(), "runId", run.runId(),
                            "startedAt", run.startedAt(), "endedAt", run.endedAt(),
                            "state", run.state(), "mapId", run.mapId(),
                            "channel", run.channel(), "totalTargets", run.totalTargets(),
                            "visitedTargets", run.visitedTargets(), "failedTargets", run.failedTargets(),
                            "publishable", run.isPublishable()))
                    .update();
        }

        for (ImportRequest.Observation observation : request.observations()) {
            if (observation.itemCount() != observation.listings().size()) {
                throw new ImportConflictException("Observation " + observation.observationId()
                        + " reports " + observation.itemCount() + " items but contains "
                        + observation.listings().size() + " listings");
            }
            String observationHash = hash(observation);
            Long runPk = observation.runId() == null ? null : jdbc.sql("""
                    SELECT id FROM scan_run WHERE source_id = :sourceId AND source_run_id = :runId
                    """).param("sourceId", request.sourceId()).param("runId", observation.runId())
                    .query(Long.class).optional().orElseThrow(() ->
                            new ImportConflictException("Observation references unknown run " + observation.runId()));

            Long observationPk = jdbc.sql("""
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
                            "sourceId", request.sourceId(),
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
                    .query(Long.class).optional().orElse(null);

            if (observationPk == null) {
                ExistingObservation stored = jdbc.sql("""
                        SELECT content_fingerprint, source_payload_sha256 FROM shop_observation
                        WHERE source_id = :sourceId AND source_observation_id = :observationId
                        """).param("sourceId", request.sourceId()).param("observationId", observation.observationId())
                        .query((rs, row) -> new ExistingObservation(rs.getString(1), rs.getString(2))).single();
                if (!stored.fingerprint().equals(observation.contentFingerprint())
                        || !stored.payloadHash().equals(observationHash)) {
                    throw new ImportConflictException("Observation " + observation.observationId()
                            + " was already imported with a different content fingerprint");
                }
                continue;
            }

            importedObservations++;
            for (ImportRequest.Listing listing : observation.listings()) {
                Long listingPk = jdbc.sql("""
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
                importedListings++;

                for (ImportRequest.Attribute attribute : listing.attributes()) {
                    jdbc.sql("""
                            INSERT INTO shop_listing_attribute (listing_id, slot_index, attr_type, attr_value)
                            VALUES (:listingPk, :slotIndex, :attrType, :attrValue)
                            """).params(Map.of(
                                    "listingPk", listingPk, "slotIndex", attribute.slotIndex(),
                                    "attrType", attribute.attrType(), "attrValue", attribute.attrValue())).update();
                }
                for (ImportRequest.Socket socket : listing.sockets()) {
                    jdbc.sql("""
                            INSERT INTO shop_listing_socket (listing_id, socket_index, socket_value)
                            VALUES (:listingPk, :socketIndex, :socketValue)
                            """).params(Map.of(
                                    "listingPk", listingPk, "socketIndex", socket.socketIndex(),
                                    "socketValue", socket.socketValue())).update();
                }
            }
        }

        jdbc.sql("""
                UPDATE synchronization_batch
                SET imported_runs = :runs, imported_observations = :observations, imported_listings = :listings
                WHERE source_id = :sourceId AND external_batch_id = :batchId
                """).params(Map.of("sourceId", request.sourceId(), "batchId", request.batchId(),
                        "runs", importedRuns, "observations", importedObservations,
                        "listings", importedListings)).update();
        return new ImportResponse(request.sourceId(), request.batchId(), false,
                importedRuns, importedObservations, importedListings);
    }

    private ImportResponse replayOrReject(ImportRequest request, String payloadHash, ExistingBatch existing) {
        if (!MessageDigest.isEqual(payloadHash.getBytes(StandardCharsets.US_ASCII),
                existing.hash().getBytes(StandardCharsets.US_ASCII))) {
            throw new ImportConflictException("Batch ID was already used with a different payload");
        }
        return new ImportResponse(request.sourceId(), request.batchId(), true,
                existing.runs(), existing.observations(), existing.listings());
    }

    private ExistingBatch findBatch(String sourceId, String batchId) {
        return jdbc.sql("""
                SELECT payload_sha256, imported_runs, imported_observations, imported_listings
                FROM synchronization_batch WHERE source_id = :sourceId AND external_batch_id = :batchId
                """).param("sourceId", sourceId).param("batchId", batchId)
                .query((rs, row) -> new ExistingBatch(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)))
                .optional().orElse(null);
    }

    private String hash(Object request) {
        try {
            byte[] json = canonicalMapper.writeValueAsBytes(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JacksonException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot hash synchronization payload", e);
        }
    }

    private Map<String, Object> params(Object... keyValues) {
        Map<String, Object> parameters = new HashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            parameters.put((String) keyValues[index], keyValues[index + 1]);
        }
        return parameters;
    }

    private record ExistingBatch(String hash, int runs, int observations, int listings) {}
    private record ExistingObservation(String fingerprint, String payloadHash) {}
}
