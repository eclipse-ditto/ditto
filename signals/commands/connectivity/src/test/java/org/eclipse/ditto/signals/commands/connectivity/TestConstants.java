/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.connectivity;

import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.connectivity.AddressMetric;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionMetrics;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.model.connectivity.HeaderMapping;
import org.eclipse.ditto.model.connectivity.ImmutableLogEntry;
import org.eclipse.ditto.model.connectivity.LogCategory;
import org.eclipse.ditto.model.connectivity.LogEntry;
import org.eclipse.ditto.model.connectivity.LogLevel;
import org.eclipse.ditto.model.connectivity.LogType;
import org.eclipse.ditto.model.connectivity.MappingContext;
import org.eclipse.ditto.model.connectivity.Measurement;
import org.eclipse.ditto.model.connectivity.MetricType;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.SourceMetrics;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.connectivity.TargetMetrics;
import org.eclipse.ditto.model.connectivity.Topic;

/**
 * Constants for testing.
 */
public final class TestConstants {

    public static String ID = "myConnectionId";

    public static String TIMESTAMP = "2019-05-21T11:06:54.210Z";

    public static ConnectionType TYPE = ConnectionType.AMQP_10;
    public static ConnectivityStatus STATUS = ConnectivityStatus.OPEN;

    private static final String URI = "amqps://username:password@my.endpoint:443";

    private static final AuthorizationContext AUTHORIZATION_CONTEXT = AuthorizationContext.newInstance(
            AuthorizationSubject.newInstance("mySolutionId:mySubject"));

    private static final List<Source> SOURCES = Arrays.asList(
            ConnectivityModelFactory.newSourceBuilder()
                    .authorizationContext(AUTHORIZATION_CONTEXT)
                    .address("amqp/source1")
                    .consumerCount(2)
                    .index(0)
                    .build(),
            ConnectivityModelFactory.newSourceBuilder()
                    .authorizationContext(AUTHORIZATION_CONTEXT)
                    .address("amqp/source2")
                    .consumerCount(2)
                    .index(1)
                    .build()
    );

    private static final HeaderMapping HEADER_MAPPING = null;
    private static final List<Target> TARGETS = Collections.singletonList(
                    ConnectivityModelFactory.newTarget("eventQueue", AUTHORIZATION_CONTEXT, HEADER_MAPPING, null, Topic.TWIN_EVENTS));

    private static final MappingContext MAPPING_CONTEXT = ConnectivityModelFactory.newMappingContext(
            "JavaScript",
            Collections.singletonMap("incomingScript",
                    "function mapToDittoProtocolMsg(\n" +
                            "    headers,\n" +
                            "    textPayload,\n" +
                            "    bytePayload,\n" +
                            "    contentType\n" +
                            ") {\n" +
                            "\n" +
                            "    // ###\n" +
                            "    // Insert your mapping logic here\n" +
                            "    let namespace = \"org.eclipse.ditto\";\n" +
                            "    let id = \"foo-bar\";\n" +
                            "    let group = \"things\";\n" +
                            "    let channel = \"twin\";\n" +
                            "    let criterion = \"commands\";\n" +
                            "    let action = \"modify\";\n" +
                            "    let path = \"/attributes/foo\";\n" +
                            "    let dittoHeaders = headers;\n" +
                            "    let value = textPayload;\n" +
                            "    // ###\n" +
                            "\n" +
                            "    return Ditto.buildDittoProtocolMsg(\n" +
                            "        namespace,\n" +
                            "        id,\n" +
                            "        group,\n" +
                            "        channel,\n" +
                            "        criterion,\n" +
                            "        action,\n" +
                            "        path,\n" +
                            "        dittoHeaders,\n" +
                            "        value\n" +
                            "    );\n" +
                            "}"));

    public static Connection CONNECTION =
            ConnectivityModelFactory.newConnectionBuilder(ID, TYPE, STATUS, URI)
                    .sources(SOURCES)
                    .targets(TARGETS)
                    .mappingContext(MAPPING_CONTEXT)
                    .build();

    public static class Metrics {

        private static Instant LAST_MESSAGE_AT = Instant.now();

        public static final Duration ONE_MINUTE = Duration.ofMinutes(1);
        public static final Duration ONE_HOUR = Duration.ofHours(1);
        public static final Duration ONE_DAY = Duration.ofDays(1);
        public static final Duration[] DEFAULT_INTERVALS = {ONE_MINUTE, ONE_HOUR, ONE_DAY};

        public static final Map<Duration, Long> SOURCE_COUNTERS = asMap(
                        entry(ONE_MINUTE, ONE_MINUTE.getSeconds()),
                        entry(ONE_HOUR, ONE_HOUR.getSeconds()),
                        entry(ONE_DAY, ONE_DAY.getSeconds()));
        public static final Measurement INBOUND =
                ConnectivityModelFactory.newMeasurement(MetricType.CONSUMED, true, SOURCE_COUNTERS, LAST_MESSAGE_AT);
        public static final Map<Duration, Long> TARGET_COUNTERS = asMap(
                        entry(ONE_MINUTE, ONE_MINUTE.toMillis()),
                        entry(ONE_HOUR, ONE_HOUR.toMillis()),
                        entry(ONE_DAY, ONE_DAY.toMillis()));
        public static final Measurement OUTBOUND =
                ConnectivityModelFactory.newMeasurement(MetricType.PUBLISHED, true, TARGET_COUNTERS, LAST_MESSAGE_AT);
        public static final Map<Duration, Long> MAPPING_COUNTERS = asMap(
                        entry(ONE_MINUTE, ONE_MINUTE.toMinutes()),
                        entry(ONE_HOUR, ONE_HOUR.toMinutes()),
                        entry(ONE_DAY, ONE_DAY.toMinutes()));
        public static final Measurement MAPPING =
                ConnectivityModelFactory.newMeasurement(MetricType.MAPPED, true, MAPPING_COUNTERS, LAST_MESSAGE_AT);

        public static final AddressMetric INBOUND_METRIC = ConnectivityModelFactory.newAddressMetric(asSet(INBOUND, MAPPING));
        public static final AddressMetric OUTBOUND_METRIC = ConnectivityModelFactory.newAddressMetric(asSet(MAPPING, OUTBOUND));

        public static final SourceMetrics SOURCE_METRICS1 = ConnectivityModelFactory.newSourceMetrics(
                asMap(entry("source1", INBOUND_METRIC), entry("source2", INBOUND_METRIC)));
        public static final TargetMetrics TARGET_METRICS1 = ConnectivityModelFactory.newTargetMetrics(
                asMap(entry("target1", OUTBOUND_METRIC), entry("target2", OUTBOUND_METRIC)));

        public static final Measurement INBOUND_OVERALL = mergeMeasurements(MetricType.CONSUMED, true, Metrics.INBOUND, 4);
        public static final Measurement OUTBOUND_OVERALL = mergeMeasurements(MetricType.PUBLISHED, true, Metrics.OUTBOUND, 4);
        public static final Measurement MAPPING_OVERALL = mergeMeasurements(MetricType.MAPPED, true, Metrics.MAPPING, 8);

        public static final Set<Measurement> inboundMeasurements = new HashSet<>(asSet(INBOUND_OVERALL, MAPPING_OVERALL));
        public static final Set<Measurement> outboundMeasurements = new HashSet<>(asSet(OUTBOUND_OVERALL, MAPPING_OVERALL));

        public static final ConnectionMetrics CONNECTION_METRICS = ConnectivityModelFactory.newConnectionMetrics(
                ConnectivityModelFactory.newAddressMetric(inboundMeasurements),
                ConnectivityModelFactory.newAddressMetric(outboundMeasurements)
        );

        public static class Json {
            public static final JsonObject CONNECTION_METRICS_JSON = JsonFactory
                    .newObjectBuilder()
                    .set(ConnectionMetrics.JsonFields.INBOUND_METRICS,
                            JsonFactory.newObjectBuilder()
                                    .setAll(INBOUND_OVERALL.toJson())
                                    .setAll(MAPPING_OVERALL.toJson())
                                    .build()
                    )
                    .set(ConnectionMetrics.JsonFields.OUTBOUND_METRICS,
                            JsonFactory.newObjectBuilder()
                                    .setAll(OUTBOUND_OVERALL.toJson())
                                    .setAll(MAPPING_OVERALL.toJson())
                                    .build()
                    ).build();
        }

        public static Measurement mergeMeasurements(final MetricType type, final boolean success,
                final Measurement measurements, int times) {
            final Map<Duration, Long> result = new HashMap<>();
            for (Duration interval : DEFAULT_INTERVALS) {
                result.put(interval,
                        Optional.of(measurements)
                                .filter(m -> Objects.equals(type, m.getMetricType()))
                                .filter(m -> Objects.equals(success, m.isSuccess()))
                                .map(Measurement::getCounts)
                                .map(m -> m.getOrDefault(interval, 0L))
                                .orElse(0L) * times
                );
            }
            return ConnectivityModelFactory.newMeasurement(type, success, result, Metrics.LAST_MESSAGE_AT);
        }
    }

    public static class Logs {

        public static String CORRELATION_ID = UUID.randomUUID().toString();
        public static Instant TIMESTAMP_1 = Instant.now().minusSeconds(1);
        public static Instant TIMESTAMP_2 = Instant.now();
        public static LogCategory CATEGORY = LogCategory.TARGET;
        public static LogType TYPE_1 = LogType.MAPPED;
        public static LogType TYPE_2 = LogType.PUBLISHED;
        public static LogLevel LEVEL = LogLevel.SUCCESS;
        public static String MESSAGE_1 = "Message was successfully mapped.";
        public static String MESSAGE_2 = "Message was successfully published.";
        public static String ADDRESS = "test-topic";
        public static String THING_ID = "org.eclipse.ditto.connection.logs:loggedThing";

        public static LogEntry ENTRY_1 = ImmutableLogEntry.getBuilder(CORRELATION_ID, TIMESTAMP_1, CATEGORY, TYPE_1, LEVEL, MESSAGE_1, ADDRESS, THING_ID)
                .build();
        public static LogEntry ENTRY_2 = ImmutableLogEntry.getBuilder(CORRELATION_ID, TIMESTAMP_2, CATEGORY, TYPE_2, LEVEL, MESSAGE_2, ADDRESS, THING_ID)
                .build();

        public static List<LogEntry> ENTRIES = Arrays.asList(ENTRY_1, ENTRY_2);

        public static class Json {

            public static final JsonObject ENTRY_1_JSON = JsonFactory.newObjectBuilder()
                    .set(LogEntry.JsonFields.CORRELATION_ID, CORRELATION_ID)
                    .set(LogEntry.JsonFields.TIMESTAMP, TIMESTAMP_1.toString())
                    .set(LogEntry.JsonFields.CATEGORY, CATEGORY.getName())
                    .set(LogEntry.JsonFields.TYPE, TYPE_1.getType())
                    .set(LogEntry.JsonFields.MESSAGE, MESSAGE_1)
                    .set(LogEntry.JsonFields.LEVEL, LEVEL.getLevel())
                    .set(LogEntry.JsonFields.ADDRESS, ADDRESS)
                    .set(LogEntry.JsonFields.THING_ID, THING_ID)
                    .build();
            public static final JsonObject ENTRY_2_JSON = JsonFactory.newObjectBuilder()
                    .set(LogEntry.JsonFields.CORRELATION_ID, CORRELATION_ID)
                    .set(LogEntry.JsonFields.TIMESTAMP, TIMESTAMP_2.toString())
                    .set(LogEntry.JsonFields.CATEGORY, CATEGORY.getName())
                    .set(LogEntry.JsonFields.TYPE, TYPE_2.getType())
                    .set(LogEntry.JsonFields.MESSAGE, MESSAGE_2)
                    .set(LogEntry.JsonFields.LEVEL, LEVEL.getLevel())
                    .set(LogEntry.JsonFields.ADDRESS, ADDRESS)
                    .set(LogEntry.JsonFields.THING_ID, THING_ID)
                    .build();

            public static final JsonArray ENTRIES_JSON = JsonFactory.newArrayBuilder()
                    .add(ENTRY_1_JSON, ENTRY_2_JSON)
                    .build();

        }

    }

    private static <K, V> Map.Entry<K, V> entry(K interval, V count) {
        return new AbstractMap.SimpleImmutableEntry<>(interval, count);
    }

    @SafeVarargs
    private static <K, V> Map<K, V> asMap(Map.Entry<K, V>... entries) {
        return Stream.of(entries).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @SafeVarargs
    private static <T> Set<T> asSet(T... elements) {
        return new HashSet<>(Arrays.asList(elements));
    }

}
