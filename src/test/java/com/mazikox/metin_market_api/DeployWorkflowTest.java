package com.mazikox.metin_market_api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeployWorkflowTest {

    @Test
    void deployWorkflowHealthCheckFailsWhenExhausted() throws IOException {
        Path workflowPath = Path.of(".github", "workflows", "deploy.yml");
        assertTrue(Files.exists(workflowPath), "deploy.yml must exist at .github/workflows/deploy.yml");

        String content = Files.readString(workflowPath);

        // Verify health check exists and includes all 3 servers
        assertTrue(content.contains("name: Health check"), "Must have Health check step");
        assertTrue(content.contains("servers/pandora/items"), "Health check must query Pandora");
        assertTrue(content.contains("servers/elder/items"), "Health check must query Elder");
        assertTrue(content.contains("servers/beavium/items"), "Health check must query Beavium");

        // Verify health check exits with 0 on success and exits with 1 when loop finishes without success
        int healthCheckIdx = content.indexOf("name: Health check");
        int nextStepIdx = content.indexOf("name: Verify Caddy routing smoke test", healthCheckIdx);
        assertTrue(healthCheckIdx > 0 && nextStepIdx > healthCheckIdx, "Health check step must precede smoke test");

        String healthCheckStep = content.substring(healthCheckIdx, nextStepIdx);
        assertTrue(healthCheckStep.contains("exit 0"), "Successful health check must exit 0");
        assertTrue(healthCheckStep.contains("exit 1"), "Failed health check must exit 1 after loop");
        assertFalse(healthCheckStep.contains("break\n"), "Health check must not silently break from retry loop");

        // Verify Caddy routing smoke test includes repeated server params
        String smokeTestStep = content.substring(nextStepIdx);
        assertTrue(smokeTestStep.contains("?server=pandora&server=elder"), "Smoke test must verify repeated server params");
        assertTrue(smokeTestStep.contains("?server=elder&server=pandora"), "Smoke test must verify repeated server params");
        assertTrue(smokeTestStep.contains("elder\" \"beavium\""), "Smoke test must verify 404 error routes");
    }
}
