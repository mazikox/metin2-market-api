package com.mazikox.metin_market_api.ingestion;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/internal/v1/imports")
public class ImportController {
    private final ImportService service;
    private final byte[] expectedToken;

    public ImportController(ImportService service, @Value("${app.scanner-token}") String scannerToken) {
        this.service = service;
        this.expectedToken = scannerToken.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public ImportResponse importBatch(
            @RequestHeader(value = "X-Scanner-Token", required = false) String token,
            @Valid @RequestBody ImportRequest request) {
        byte[] supplied = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedToken, supplied)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid scanner token");
        }
        return service.importBatch(request);
    }
}
