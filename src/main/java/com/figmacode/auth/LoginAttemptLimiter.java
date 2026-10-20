package com.figmacode.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.figmacode.config.AppProperties;

/**
 * Stops this login form from being used to lock accounts out of Bitbucket:
 * after N failures for a username within the window, further attempts are refused locally.
 */
@Component
public class LoginAttemptLimiter {

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final AppProperties.Security config;
    private final Clock clock;

    @Autowired
    public LoginAttemptLimiter(AppProperties props) {
        this(props, Clock.systemUTC());
    }

    LoginAttemptLimiter(AppProperties props, Clock clock) {
        this.config = props.getSecurity();
        this.clock = clock;
    }

    public boolean isBlocked(String username) {
        Deque<Instant> attempts = failures.get(key(username));
        if (attempts == null) {
            return false;
        }
        prune(attempts);
        return attempts.size() >= config.getMaxFailedLoginsPerWindow();
    }

    public void recordFailure(String username) {
        Deque<Instant> attempts = failures.computeIfAbsent(key(username), k -> new ConcurrentLinkedDeque<>());
        attempts.addLast(clock.instant());
        prune(attempts);
    }

    public void recordSuccess(String username) {
        failures.remove(key(username));
    }

    private void prune(Deque<Instant> attempts) {
        Instant cutoff = clock.instant().minus(config.getFailedLoginWindow());
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
            attempts.pollFirst();
        }
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
