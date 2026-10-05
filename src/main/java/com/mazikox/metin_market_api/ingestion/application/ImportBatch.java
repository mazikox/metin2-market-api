package com.mazikox.metin_market_api.ingestion.application;

import com.mazikox.metin_market_api.ingestion.api.ImportRequest;
import com.mazikox.metin_market_api.ingestion.api.ImportResponse;
import com.mazikox.metin_market_api.ingestion.application.port.ImportRepository;
import com.mazikox.metin_market_api.ingestion.application.port.ImportRepository.ExistingBatch;
import com.mazikox.metin_market_api.ingestion.application.port.ImportRepository.ExistingObservation;
import com.mazikox.metin_market_api.ingestion.domain.ImportConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class ImportBatch {
    private final ImportRepository repository;
    private final ObjectMapper canonicalMapper;

    public ImportBatch(ImportRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.canonicalMapper = objectMapper;
    }

    @Transactional
    public ImportResponse importBatch(ImportRequest request) {
        String payloadHash = hash(request);
        int claimed = repository.claimBatch(request.sourceId(), request.batchId(), payloadHash);
        if (claimed == 0) {
            ExistingBatch existing = repository.findBatch(request.sourceId(), request.batchId())
                    .orElse(null);
            return replayOrReject(request, payloadHash, existing);
        }

        int importedRuns = 0;
        int importedObservations = 0;
        int importedListings = 0;

        for (ImportRequest.ScanRun run : request.runs()) {
            int affected = repository.upsertScanRun(request.sourceId(), run);
            if (affected == 0) throw new ImportConflictException("An existing scan cannot change map");
            importedRuns += affected;
        }

        for (ImportRequest.Observation observation : request.observations()) {
            if (observation.itemCount() != observation.listings().size()) {
                throw new ImportConflictException("Observation " + observation.observationId()
                        + " reports " + observation.itemCount() + " items but contains "
                        + observation.listings().size() + " listings");
            }
            String observationHash = hash(observation);
            Long runPk = null;
            if (observation.runId() != null) {
                var run = repository.findScanRun(request.sourceId(), observation.runId())
                        .orElseThrow(() -> new ImportConflictException("Observation references unknown run " + observation.runId()));
                if (!run.mapId().equals(observation.mapId()))
                    throw new ImportConflictException("Observation map differs from scan map");
                runPk = run.id();
            }

            Long observationPk = repository.insertObservation(request.sourceId(), runPk, observation, observationHash)
                    .orElse(null);

            if (observationPk == null) {
                ExistingObservation stored = repository.findExistingObservation(request.sourceId(), observation.observationId())
                        .orElseThrow(() -> new IllegalStateException("Existing observation could not be found"));
                if (!stored.fingerprint().equals(observation.contentFingerprint())
                        || !stored.payloadHash().equals(observationHash)) {
                    throw new ImportConflictException("Observation " + observation.observationId()
                            + " was already imported with a different content fingerprint");
                }
                continue;
            }

            importedObservations++;
            for (ImportRequest.Listing listing : observation.listings()) {
                long listingPk = repository.insertListing(observationPk, listing);
                importedListings++;

                for (ImportRequest.Attribute attribute : listing.attributes()) {
                    repository.insertListingAttribute(listingPk, attribute);
                }
                for (ImportRequest.Socket socket : listing.sockets()) {
                    repository.insertListingSocket(listingPk, socket);
                }
            }
        }

        repository.updateBatchCounts(request.sourceId(), request.batchId(),
                importedRuns, importedObservations, importedListings);
        return new ImportResponse(request.sourceId(), request.batchId(), false,
                importedRuns, importedObservations, importedListings);
    }

    private ImportResponse replayOrReject(ImportRequest request, String payloadHash, ExistingBatch existing) {
        if (existing == null || !MessageDigest.isEqual(payloadHash.getBytes(StandardCharsets.US_ASCII),
                existing.hash().getBytes(StandardCharsets.US_ASCII))) {
            throw new ImportConflictException("Batch ID was already used with a different payload");
        }
        return new ImportResponse(request.sourceId(), request.batchId(), true,
                existing.runs(), existing.observations(), existing.listings());
    }

    private String hash(Object request) {
        try {
            byte[] json = canonicalMapper.writeValueAsBytes(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JacksonException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot hash synchronization payload", e);
        }
    }
}
