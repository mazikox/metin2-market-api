package com.mazikox.metin_market_api.ingestion;

import jakarta.validation.Valid;
import com.mazikox.metin_market_api.server.ServerContext;
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
@RequestMapping({"/internal/v1/imports", "/internal/v1/servers/{server}/imports"})
public class ImportController {
    private final ImportService service;
    private final byte[] pandoraToken;
    private final byte[] elderToken;
    private final byte[] beaviumToken;

    public ImportController(
            ImportService service,
            @Value("${app.scanner-token.pandora}") String pandoraToken,
            @Value("${app.scanner-token.elder}") String elderToken,
            @Value("${app.scanner-token.beavium}") String beaviumToken) {
        this.service = service;
        this.pandoraToken = pandoraToken.getBytes(StandardCharsets.UTF_8);
        this.elderToken = elderToken.getBytes(StandardCharsets.UTF_8);
        this.beaviumToken = beaviumToken.getBytes(StandardCharsets.UTF_8);
        if (pandoraToken.isBlank() || elderToken.isBlank() || beaviumToken.isBlank()
                || MessageDigest.isEqual(this.pandoraToken, this.elderToken)
                || MessageDigest.isEqual(this.pandoraToken, this.beaviumToken)
                || MessageDigest.isEqual(this.elderToken, this.beaviumToken)) {
            throw new IllegalStateException("Configure a non-empty, unique scanner token for each game server");
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public ImportResponse importBatch(
            @RequestHeader(value = "X-Scanner-Token", required = false) String token,
            @Valid @RequestBody ImportRequest request) {
        byte[] expectedToken = switch (ServerContext.requireCurrent()) {
            case PANDORA -> pandoraToken;
            case ELDER -> elderToken;
            case BEAVIUM -> beaviumToken;
        };
        byte[] supplied = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedToken, supplied)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid scanner token");
        }
        return service.importBatch(request);
    }
}
