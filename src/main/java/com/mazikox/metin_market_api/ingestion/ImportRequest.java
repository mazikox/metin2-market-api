package com.mazikox.metin_market_api.ingestion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.OffsetDateTime;
import java.util.List;

public record ImportRequest(
        @NotBlank String sourceId,
        @NotBlank String batchId,
        @NotNull List<@Valid ScanRun> runs,
        @NotNull List<@Valid Observation> observations) {

    public record ScanRun(
            @NotBlank String runId,
            @NotNull OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            int state,
            @NotBlank String mapId,
            Integer channel,
            @PositiveOrZero int totalTargets,
            @PositiveOrZero int visitedTargets,
            @PositiveOrZero int failedTargets,
            Boolean publishable) {
        public boolean isPublishable() {
            return Boolean.TRUE.equals(publishable);
        }
    }

    public record Observation(
            @NotBlank String observationId,
            String runId,
            Long shopVid,
            String shopTitle,
            String ownerName,
            @NotBlank String mapId,
            Integer channel,
            double x,
            double y,
            double z,
            @NotNull OffsetDateTime observedAt,
            @NotBlank String contentFingerprint,
            @PositiveOrZero int itemCount,
            @NotNull List<@Valid Listing> listings) {}

    public record Listing(
            @NotNull @Positive Long listingId,
            @PositiveOrZero int slotIndex,
            @Positive int vnum,
            @NotBlank String itemName,
            @Positive int count,
            @PositiveOrZero long priceRaw,
            @PositiveOrZero long unitPrice,
            long tailField,
            List<@Valid Attribute> attributes,
            List<@Valid Socket> sockets) {
        public Listing {
            attributes = attributes == null ? List.of() : List.copyOf(attributes);
            sockets = sockets == null ? List.of() : List.copyOf(sockets);
        }
    }

    public record Attribute(@PositiveOrZero int slotIndex, int attrType, int attrValue) {}
    public record Socket(@PositiveOrZero int socketIndex, long socketValue) {}
}
