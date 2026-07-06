package com.payledger.platform.shared.resilience;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "payledger.resilience")
public class ResilienceProperties {

    private long maxRequestBodyBytes = 131_072;
    private RateLimit rateLimit = new RateLimit();

    public long getMaxRequestBodyBytes() {
        return maxRequestBodyBytes;
    }

    public void setMaxRequestBodyBytes(long maxRequestBodyBytes) {
        this.maxRequestBodyBytes = maxRequestBodyBytes;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public static class RateLimit {

        private boolean enabled = true;
        private Duration window = Duration.ofMinutes(1);
        private int maxRequestsPerWindow = 120;
        private List<String> moneyMovingPaths = List.of(
                "/api/v1/transfers",
                "/api/v1/payment-intents",
                "/api/v1/operations/payment-intents",
                "/api/v1/operations/settlements"
        );

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }

        public int getMaxRequestsPerWindow() {
            return maxRequestsPerWindow;
        }

        public void setMaxRequestsPerWindow(int maxRequestsPerWindow) {
            this.maxRequestsPerWindow = maxRequestsPerWindow;
        }

        public List<String> getMoneyMovingPaths() {
            return moneyMovingPaths;
        }

        public void setMoneyMovingPaths(List<String> moneyMovingPaths) {
            this.moneyMovingPaths = moneyMovingPaths;
        }
    }
}
