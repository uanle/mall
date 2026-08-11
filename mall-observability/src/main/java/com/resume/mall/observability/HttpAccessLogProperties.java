package com.resume.mall.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties("mall.observability.access-log")
public class HttpAccessLogProperties {
    private boolean enabled = true;
    private double successSampleRate = 1.0;
    private Duration slowThreshold = Duration.ofSeconds(1);
    private List<String> excludedPathPrefixes = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getSuccessSampleRate() {
        return successSampleRate;
    }

    public void setSuccessSampleRate(double successSampleRate) {
        if (successSampleRate < 0.0 || successSampleRate > 1.0) {
            throw new IllegalArgumentException("successSampleRate must be between 0.0 and 1.0");
        }
        this.successSampleRate = successSampleRate;
    }

    public Duration getSlowThreshold() {
        return slowThreshold;
    }

    public void setSlowThreshold(Duration slowThreshold) {
        this.slowThreshold = slowThreshold;
    }

    public List<String> getExcludedPathPrefixes() {
        return excludedPathPrefixes;
    }

    public void setExcludedPathPrefixes(List<String> excludedPathPrefixes) {
        this.excludedPathPrefixes = excludedPathPrefixes == null
                ? new ArrayList<>()
                : new ArrayList<>(excludedPathPrefixes);
    }

    boolean isExcluded(String path) {
        return path != null && excludedPathPrefixes.stream().anyMatch(path::startsWith);
    }
}
