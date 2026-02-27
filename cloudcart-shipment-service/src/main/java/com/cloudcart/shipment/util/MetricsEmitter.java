package com.cloudcart.shipment.util;

import java.time.Instant;

/**
 * Emits CloudWatch metrics using Embedded Metrics Format (EMF).
 * Lambda captures stdout to CloudWatch Logs, which auto-publishes EMF metrics.
 * No additional SDK dependency required.
 */
public class MetricsEmitter {

    private final String namespace;

    public MetricsEmitter(String namespace) {
        this.namespace = namespace;
    }

    public void count(String metricName) {
        emit(metricName, 1.0, "Count");
    }

    public void emit(String metricName, double value, String unit) {
        String emf = String.format(
                "{\"_aws\":{\"Timestamp\":%d,\"CloudWatchMetrics\":[{\"Namespace\":\"%s\",\"Dimensions\":[[]],\"Metrics\":[{\"Name\":\"%s\",\"Unit\":\"%s\"}]}]},\"%s\":%.1f}",
                Instant.now().toEpochMilli(),
                namespace,
                metricName,
                unit,
                metricName,
                value
        );
        System.out.println(emf);
    }
}
