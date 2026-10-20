package com.figmacode.figma;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.figmacode.config.AppProperties;
import com.figmacode.mcp.McpException;

/** Decides what the dashboard's Figma card shows, by actually talking to the MCP server. */
@Service
public class FigmaStatusService {

    private static final Logger log = LoggerFactory.getLogger(FigmaStatusService.class);
    public static final String DESIGN_TOOL = "get_design_context";

    public enum State { NOT_CONNECTED, CONNECTED, EXPIRED, TOOL_MISSING, UNREACHABLE }

    public record Status(State state, String account, List<String> tools, String message, Instant checkedAt) {
        public boolean connected() {
            return state == State.CONNECTED;
        }
    }

    private final FigmaTokenService tokens;
    private final FigmaMcpGateway gateway;
    private final AppProperties.Figma figma;
    private final Map<String, Status> cache = new ConcurrentHashMap<>();

    public FigmaStatusService(FigmaTokenService tokens, FigmaMcpGateway gateway, AppProperties props) {
        this.tokens = tokens;
        this.gateway = gateway;
        this.figma = props.getFigma();
    }

    public Status status(String username, boolean forceRefresh) {
        Status cached = cache.get(username);
        if (!forceRefresh && cached != null && cached.connected()
                && cached.checkedAt().plus(figma.getStatusCacheTtl()).isAfter(Instant.now())) {
            return cached;
        }
        Status fresh = check(username);
        if (fresh.connected()) {
            cache.put(username, fresh);
        } else {
            cache.remove(username);
        }
        return fresh;
    }

    public void evict(String username) {
        cache.remove(username);
    }

    private Status check(String username) {
        Optional<FigmaConnection> connection = tokens.find(username);
        if (connection.isEmpty()) {
            return status(State.NOT_CONNECTED, null, List.of(), "Connect your Figma account to get started.");
        }
        Optional<String> token = tokens.validAccessToken(username);
        if (token.isEmpty()) {
            return status(State.EXPIRED, connection.get().getFigmaAccount(), List.of(),
                    "Your Figma connection expired. Reconnect to continue.");
        }
        try {
            FigmaMcpGateway.Probe probe = probeWithRetry(username, token.get());
            if (probe == null) {
                return status(State.EXPIRED, connection.get().getFigmaAccount(), List.of(),
                        "Figma rejected the saved token. Reconnect to continue.");
            }
            String account = probe.account() != null ? probe.account() : connection.get().getFigmaAccount();
            if (probe.account() != null) {
                tokens.setAccount(username, probe.account());
            }
            if (!probe.tools().contains(DESIGN_TOOL)) {
                return status(State.TOOL_MISSING, account, probe.tools(),
                        "Connected, but the Figma MCP server does not offer " + DESIGN_TOOL + ".");
            }
            return status(State.CONNECTED, account, probe.tools(), "Figma MCP is connected.");
        } catch (McpException e) {
            log.warn("Figma MCP status check failed for {}: {}", username, e.getMessage());
            return status(State.UNREACHABLE, connection.get().getFigmaAccount(), List.of(),
                    "Figma MCP could not be reached: " + e.getMessage());
        }
    }

    /** Probes once; on 401 refreshes the token and probes again. Null means the token is dead. */
    private FigmaMcpGateway.Probe probeWithRetry(String username, String token) {
        try {
            return gateway.probe(token);
        } catch (McpException.Unauthorized e) {
            Optional<String> refreshed = tokens.forceRefresh(username);
            if (refreshed.isEmpty()) {
                return null;
            }
            try {
                return gateway.probe(refreshed.get());
            } catch (McpException.Unauthorized again) {
                return null;
            }
        }
    }

    private static Status status(State state, String account, List<String> tools, String message) {
        return new Status(state, account, tools, message, Instant.now());
    }
}
