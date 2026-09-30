package com.mazikox.metin_market_api.server.infrastructure;

import com.mazikox.metin_market_api.server.domain.GameServer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ServerRoutingFilter extends OncePerRequestFilter {
    private static final String LEGACY_ITEMS = "/api/v1/items";
    private static final String LEGACY_IMPORT = "/internal/v1/imports";
    private static final String SERVER_ITEMS_PREFIX = "/api/v1/servers/";
    private static final String SERVER_IMPORT_PREFIX = "/internal/v1/servers/";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        GameServer server;

        if (isLegacyItemsPath(path) || LEGACY_IMPORT.equals(path)) {
            server = GameServer.PANDORA;
        } else if (path.startsWith(SERVER_ITEMS_PREFIX)) {
            server = serverForItemsPath(path.substring(SERVER_ITEMS_PREFIX.length()));
            if (server == null) {
                response.sendError(HttpStatus.NOT_FOUND.value());
                return;
            }
        } else if (path.startsWith(SERVER_IMPORT_PREFIX)) {
            server = serverForImportPath(path.substring(SERVER_IMPORT_PREFIX.length()));
            if (server == null) {
                response.sendError(HttpStatus.NOT_FOUND.value());
                return;
            }
        } else {
            filterChain.doFilter(request, response);
            return;
        }

        try (ServerContext.Scope ignored = ServerContext.use(server)) {
            filterChain.doFilter(request, response);
        }
    }

    private static boolean isLegacyItemsPath(String path) {
        return LEGACY_ITEMS.equals(path) || path.startsWith(LEGACY_ITEMS + "/");
    }

    private static GameServer serverForItemsPath(String path) {
        int separator = path.indexOf('/');
        if (separator <= 0) {
            return null;
        }
        String slug = path.substring(0, separator);
        String remainder = path.substring(separator);
        if (!("/items".equals(remainder) || remainder.startsWith("/items/"))) {
            return null;
        }
        return GameServer.fromSlug(slug).orElse(null);
    }

    private static GameServer serverForImportPath(String path) {
        String suffix = "/imports";
        if (!path.endsWith(suffix) || path.indexOf('/') != path.length() - suffix.length()) {
            return null;
        }
        String slug = path.substring(0, path.length() - suffix.length());
        return GameServer.fromSlug(slug).orElse(null);
    }
}
