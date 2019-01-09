package org.eclipse.ditto.services.connectivity.messaging.metrics;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.connectivity.messaging.metrics.ConnectivityCounterRegistry.Direction.INBOUND;
import static org.eclipse.ditto.services.connectivity.messaging.metrics.ConnectivityCounterRegistry.Direction.OUTBOUND;
import static org.eclipse.ditto.services.connectivity.messaging.metrics.ConnectivityCounterRegistry.Metric.CONSUMED;
import static org.eclipse.ditto.services.connectivity.messaging.metrics.ConnectivityCounterRegistry.Metric.FILTERED;
import static org.eclipse.ditto.services.connectivity.messaging.metrics.ConnectivityCounterRegistry.Metric.MAPPED;
import static org.eclipse.ditto.services.connectivity.messaging.metrics.ConnectivityCounterRegistry.Metric.PUBLISHED;
import static org.eclipse.ditto.services.connectivity.messaging.metrics.ConnectivityCounterRegistry.Metric.RESPONDED;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.eclipse.ditto.model.connectivity.ImmutableMeasurement;
import org.eclipse.ditto.model.connectivity.Measurement;
import org.eclipse.ditto.model.connectivity.SourceMetrics;
import org.eclipse.ditto.model.connectivity.TargetMetrics;
import org.junit.BeforeClass;
import org.junit.Test;

public class ConnectivityCounterRegistryTest {

    public static final String CONNECTION_ID = "theConnection";
    public static final String SOURCE = "source1";
    public static final String TARGET = "target1";
    public static final Instant FIXED_INSTANT = Instant.now();
    public static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneId.systemDefault());
    public static final Map<Duration, Long> COUNTERS = getCounters(1);

    private static Map<Duration, Long> getCounters(final long value) {
        return Stream.of(MeasurementWindow.values())
                .map(MeasurementWindow::getWindow)
                .collect(toMap(d -> d, d -> value));
    }

    @BeforeClass
    public static void setUp() {
        Stream.of(
                ConnectivityCounterRegistry.getCounter(FIXED_CLOCK, CONNECTION_ID, CONSUMED, INBOUND, SOURCE),
                ConnectivityCounterRegistry.getCounter(FIXED_CLOCK, CONNECTION_ID, MAPPED, INBOUND, SOURCE),
                ConnectivityCounterRegistry.getCounter(FIXED_CLOCK, CONNECTION_ID, RESPONDED, INBOUND, SOURCE),
                ConnectivityCounterRegistry.getCounter(FIXED_CLOCK, CONNECTION_ID, CONSUMED, OUTBOUND, TARGET),
                ConnectivityCounterRegistry.getCounter(FIXED_CLOCK, CONNECTION_ID, FILTERED, OUTBOUND, TARGET),
                ConnectivityCounterRegistry.getCounter(FIXED_CLOCK, CONNECTION_ID, MAPPED, OUTBOUND, TARGET),
                ConnectivityCounterRegistry.getCounter(FIXED_CLOCK, CONNECTION_ID, PUBLISHED, OUTBOUND, TARGET)
        ).forEach(counter ->
                // just to have some different values...
                IntStream.rangeClosed(0, counter.getMetric().ordinal()).forEach(i -> {
                    counter.recordSuccess();
                    counter.recordFailure();
                }));
    }

    @Test
    public void testAggregateSourceMetrics() {

        final SourceMetrics sourceMetrics = ConnectivityCounterRegistry.aggregateSourceMetrics(CONNECTION_ID);

        final Measurement[] expected = {
                getMeasurement(MAPPED, true),
                getMeasurement(MAPPED, false),
                getMeasurement(CONSUMED, true),
                getMeasurement(CONSUMED, false),
                getMeasurement(RESPONDED, true),
                getMeasurement(RESPONDED, false)
        };

        assertThat(sourceMetrics.getAddressMetrics().get(SOURCE).getMeasurements()).containsExactlyInAnyOrder(expected);

    }

    @Test
    public void testAggregateTargetMetrics() {

        final TargetMetrics targetMetrics = ConnectivityCounterRegistry.aggregateTargetMetrics(CONNECTION_ID);

        final Measurement[] expected = {
                getMeasurement(MAPPED, true),
                getMeasurement(MAPPED, false),
                getMeasurement(CONSUMED, true),
                getMeasurement(CONSUMED, false),
                getMeasurement(FILTERED, true),
                getMeasurement(FILTERED, false),
                getMeasurement(PUBLISHED, true),
                getMeasurement(PUBLISHED, false)
        };

        assertThat(targetMetrics.getAddressMetrics().get(TARGET).getMeasurements()).containsExactlyInAnyOrder(expected);

    }

    private ImmutableMeasurement getMeasurement(final ConnectivityCounterRegistry.Metric metric, final boolean b) {
        return new ImmutableMeasurement(metric.getLabel(), b, getCounters(metric.ordinal() + 1), FIXED_INSTANT);
    }

}