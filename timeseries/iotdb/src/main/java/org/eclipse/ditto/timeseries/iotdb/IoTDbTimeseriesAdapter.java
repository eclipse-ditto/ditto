/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.timeseries.iotdb;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import org.apache.iotdb.isession.ITableSession;
import org.apache.iotdb.isession.SessionDataSet;
import org.apache.iotdb.isession.pool.ITableSessionPool;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.rpc.TSStatusCode;
import org.apache.iotdb.session.TableSessionBuilder;
import org.apache.iotdb.session.pool.TableSessionPoolBuilder;
import org.apache.pekko.actor.ActorSystem;
import org.apache.tsfile.enums.ColumnCategory;
import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.write.record.Tablet;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.timeseries.api.Capabilities;
import org.eclipse.ditto.timeseries.api.HealthStatus;
import org.eclipse.ditto.timeseries.api.TimeseriesAdapter;
import org.eclipse.ditto.timeseries.api.compute.TimeseriesComputeKernel;
import org.eclipse.ditto.timeseries.iotdb.IoTDbSql.TagType;
import org.eclipse.ditto.timeseries.iotdb.IoTDbValues.Typed;
import org.eclipse.ditto.timeseries.model.AggregatedTimeseriesResult;
import org.eclipse.ditto.timeseries.model.Aggregation;
import org.eclipse.ditto.timeseries.model.CrossThingTimeseriesQuery;
import org.eclipse.ditto.timeseries.model.GroupBy;
import org.eclipse.ditto.timeseries.model.SortOrder;
import org.eclipse.ditto.timeseries.model.TimeseriesCursor;
import org.eclipse.ditto.timeseries.model.TimeseriesDataPoint;
import org.eclipse.ditto.timeseries.model.TimeseriesDataValue;
import org.eclipse.ditto.timeseries.model.TimeseriesQuery;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryInvalidException;
import org.eclipse.ditto.timeseries.model.TimeseriesQueryResult;
import org.eclipse.ditto.timeseries.model.TimeseriesResultMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

/**
 * {@link TimeseriesAdapter} backed by the Apache IoTDB 2.x table model.
 * <p>
 * Each Thing namespace is one table, so retention is a per-table TTL. {@code thing_id} and {@code path} are TAG
 * columns (one IoTDB device per series); the value lives in a FIELD column per JSON type, next to
 * {@code revision} and {@code unit}. Ingest-time tags are typed FIELD columns added on first sight, see
 * {@link IoTDbSql#tagColumn}.
 * <p>
 * IoTDB keeps one row per device and timestamp, so a point whose timestamp is already taken by another revision of
 * the same series is written to a device of its own: the {@code rev_slot} TAG carries its revision and is null for
 * every other point. Raw reads order by {@code (timestamp, revision)} like the other adapters.
 * <p>
 * Raw reads, bucketed aggregations, derivative/rate and cross-Thing aggregations all run inside IoTDB.
 * Gap fill, timezone-aligned buckets, percentile and integral are rejected.
 * <p>
 * The IoTDB client blocks, so every statement runs on a dedicated executor sized to the session pool, and every
 * returned future times out after the configured query timeout.
 */
@ThreadSafe
public final class IoTDbTimeseriesAdapter implements TimeseriesAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(IoTDbTimeseriesAdapter.class);

    static final Set<Aggregation> PUSHABLE_AGGREGATIONS = Collections.unmodifiableSet(
            EnumSet.of(Aggregation.AVG, Aggregation.MIN, Aggregation.MAX, Aggregation.SUM, Aggregation.COUNT,
                    Aggregation.FIRST, Aggregation.LAST, Aggregation.STDDEV, Aggregation.DERIVATIVE,
                    Aggregation.RATE));

    static final Capabilities CAPABILITIES = Capabilities.builder()
            .supportsNativeQuery(true)
            .supportsNativeCrossThingQuery(true)
            .pushableAggregations(PUSHABLE_AGGREGATIONS)
            .build();

    private static final String SUPPORTED_DESCRIPTION = "The IoTDB timeseries backend supports raw reads and the " +
            "aggregations avg, min, max, sum, count, first, last, stddev, derivative and rate, without 'fill' " +
            "and 'timezone'.";

    private static final List<String> BASE_COLUMNS = List.of(IoTDbSql.COL_THING_ID, IoTDbSql.COL_PATH,
            IoTDbSql.COL_VALUE_DOUBLE, IoTDbSql.COL_VALUE_TEXT, IoTDbSql.COL_VALUE_BOOL, IoTDbSql.COL_REVISION,
            IoTDbSql.COL_UNIT, IoTDbSql.COL_REVISION_SLOT);

    private static final List<TSDataType> BASE_TYPES = List.of(TSDataType.STRING, TSDataType.STRING,
            TSDataType.DOUBLE, TSDataType.STRING, TSDataType.BOOLEAN, TSDataType.INT64, TSDataType.STRING,
            TSDataType.STRING);

    private static final List<ColumnCategory> BASE_CATEGORIES = List.of(ColumnCategory.TAG, ColumnCategory.TAG,
            ColumnCategory.FIELD, ColumnCategory.FIELD, ColumnCategory.FIELD, ColumnCategory.FIELD,
            ColumnCategory.FIELD, ColumnCategory.TAG);

    private static final int WRITE_LOCK_STRIPES = 64;

    /**
     * Config path of the adapter settings. Not the extension's {@code extension-config}: Ditto parses that as a map
     * whose keys are paths, which would split dotted namespaces in {@code retention-overrides}.
     */
    static final String CONFIG_PATH = "ditto.timeseries.adapter.iotdb";

    private final IoTDbTimeseriesAdapterConfig config;

    private final AtomicReference<State> state = new AtomicReference<>(State.notInitialized());

    /**
     * Serializes the lookup and the insert of one Thing's batches, which the ingest entity may write in parallel. A
     * write still in flight on another node while the Thing's shard moves is not covered.
     */
    private final Object[] writeLocks = newWriteLocks();

    /**
     * Per-table columns, populated once the table has been created (or found) by this JVM. Failed ensures are
     * evicted so the next write retries.
     */
    private final ConcurrentHashMap<String, CompletableFuture<Set<String>>> ensuredTables =
            new ConcurrentHashMap<>();

    /**
     * Creates the adapter as the {@code timeseries-adapter} extension, configured by {@value #CONFIG_PATH}.
     *
     * @param actorSystem the actor system.
     * @param extensionConfig the {@code extension-config} of the extension, unused.
     */
    public IoTDbTimeseriesAdapter(final ActorSystem actorSystem, final Config extensionConfig) {
        this(DefaultIoTDbTimeseriesAdapterConfig.of(actorSystem.settings().config().getConfig(CONFIG_PATH)));
    }

    /**
     * @param config the adapter configuration.
     */
    public IoTDbTimeseriesAdapter(final IoTDbTimeseriesAdapterConfig config) {
        this.config = checkNotNull(config, "config");
    }

    @Override
    public Capabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public CompletionStage<Void> initialize() {
        final IoTDbTimeseriesAdapterConfig iotDbConfig = config;
        final ExecutorService executor = newExecutor(iotDbConfig.getMaxSessions());
        final CompletableFuture<ITableSessionPool> connect;
        try {
            connect = CompletableFuture.supplyAsync(() -> connect(iotDbConfig), executor);
        } catch (final RejectedExecutionException e) {
            executor.shutdown();
            return CompletableFuture.failedFuture(e);
        }
        return connect.copy()
                .orTimeout(iotDbConfig.getQueryTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .handle((pool, error) -> {
                    if (error == null) {
                        ensuredTables.clear();
                        final State previous = state.getAndSet(State.initialized(iotDbConfig, pool, executor));
                        if (previous.executor != null) {
                            previous.executor.shutdown();
                        }
                        if (previous.pool != null) {
                            closeQuietly(previous.pool);
                        }
                        return null;
                    }
                    // Also covers a connect that finishes after the timeout fired.
                    connect.thenAccept(IoTDbTimeseriesAdapter::closeQuietly);
                    executor.shutdown();
                    throw new CompletionException(unwrap(error));
                });
    }

    private static ITableSessionPool connect(final IoTDbTimeseriesAdapterConfig config) {
        final List<String> nodeUrls = List.of(config.getHost() + ":" + config.getPort());
        final int timeoutMillis = (int) Math.min(Integer.MAX_VALUE, config.getQueryTimeout().toMillis());
        // The pool's sessions open the database on connect, so it has to exist first.
        try (ITableSession bootstrap = new TableSessionBuilder()
                .nodeUrls(nodeUrls)
                .username(config.getUsername())
                .password(config.getPassword())
                .connectionTimeoutInMs(timeoutMillis)
                .build()) {
            bootstrap.executeNonQueryStatement(
                    "CREATE DATABASE IF NOT EXISTS " + IoTDbSql.quote(config.getDatabase()));
        } catch (final IoTDBConnectionException | StatementExecutionException e) {
            throw new CompletionException(e);
        }
        final ITableSessionPool pool = new TableSessionPoolBuilder()
                .nodeUrls(nodeUrls)
                .user(config.getUsername())
                .password(config.getPassword())
                .database(config.getDatabase())
                .maxSize(config.getMaxSessions())
                .connectionTimeoutInMs(timeoutMillis)
                .waitToGetSessionTimeoutInMs(config.getQueryTimeout().toMillis())
                .queryTimeoutInMs(config.getQueryTimeout().toMillis())
                .build();
        try (ITableSession session = pool.getSession()) {
            ping(session);
        } catch (final IoTDBConnectionException | StatementExecutionException e) {
            closeQuietly(pool);
            throw new CompletionException(e);
        }
        return pool;
    }

    @Override
    public CompletionStage<Void> shutdown() {
        final State previous = state.getAndSet(State.shutDown());
        ensuredTables.clear();
        if (previous.executor != null) {
            previous.executor.shutdown();
        }
        if (previous.pool != null) {
            try {
                previous.pool.close();
            } catch (final RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public HealthStatus getHealth() {
        return state.get().health;
    }

    @Override
    public CompletionStage<HealthStatus> checkHealth() {
        final State current = state.get();
        if (current.health != HealthStatus.UP) {
            return CompletableFuture.completedFuture(current.health);
        }
        return run(current, session -> {
            ping(session);
            return HealthStatus.UP;
        }).handle((status, error) -> {
            if (error != null) {
                LOGGER.warn("IoTDB timeseries backend did not answer a ping: {}", unwrap(error).toString());
                return HealthStatus.DOWN;
            }
            return status;
        });
    }

    private static void ping(final ITableSession session)
            throws IoTDBConnectionException, StatementExecutionException {

        try (SessionDataSet dataSet = session.executeQueryStatement("SELECT 1")) {
            dataSet.hasNext();
        }
    }

    // --- Ingestion ---

    @Override
    public CompletionStage<Void> write(final TimeseriesDataPoint dataPoint) {
        checkNotNull(dataPoint, "dataPoint");
        return writeBatch(List.of(dataPoint));
    }

    @Override
    public CompletionStage<Void> writeBatch(final List<TimeseriesDataPoint> dataPoints) {
        checkNotNull(dataPoints, "dataPoints");
        if (dataPoints.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        final State current = state.get();
        if (current.health != HealthStatus.UP) {
            return notInitialized();
        }
        final Map<String, List<Row>> rowsByNamespace = new LinkedHashMap<>();
        for (final TimeseriesDataPoint dataPoint : dataPoints) {
            checkNotNull(dataPoint, "dataPoint in batch");
            final Row row;
            try {
                row = Row.of(dataPoint);
            } catch (final IllegalArgumentException e) {
                return CompletableFuture.failedFuture(e);
            }
            rowsByNamespace.computeIfAbsent(dataPoint.getThingId().getNamespace(), ns -> new ArrayList<>()).add(row);
        }
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (final Map.Entry<String, List<Row>> entry : rowsByNamespace.entrySet()) {
            chain = chain.thenCompose(ignored -> insert(current, entry.getKey(), entry.getValue()));
        }
        return chain;
    }

    private CompletionStage<Void> insert(final State current, final String namespace, final List<Row> rows) {
        final String table = tableName(current, namespace);
        return ensureTable(current, namespace).thenCompose(columns -> IoTDbTimeseriesAdapter.<Void>run(current, session -> {
            final Map<String, TagType> tagColumns = new TreeMap<>();
            rows.forEach(row -> row.tags().forEach((column, value) -> tagColumns.put(column, value.type())));
            for (final Map.Entry<String, TagType> tagColumn : tagColumns.entrySet()) {
                if (!columns.contains(tagColumn.getKey())) {
                    session.executeNonQueryStatement("ALTER TABLE " + IoTDbSql.quote(table) +
                            " ADD COLUMN IF NOT EXISTS " + IoTDbSql.quote(tagColumn.getKey()) + " " +
                            tagColumn.getValue().getSqlType() + " FIELD");
                    columns.add(tagColumn.getKey());
                }
            }
            final Map<String, List<Row>> rowsByThing = new LinkedHashMap<>();
            rows.forEach(row -> rowsByThing.computeIfAbsent(row.thingId(), thingId -> new ArrayList<>()).add(row));
            for (final Map.Entry<String, List<Row>> thingRows : rowsByThing.entrySet()) {
                final String thingKey = table + '\u0000' + thingRows.getKey();
                synchronized (writeLocks[Math.floorMod(thingKey.hashCode(), WRITE_LOCK_STRIPES)]) {
                    session.insert(toTablet(table, withRevisionSlots(session, table, thingRows.getValue()),
                            tagColumns));
                }
            }
            return null;
        })).whenComplete((ignored, error) -> {
            if (error != null && isMissingTable(error)) {
                ensuredTables.remove(table);
            }
        });
    }

    /**
     * Assigns each row the device it is written to: the series' own device, unless its timestamp is already taken
     * by another revision, which then goes to the device of its revision. A retried revision keeps its device.
     */
    private static List<Row> withRevisionSlots(final ITableSession session, final String table,
            final List<Row> rows) throws IoTDBConnectionException, StatementExecutionException {

        final Map<SeriesInstant, Map<Long, String>> slots = storedRevisionSlots(session, table, rows);
        final List<Row> slotted = new ArrayList<>(rows.size());
        for (final Row row : rows) {
            final Map<Long, String> taken =
                    slots.computeIfAbsent(new SeriesInstant(row.path(), row.time()), instant -> new HashMap<>());
            final String slot;
            if (taken.containsKey(row.revision())) {
                slot = taken.get(row.revision());
            } else {
                slot = taken.isEmpty() ? null : Long.toString(row.revision());
                taken.put(row.revision(), slot);
            }
            slotted.add(row.withRevisionSlot(slot));
        }
        return slotted;
    }

    private static Map<SeriesInstant, Map<Long, String>> storedRevisionSlots(final ITableSession session,
            final String table, final List<Row> rows) throws IoTDBConnectionException, StatementExecutionException {

        final Map<SeriesInstant, Map<Long, String>> slots = new HashMap<>();
        select(session, storedRevisionsStatement(table, rows), row -> {
            final SeriesInstant instant = new SeriesInstant(row.getString(IoTDbSql.COL_PATH),
                    row.getLong(IoTDbSql.COL_TIME));
            final long revision = row.isNull(IoTDbSql.COL_REVISION) ? 0L : row.getLong(IoTDbSql.COL_REVISION);
            slots.computeIfAbsent(instant, i -> new HashMap<>())
                    .put(revision, nullableString(row, IoTDbSql.COL_REVISION_SLOT));
            return instant;
        });
        return slots;
    }

    static String storedRevisionsStatement(final String table, final List<Row> rows) {
        final String times = rows.stream().map(Row::time).distinct().sorted().map(String::valueOf)
                .collect(Collectors.joining(", "));
        return "SELECT " + IoTDbSql.quote(IoTDbSql.COL_PATH) + ", " + IoTDbSql.COL_TIME + ", " +
                IoTDbSql.quote(IoTDbSql.COL_REVISION) + ", " + IoTDbSql.quote(IoTDbSql.COL_REVISION_SLOT) +
                " FROM " + IoTDbSql.quote(table) + " WHERE " + IoTDbSql.quote(IoTDbSql.COL_THING_ID) + " = " +
                IoTDbSql.literal(rows.get(0).thingId()) + " AND " + IoTDbSql.COL_TIME + " IN (" + times + ")";
    }

    static Tablet toTablet(final String table, final List<Row> rows, final Map<String, TagType> tagColumns) {
        final List<String> names = new ArrayList<>(BASE_COLUMNS);
        final List<TSDataType> types = new ArrayList<>(BASE_TYPES);
        final List<ColumnCategory> categories = new ArrayList<>(BASE_CATEGORIES);
        tagColumns.forEach((column, type) -> {
            names.add(column);
            types.add(dataTypeOf(type));
            categories.add(ColumnCategory.FIELD);
        });
        final Tablet tablet = new Tablet(table, names, types, categories, rows.size());
        for (int i = 0; i < rows.size(); i++) {
            final Row row = rows.get(i);
            tablet.addTimestamp(i, row.time());
            tablet.addValue(i, IoTDbSql.COL_THING_ID, row.thingId());
            tablet.addValue(i, IoTDbSql.COL_PATH, row.path());
            tablet.addValue(i, IoTDbSql.COL_REVISION, row.revision());
            if (row.unit() != null) {
                tablet.addValue(i, IoTDbSql.COL_UNIT, row.unit());
            }
            if (row.revisionSlot() != null) {
                tablet.addValue(i, IoTDbSql.COL_REVISION_SLOT, row.revisionSlot());
            }
            if (row.value() != null) {
                addTyped(tablet, i, valueColumn(row.value().type()), row.value());
            }
            final int rowIndex = i;
            row.tags().forEach((column, value) -> addTyped(tablet, rowIndex, column, value));
        }
        tablet.setRowSize(rows.size());
        return tablet;
    }

    private static void addTyped(final Tablet tablet, final int row, final String column, final Typed typed) {
        switch (typed.type()) {
            case NUMBER -> tablet.addValue(row, column, (double) (Double) typed.value());
            case BOOLEAN -> tablet.addValue(row, column, (boolean) (Boolean) typed.value());
            case STRING -> tablet.addValue(row, column, (String) typed.value());
        }
    }

    private static TSDataType dataTypeOf(final TagType type) {
        return switch (type) {
            case NUMBER -> TSDataType.DOUBLE;
            case BOOLEAN -> TSDataType.BOOLEAN;
            case STRING -> TSDataType.STRING;
        };
    }

    private static String valueColumn(final TagType type) {
        return switch (type) {
            case NUMBER -> IoTDbSql.COL_VALUE_DOUBLE;
            case BOOLEAN -> IoTDbSql.COL_VALUE_BOOL;
            case STRING -> IoTDbSql.COL_VALUE_TEXT;
        };
    }

    /**
     * Creates the namespace's table if absent and reconciles its TTL with the configured retention, once per
     * table per JVM. Completes with the table's (mutable) column set.
     */
    private CompletionStage<Set<String>> ensureTable(final State current, final String namespace) {
        final String table = tableName(current, namespace);
        return ensuredTables.computeIfAbsent(table, t -> {
            final CompletableFuture<Set<String>> ensure = run(current, session -> {
                final String ttl = IoTDbSql.ttl(current.config.getRetention(namespace).orElse(null));
                session.executeNonQueryStatement(createTableStatement(t, ttl));
                try {
                    session.executeNonQueryStatement(
                            "ALTER TABLE " + IoTDbSql.quote(t) + " SET PROPERTIES TTL=" + ttl);
                } catch (final StatementExecutionException e) {
                    LOGGER.warn("Failed to reconcile the TTL of timeseries table <{}> to <{}>; leaving it " +
                            "unchanged. Cause: {}", t, ttl, e.getMessage());
                }
                final Set<String> columns = ConcurrentHashMap.newKeySet();
                columns.addAll(describe(session, t).orElseThrow(() ->
                        new IllegalStateException("Timeseries table <" + t + "> vanished after creation.")));
                if (!columns.contains(IoTDbSql.COL_REVISION_SLOT)) {
                    session.executeNonQueryStatement("ALTER TABLE " + IoTDbSql.quote(t) + " ADD COLUMN IF NOT EXISTS " +
                            IoTDbSql.quote(IoTDbSql.COL_REVISION_SLOT) + " STRING TAG");
                    columns.add(IoTDbSql.COL_REVISION_SLOT);
                }
                return columns;
            });
            ensure.whenComplete((ignored, error) -> {
                if (error != null) {
                    ensuredTables.remove(t, ensure);
                }
            });
            return ensure;
        });
    }

    static String createTableStatement(final String table, final String ttl) {
        return "CREATE TABLE IF NOT EXISTS " + IoTDbSql.quote(table) + " (" +
                IoTDbSql.quote(IoTDbSql.COL_THING_ID) + " STRING TAG, " +
                IoTDbSql.quote(IoTDbSql.COL_PATH) + " STRING TAG, " +
                IoTDbSql.quote(IoTDbSql.COL_VALUE_DOUBLE) + " DOUBLE FIELD, " +
                IoTDbSql.quote(IoTDbSql.COL_VALUE_TEXT) + " STRING FIELD, " +
                IoTDbSql.quote(IoTDbSql.COL_VALUE_BOOL) + " BOOLEAN FIELD, " +
                IoTDbSql.quote(IoTDbSql.COL_REVISION) + " INT64 FIELD, " +
                IoTDbSql.quote(IoTDbSql.COL_UNIT) + " STRING FIELD, " +
                IoTDbSql.quote(IoTDbSql.COL_REVISION_SLOT) + " STRING TAG) WITH (TTL=" + ttl + ")";
    }

    /**
     * @return the table's columns, or empty if the table does not exist.
     */
    private static Optional<Set<String>> describe(final ITableSession session, final String table)
            throws IoTDBConnectionException, StatementExecutionException {

        final Optional<List<String>> columns = select(session, "DESC " + IoTDbSql.quote(table),
                row -> row.getString("ColumnName"));
        return columns.map(LinkedHashSet::new);
    }

    // --- Single-Thing query ---

    @Override
    public CompletionStage<List<TimeseriesQueryResult>> query(final TimeseriesQuery query) {
        checkNotNull(query, "query");
        final State current = state.get();
        if (current.health != HealthStatus.UP) {
            return notInitialized();
        }
        try {
            ensureSupported(query);
        } catch (final TimeseriesQueryInvalidException e) {
            return CompletableFuture.failedFuture(e);
        }
        final List<CompletableFuture<TimeseriesQueryResult>> perPath = new ArrayList<>(query.getPaths().size());
        for (final JsonPointer path : query.getPaths()) {
            perPath.add(queryOnePath(current, query, path).toCompletableFuture());
        }
        return CompletableFuture.allOf(perPath.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> perPath.stream().map(CompletableFuture::join).toList());
    }

    static void ensureSupported(final TimeseriesQuery query) {
        if (query.getFillStrategy().isPresent()) {
            throw unsupported("The 'fill' parameter is not supported by the IoTDB timeseries backend.");
        }
        if (query.getTimezone().isPresent()) {
            throw unsupported("The 'timezone' parameter is not supported by the IoTDB timeseries backend.");
        }
        query.getAggregation()
                .filter(aggregation -> !PUSHABLE_AGGREGATIONS.contains(aggregation))
                .ifPresent(aggregation -> {
                    throw unsupported("The '" + aggregation.getName() +
                            "' aggregation is not supported by the IoTDB timeseries backend.");
                });
    }

    private static TimeseriesQueryInvalidException unsupported(final String message) {
        return TimeseriesQueryInvalidException.newBuilder(message).description(SUPPORTED_DESCRIPTION).build();
    }

    private CompletionStage<TimeseriesQueryResult> queryOnePath(final State current, final TimeseriesQuery query,
            final JsonPointer path) {

        final long startNanos = System.nanoTime();
        final String table = tableName(current, query.getThingId().getNamespace());
        final int maxPoints = current.config.getMaxQueryResultSize();
        final CompletionStage<TimeseriesQueryResult> result = run(current, session -> {
            final String where;
            if (query.getTagFilters().isEmpty()) {
                where = seriesWhere(query.getThingId(), path, query.getFrom(), query.getTo());
            } else {
                final Optional<Set<String>> columns = describe(session, table);
                if (columns.isEmpty()) {
                    return emptyResult(query, path);
                }
                where = seriesWhere(query.getThingId(), path, query.getFrom(), query.getTo()) + " AND " +
                        IoTDbTagPredicates.forTagFilters(query.getTagFilters(), columns.get());
            }
            final Optional<Aggregation> aggregation = query.getAggregation();
            if (aggregation.isEmpty()) {
                return rawRead(session, table, where, query, path, maxPoints);
            }
            if (aggregation.get().requiresStep()) {
                return bucketAggregation(session, table, where, query, path, aggregation.get(), maxPoints);
            }
            return derivative(session, table, where, query, path, aggregation.get() == Aggregation.RATE,
                    maxPoints);
        });
        return result.whenComplete((r, error) -> LOGGER.debug("Timeseries query cost: thing=<{}> path=<{}> " +
                        "agg=<{}> step=<{}> resultPoints=<{}> elapsedMs=<{}> outcome=<{}>",
                query.getThingId(), path, query.getAggregation().map(Aggregation::getName).orElse("none"),
                query.getStep().map(Duration::toString).orElse("none"),
                r == null ? 0 : r.getMeta().getCount(), (System.nanoTime() - startNanos) / 1_000_000L,
                error == null ? "ok" : "error:" + unwrap(error).getClass().getSimpleName()));
    }

    static String seriesWhere(final ThingId thingId, final JsonPointer path, final Instant from, final Instant to) {
        return IoTDbSql.quote(IoTDbSql.COL_THING_ID) + " = " + IoTDbSql.literal(thingId.toString()) +
                " AND " + IoTDbSql.quote(IoTDbSql.COL_PATH) + " = " + IoTDbSql.literal(path.toString()) +
                " AND " + timeRange(from, to);
    }

    private static String timeRange(final Instant from, final Instant to) {
        return IoTDbSql.COL_TIME + " >= " + IoTDbSql.literal(from) + " AND " + IoTDbSql.COL_TIME + " < " +
                IoTDbSql.literal(to);
    }

    static String rawReadStatement(final String table, final String where, @Nullable final TimeseriesCursor cursor,
            final boolean descending) {

        final String direction = descending ? " DESC" : " ASC";
        final StringBuilder sql = new StringBuilder("SELECT * FROM ").append(IoTDbSql.quote(table))
                .append(" WHERE ").append(where);
        // Ordered by time only, which IoTDB streams; ties of a timestamp are ordered by revision while reading.
        // No LIMIT: with a tag predicate IoTDB plans a top-k sort whose memory it estimates from the LIMIT.
        if (cursor != null) {
            final String timestamp = IoTDbSql.literal(cursor.getTimestamp());
            final String comparison = descending ? " < " : " > ";
            // The bare time bound keeps the time filter pushable, so a later page does not scan from `from`.
            sql.append(" AND ").append(IoTDbSql.COL_TIME).append(descending ? " <= " : " >= ").append(timestamp)
                    .append(" AND (").append(IoTDbSql.COL_TIME).append(comparison).append(timestamp)
                    .append(" OR ").append(IoTDbSql.quote(IoTDbSql.COL_REVISION)).append(comparison)
                    .append(cursor.getRevision()).append(")");
        }
        return sql.append(" ORDER BY ").append(IoTDbSql.COL_TIME).append(direction).toString();
    }

    private static TimeseriesQueryResult rawRead(final ITableSession session, final String table,
            final String where, final TimeseriesQuery query, final JsonPointer path, final int maxPoints)
            throws IoTDBConnectionException, StatementExecutionException {

        final boolean descending = query.getOrder().orElse(SortOrder.ASC) == SortOrder.DESC;
        final TimeseriesCursor cursor = query.getCursor().map(TimeseriesCursor::decode).orElse(null);
        final int pageSize = query.getLimit().map(limit -> Math.min(limit, maxPoints)).orElse(maxPoints);
        final String sql = rawReadStatement(table, where, cursor, descending);
        final Optional<List<StoredPoint>> rows = selectInRevisionOrder(session, sql, pageSize + 1, descending);
        if (rows.isEmpty()) {
            return emptyResult(query, path);
        }
        final List<StoredPoint> points = rows.get();
        final boolean hasMore = points.size() > pageSize;
        final List<StoredPoint> page = hasMore ? points.subList(0, pageSize) : points;
        final List<TimeseriesDataValue> data = new ArrayList<>(page.size());
        String unit = null;
        String dataType = null;
        for (final StoredPoint point : page) {
            data.add(TimeseriesDataValue.of(point.time(), point.value()));
            if (unit == null) {
                unit = point.unit();
            }
            if (dataType == null && !point.value().isNull()) {
                dataType = IoTDbValues.dataTypeOf(point.value());
            }
        }
        final String nextCursor = hasMore
                ? TimeseriesCursor.of(page.get(page.size() - 1).time(), page.get(page.size() - 1).revision())
                .encode()
                : null;
        final Map<String, JsonValue> tags = page.isEmpty() ? Map.of() : page.get(0).tags();
        final TimeseriesResultMeta meta = TimeseriesResultMeta.of(page.size(), unit,
                dataType == null ? "null" : dataType, tags, hasMore, nextCursor);
        return TimeseriesQueryResult.of(query.getThingId(), path, query, meta, data);
    }

    static String aggregateExpression(final Aggregation aggregation) {
        final String value = IoTDbSql.quote(IoTDbSql.COL_VALUE_DOUBLE);
        return switch (aggregation) {
            case AVG -> "avg(" + value + ")";
            case MIN -> "min(" + value + ")";
            case MAX -> "max(" + value + ")";
            case SUM -> "sum(" + value + ")";
            case COUNT -> "count(*)";
            case FIRST -> "first(" + value + ", " + IoTDbSql.COL_TIME + ")";
            case LAST -> "last(" + value + ", " + IoTDbSql.COL_TIME + ")";
            case STDDEV -> "stddev_samp(" + value + ")";
            default -> throw new IllegalStateException("Not a bucket aggregation: " + aggregation);
        };
    }

    static String bucketAggregationStatement(final String table, final String where, final Duration step,
            final Aggregation aggregation) {

        return "SELECT " + IoTDbSql.dateBin(step) + " AS b, " + aggregateExpression(aggregation) + " AS v, " +
                "first(" + IoTDbSql.quote(IoTDbSql.COL_UNIT) + ", " + IoTDbSql.COL_TIME + ") AS u FROM " +
                IoTDbSql.quote(table) + " WHERE " + where + " GROUP BY 1 ORDER BY 1";
    }

    private static TimeseriesQueryResult bucketAggregation(final ITableSession session, final String table,
            final String where, final TimeseriesQuery query, final JsonPointer path, final Aggregation aggregation,
            final int maxPoints) throws IoTDBConnectionException, StatementExecutionException {

        final Duration step = query.getStep().orElseThrow();
        final String sql = bucketAggregationStatement(table, where, step, aggregation);
        final Optional<List<Bucket>> rows =
                select(session, sql, row -> Bucket.read(row, aggregation), maxPoints + 1);
        if (rows.isEmpty()) {
            return emptyResult(query, path);
        }
        if (rows.get().size() > maxPoints) {
            throw resultCeilingExceeded(aggregation.getName(), maxPoints, path);
        }
        final LinkedHashMap<Instant, JsonValue> byBucket = new LinkedHashMap<>();
        String unit = null;
        for (final Bucket bucket : rows.get()) {
            byBucket.put(bucket.start(), bucket.value());
            if (unit == null) {
                unit = bucket.unit();
            }
        }
        final List<TimeseriesDataValue> data = TimeseriesComputeKernel.fillBuckets(byBucket, step, null, null);
        final TimeseriesResultMeta meta = TimeseriesResultMeta.of(data.size(), unit, aggregatedDataType(data));
        return TimeseriesQueryResult.of(query.getThingId(), path, query, meta, data);
    }

    /**
     * Differences consecutive numeric points (or, with a step, the last value per bucket) with {@code LAG},
     * stamped at the later point and per second; non-increasing timestamps are skipped, as in the kernel.
     */
    static String derivativeStatement(final String table, final String where, @Nullable final Duration step,
            final boolean rate) {

        final String value = IoTDbSql.quote(IoTDbSql.COL_VALUE_DOUBLE);
        final String numericWhere = where + " AND " + value + " IS NOT NULL";
        final String source = step == null
                ? "SELECT " + IoTDbSql.COL_TIME + " AS t, " + value + " AS v FROM " + IoTDbSql.quote(table) +
                " WHERE " + numericWhere
                : "SELECT " + IoTDbSql.dateBin(step) + " AS t, last(" + value + ", " + IoTDbSql.COL_TIME +
                ") AS v FROM " + IoTDbSql.quote(table) + " WHERE " + numericWhere + " GROUP BY 1";
        final String lagged = "SELECT t, v, LAG(v) OVER (ORDER BY t) AS pv, " +
                "CAST(t AS INT64) - CAST(LAG(t) OVER (ORDER BY t) AS INT64) AS dt FROM (" + source + ")";
        final String delta = rate ? "(CASE WHEN v < pv THEN v ELSE v - pv END)" : "(v - pv)";
        return "SELECT t, " + delta + " * 1000.0 / dt AS d FROM (" + lagged + ") WHERE dt > 0 ORDER BY t";
    }

    private static TimeseriesQueryResult derivative(final ITableSession session, final String table,
            final String where, final TimeseriesQuery query, final JsonPointer path, final boolean rate,
            final int maxPoints) throws IoTDBConnectionException, StatementExecutionException {

        final String sql = derivativeStatement(table, where, query.getStep().orElse(null), rate);
        final Optional<List<TimeseriesDataValue>> rows = select(session, sql,
                row -> TimeseriesDataValue.of(Instant.ofEpochMilli(row.getLong("t")),
                        JsonValue.of(row.getDouble("d"))), maxPoints + 1);
        if (rows.isEmpty()) {
            return emptyResult(query, path);
        }
        if (rows.get().size() > maxPoints) {
            throw resultCeilingExceeded(rate ? "rate" : "derivative", maxPoints, path);
        }
        final TimeseriesResultMeta meta = TimeseriesResultMeta.of(rows.get().size(), null, "number");
        return TimeseriesQueryResult.of(query.getThingId(), path, query, meta, rows.get());
    }

    private static TimeseriesQueryInvalidException resultCeilingExceeded(final String aggregationLabel,
            final int maxPoints, final JsonPointer path) {

        return TimeseriesQueryInvalidException
                .newBuilder("The '" + aggregationLabel + "' aggregation for <" + path + "> exceeds the " +
                        "maximum of " + maxPoints + " data points.")
                .description("Narrow the time range (from/to), or increase the 'step'.")
                .build();
    }

    private static TimeseriesQueryResult emptyResult(final TimeseriesQuery query, final JsonPointer path) {
        final String dataType = query.getAggregation().isPresent() ? "number" : "null";
        return TimeseriesQueryResult.of(query.getThingId(), path, query,
                TimeseriesResultMeta.of(0, null, dataType), List.of());
    }

    private static String aggregatedDataType(final List<TimeseriesDataValue> data) {
        for (final TimeseriesDataValue value : data) {
            final Optional<JsonValue> jsonValue = value.getValue().filter(v -> !v.isNull());
            if (jsonValue.isPresent()) {
                return IoTDbValues.dataTypeOf(jsonValue.get());
            }
        }
        return "number";
    }

    // --- Scan ---

    @Override
    public CompletionStage<List<TimeseriesDataValue>> scan(final ThingId thingId, final JsonPointer path,
            final Instant from, final Instant to, final int limit) {

        checkNotNull(thingId, "thingId");
        checkNotNull(path, "path");
        checkNotNull(from, "from");
        checkNotNull(to, "to");
        final State current = state.get();
        if (current.health != HealthStatus.UP) {
            return notInitialized();
        }
        final int maxPoints = current.config.getMaxQueryResultSize();
        final int effectiveLimit = limit <= 0 ? maxPoints : Math.min(limit, maxPoints);
        final String sql = rawReadStatement(tableName(current, thingId.getNamespace()),
                seriesWhere(thingId, path, from, to), null, false);
        return run(current, session -> selectInRevisionOrder(session, sql, effectiveLimit, false)
                .map(points -> points.stream()
                        .limit(effectiveLimit)
                        .map(point -> TimeseriesDataValue.of(point.time(), point.value()))
                        .toList())
                .orElse(List.of()));
    }

    // --- Cross-Thing ---

    @Override
    public CompletionStage<List<AggregatedTimeseriesResult>> queryCrossThing(final CrossThingTimeseriesQuery query,
            final Map<JsonPointer, Collection<ThingId>> permittedThingsPerPath) {

        checkNotNull(query, "query");
        checkNotNull(permittedThingsPerPath, "permittedThingsPerPath");
        final State current = state.get();
        if (current.health != HealthStatus.UP) {
            return notInitialized();
        }
        if (query.getFillStrategy().isPresent() || query.getTimezone().isPresent()) {
            return CompletableFuture.failedFuture(unsupported(
                    "The 'fill' and 'timezone' parameters are not supported by the IoTDB timeseries backend."));
        }
        final Map<JsonPointer, Collection<ThingId>> readable = readablePaths(query, permittedThingsPerPath);
        if (readable.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        final String table = tableName(current, query.getNamespace());
        final int maxGroups = query.getMaxGroups().orElse(CrossThingTimeseriesQuery.DEFAULT_MAX_GROUPS);
        final long startNanos = System.nanoTime();
        return run(current, session -> {
            final Optional<Set<String>> columns = describe(session, table);
            if (columns.isEmpty()) {
                return List.<AggregatedTimeseriesResult>of();
            }
            final CrossThingStatement statement = crossThingStatement(table, query, readable, columns.get());
            final List<AggregatedTimeseriesResult> results = new ArrayList<>();
            try (SessionDataSet dataSet = session.executeQueryStatement(statement.sql(), timeoutMillis(current))) {
                final SessionDataSet.DataIterator row = dataSet.iterator();
                List<JsonValue> seriesKey = null;
                SeriesBuilder series = null;
                while (row.next()) {
                    final List<JsonValue> key = statement.readKey(row);
                    if (!key.equals(seriesKey)) {
                        if (series != null) {
                            results.add(series.build(query));
                        }
                        if (results.size() >= maxGroups) {
                            throw tooManyGroups(maxGroups);
                        }
                        seriesKey = key;
                        series = new SeriesBuilder(statement.groupOf(row));
                    }
                    series.add(row, query.getAggregation());
                }
                if (series != null) {
                    results.add(series.build(query));
                }
            }
            return results;
        }).whenComplete((results, error) -> LOGGER.debug("Cross-Thing query cost: namespace=<{}> paths={} " +
                        "agg=<{}> groupBy={} series=<{}> elapsedMs=<{}>", query.getNamespace(), query.getPaths(),
                query.getAggregation().getName(), query.getGroupBy(), results == null ? 0 : results.size(),
                (System.nanoTime() - startNanos) / 1_000_000L));
    }

    private static TimeseriesQueryInvalidException tooManyGroups(final int maxGroups) {
        return TimeseriesQueryInvalidException.newBuilder("The query matches more than " + maxGroups +
                        " distinct groups. Narrow it with 'filter', fewer 'groupBy' dimensions or a smaller " +
                        "namespace, or raise 'maxGroups' (ceiling " + CrossThingTimeseriesQuery.MAX_GROUPS_CEILING +
                        ").")
                .build();
    }

    /**
     * The requested paths with at least one permitted Thing, in request order. An absent or empty entry means
     * nothing is permitted for that path, never "unrestricted".
     */
    private static Map<JsonPointer, Collection<ThingId>> readablePaths(final CrossThingTimeseriesQuery query,
            final Map<JsonPointer, Collection<ThingId>> permittedThingsPerPath) {

        final Map<JsonPointer, Collection<ThingId>> readable = new LinkedHashMap<>();
        for (final JsonPointer path : query.getPaths()) {
            final Collection<ThingId> permitted = permittedThingsPerPath.get(path);
            if (permitted != null && !permitted.isEmpty()) {
                readable.put(path, permitted);
            }
        }
        return readable;
    }

    /**
     * Restricts the scan to the readable {@code (path, Thing)} combinations, one clause per path, because a
     * Thing withheld from one path may still contribute to another.
     */
    static String pathAccessPredicate(final Map<JsonPointer, Collection<ThingId>> readable) {
        final List<String> perPath = new ArrayList<>(readable.size());
        readable.forEach((path, things) -> perPath.add("(" + IoTDbSql.quote(IoTDbSql.COL_PATH) + " = " +
                IoTDbSql.literal(path.toString()) + " AND " + IoTDbSql.quote(IoTDbSql.COL_THING_ID) + " IN (" +
                things.stream().map(thingId -> IoTDbSql.literal(thingId.toString()))
                        .collect(Collectors.joining(", ")) + "))"));
        return IoTDbTagPredicates.or(perPath);
    }

    private static String crossThingWhere(final CrossThingTimeseriesQuery query, final String access,
            final Set<String> columns) {

        final String filter = query.getFilter()
                .map(rql -> IoTDbTagPredicates.forRql(rql, columns))
                .orElse(IoTDbTagPredicates.TRUE);
        return IoTDbTagPredicates.and(List.of(timeRange(query.getFrom(), query.getTo()),
                IoTDbSql.quote(IoTDbSql.COL_VALUE_DOUBLE) + " IS NOT NULL", access, filter));
    }

    static CrossThingStatement crossThingStatement(final String table, final CrossThingTimeseriesQuery query,
            final Map<JsonPointer, Collection<ThingId>> readable, final Set<String> columns) {

        final List<String> keyColumns = new ArrayList<>();
        final List<String> select = new ArrayList<>();
        select.add(IoTDbSql.dateBin(query.getStep()) + " AS b");
        select.add(IoTDbSql.quote(IoTDbSql.COL_PATH) + " AS p");
        keyColumns.add("p");
        final List<GroupDimension> dimensions = new ArrayList<>();
        for (final GroupBy groupBy : query.getGroupBy()) {
            switch (groupBy.getKind()) {
                case PATH -> dimensions.add(new GroupDimension(groupBy.getGroupKey(), "p", Map.of()));
                case THING_ID -> {
                    if (!keyColumns.contains("t")) {
                        select.add(IoTDbSql.quote(IoTDbSql.COL_THING_ID) + " AS t");
                        keyColumns.add("t");
                    }
                    dimensions.add(new GroupDimension(groupBy.getGroupKey(), "t", Map.of()));
                }
                case TAG -> {
                    final String key = IoTDbTagPredicates.normalizeTagKey(groupBy.getTagKey().orElseThrow());
                    final Map<TagType, String> aliases = new LinkedHashMap<>();
                    for (final TagType type : TagType.values()) {
                        final String column = IoTDbSql.tagColumn(key, type);
                        if (columns.contains(column)) {
                            final String alias = "g" + dimensions.size() + "_" + type.ordinal();
                            select.add(IoTDbSql.quote(column) + " AS " + alias);
                            keyColumns.add(alias);
                            aliases.put(type, alias);
                        }
                    }
                    dimensions.add(new GroupDimension(groupBy.getGroupKey(), null, aliases));
                }
            }
        }
        select.add(aggregateExpression(query.getAggregation()) + " AS v");
        select.add("first(" + IoTDbSql.quote(IoTDbSql.COL_UNIT) + ", " + IoTDbSql.COL_TIME + ") AS u");

        final List<String> groupOrdinals = new ArrayList<>();
        for (int i = 1; i <= keyColumns.size() + 1; i++) {
            groupOrdinals.add(Integer.toString(i));
        }
        final List<String> orderBy = new ArrayList<>(groupOrdinals.subList(1, groupOrdinals.size()));
        orderBy.add("1");
        final String sql = "SELECT " + String.join(", ", select) + " FROM " + IoTDbSql.quote(table) +
                " WHERE " + crossThingWhere(query, pathAccessPredicate(readable), columns) +
                " GROUP BY " + String.join(", ", groupOrdinals) +
                " ORDER BY " + String.join(", ", orderBy);
        return new CrossThingStatement(sql, keyColumns, dimensions);
    }

    @Override
    public CompletionStage<Map<JsonPointer, List<ThingId>>> discoverContributors(
            final CrossThingTimeseriesQuery query, final int limit) {

        checkNotNull(query, "query");
        final State current = state.get();
        if (current.health != HealthStatus.UP) {
            return notInitialized();
        }
        final String table = tableName(current, query.getNamespace());
        // Thing-major rows, so a row bound of (limit + 1) * paths still reaches limit + 1 distinct Things.
        final long rowBound = Math.min(Integer.MAX_VALUE, ((long) limit + 1) * Math.max(1, query.getPaths().size()));
        return run(current, session -> {
            final Map<JsonPointer, List<ThingId>> perPath = new LinkedHashMap<>();
            final Optional<Set<String>> columns = describe(session, table);
            if (columns.isEmpty()) {
                return perPath;
            }
            final String sql = discoveryStatement(table, query, columns.get(), rowBound);
            final Set<ThingId> distinct = new LinkedHashSet<>();
            final Optional<List<Map.Entry<String, String>>> rows =
                    select(session, sql, row -> Map.entry(row.getString("t"), row.getString("p")));
            for (final Map.Entry<String, String> row : rows.orElse(List.of())) {
                final ThingId thingId = ThingId.of(row.getKey());
                if (!distinct.contains(thingId) && distinct.size() > limit) {
                    continue;
                }
                distinct.add(thingId);
                perPath.computeIfAbsent(JsonPointer.of(row.getValue()), p -> new ArrayList<>()).add(thingId);
            }
            return perPath;
        });
    }

    /**
     * Same filter as the aggregation but without a Thing restriction: discovery runs before authorization and
     * finds out which Things a decision has to be taken about.
     */
    static String discoveryStatement(final String table, final CrossThingTimeseriesQuery query,
            final Set<String> columns, final long rowBound) {

        final String paths = query.getPaths().stream()
                .map(path -> IoTDbSql.literal(path.toString()))
                .collect(Collectors.joining(", "));
        final String access = IoTDbSql.quote(IoTDbSql.COL_PATH) + " IN (" + paths + ")";
        return "SELECT " + IoTDbSql.quote(IoTDbSql.COL_THING_ID) + " AS t, " + IoTDbSql.quote(IoTDbSql.COL_PATH) +
                " AS p FROM " + IoTDbSql.quote(table) + " WHERE " + crossThingWhere(query, access, columns) +
                " GROUP BY 1, 2 ORDER BY 1, 2 LIMIT " + rowBound;
    }

    // --- Plumbing ---

    private static String tableName(final State current, final String namespace) {
        return IoTDbSql.tableName(current.config.getTablePrefix(), namespace);
    }

    private static long timeoutMillis(final State current) {
        return current.config.getQueryTimeout().toMillis();
    }

    /**
     * Runs {@code call} with a pooled session on the adapter's executor, failing the returned future once the
     * query timeout has passed.
     */
    private static <T> CompletableFuture<T> run(final State current, final SessionCall<T> call) {
        final CompletableFuture<T> future;
        try {
            future = CompletableFuture.supplyAsync(() -> {
                try (ITableSession session = current.pool.getSession()) {
                    return call.apply(session);
                } catch (final IoTDBConnectionException | StatementExecutionException e) {
                    throw new CompletionException(e);
                }
            }, current.executor);
        } catch (final RejectedExecutionException e) {
            return CompletableFuture.failedFuture(new IllegalStateException("IoTDbTimeseriesAdapter is shut down.", e));
        }
        return future.orTimeout(timeoutMillis(current), TimeUnit.MILLISECONDS);
    }

    private static <T> Optional<List<T>> select(final ITableSession session, final String sql,
            final RowReader<T> reader) throws IoTDBConnectionException, StatementExecutionException {

        return select(session, sql, reader, Integer.MAX_VALUE);
    }

    /**
     * Executes a query and reads at most {@code maxRows} rows, or returns empty if the table does not exist.
     */
    private static <T> Optional<List<T>> select(final ITableSession session, final String sql,
            final RowReader<T> reader, final int maxRows) throws IoTDBConnectionException, StatementExecutionException {

        try (SessionDataSet dataSet = session.executeQueryStatement(sql)) {
            final SessionDataSet.DataIterator row = dataSet.iterator();
            final List<T> out = new ArrayList<>();
            while (out.size() < maxRows && row.next()) {
                out.add(reader.read(row));
            }
            return Optional.of(out);
        } catch (final StatementExecutionException e) {
            if (e.getStatusCode() == TSStatusCode.TABLE_NOT_EXISTS.getStatusCode()) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /**
     * Reads at least {@code minRows} points of a time-ordered statement, plus every further point sharing the last
     * point's timestamp, and orders them by {@code (timestamp, revision)}. Empty if the table does not exist.
     */
    private static Optional<List<StoredPoint>> selectInRevisionOrder(final ITableSession session, final String sql,
            final int minRows, final boolean descending) throws IoTDBConnectionException, StatementExecutionException {

        try (SessionDataSet dataSet = session.executeQueryStatement(sql)) {
            final SessionDataSet.DataIterator row = dataSet.iterator();
            final List<StoredPoint> points = new ArrayList<>();
            while (row.next()) {
                final StoredPoint point = StoredPoint.read(row);
                if (points.size() >= minRows && !point.time().equals(points.get(points.size() - 1).time())) {
                    break;
                }
                points.add(point);
            }
            final Comparator<StoredPoint> order =
                    Comparator.comparing(StoredPoint::time).thenComparingLong(StoredPoint::revision);
            points.sort(descending ? order.reversed() : order);
            return Optional.of(points);
        } catch (final StatementExecutionException e) {
            if (e.getStatusCode() == TSStatusCode.TABLE_NOT_EXISTS.getStatusCode()) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private static boolean isMissingTable(final Throwable error) {
        return unwrap(error) instanceof StatementExecutionException e &&
                e.getStatusCode() == TSStatusCode.TABLE_NOT_EXISTS.getStatusCode();
    }

    private static Throwable unwrap(final Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static ExecutorService newExecutor(final int threads) {
        final AtomicInteger counter = new AtomicInteger();
        return Executors.newFixedThreadPool(threads, runnable -> {
            final Thread thread = new Thread(runnable, "iotdb-timeseries-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    private static void closeQuietly(@Nullable final ITableSessionPool pool) {
        if (pool != null) {
            try {
                pool.close();
            } catch (final RuntimeException e) {
                LOGGER.debug("Closing the IoTDB session pool failed: {}", e.toString());
            }
        }
    }

    private static <T> CompletableFuture<T> notInitialized() {
        return CompletableFuture.failedFuture(new IllegalStateException("IoTDbTimeseriesAdapter is not initialised."));
    }

    /**
     * Visible for tests — an initialised adapter on the given pool, bypassing {@link #initialize}.
     */
    static IoTDbTimeseriesAdapter forTesting(final ITableSessionPool pool, final IoTDbTimeseriesAdapterConfig config) {
        final IoTDbTimeseriesAdapter adapter = new IoTDbTimeseriesAdapter(config);
        adapter.state.set(State.initialized(config, checkNotNull(pool, "pool"),
                newExecutor(config.getMaxSessions())));
        return adapter;
    }

    @FunctionalInterface
    private interface SessionCall<T> {

        T apply(ITableSession session) throws IoTDBConnectionException, StatementExecutionException;
    }

    @FunctionalInterface
    private interface RowReader<T> {

        T read(SessionDataSet.DataIterator row) throws StatementExecutionException;
    }

    @Nullable
    private static Double nullableDouble(final SessionDataSet.DataIterator row, final String column)
            throws StatementExecutionException {

        return row.isNull(column) ? null : row.getDouble(column);
    }

    @Nullable
    private static Boolean nullableBoolean(final SessionDataSet.DataIterator row, final String column)
            throws StatementExecutionException {

        return row.isNull(column) ? null : row.getBoolean(column);
    }

    @Nullable
    private static String nullableString(final SessionDataSet.DataIterator row, final String column)
            throws StatementExecutionException {

        return row.isNull(column) ? null : row.getString(column);
    }

    /**
     * One data point as written: the series, the typed value and the typed tag columns.
     */
    record Row(long time, String thingId, String path, @Nullable Typed value, long revision, @Nullable String unit,
               Map<String, Typed> tags, @Nullable String revisionSlot) {

        static Row of(final TimeseriesDataPoint dataPoint) {
            final Map<String, Typed> tags = new TreeMap<>();
            dataPoint.getTags().forEach((key, tagValue) -> IoTDbValues.typed(tagValue)
                    .ifPresent(typed -> tags.put(IoTDbSql.tagColumn(key, typed.type()), typed)));
            return new Row(dataPoint.getTimestamp().toEpochMilli(), dataPoint.getThingId().toString(),
                    dataPoint.getPath().toString(), IoTDbValues.typed(dataPoint.getValue()).orElse(null),
                    dataPoint.getRevision(), dataPoint.getUnit().orElse(null), tags, null);
        }

        Row withRevisionSlot(@Nullable final String slot) {
            return new Row(time, thingId, path, value, revision, unit, tags, slot);
        }
    }

    private record SeriesInstant(String path, long time) {}

    private static Object[] newWriteLocks() {
        final Object[] locks = new Object[WRITE_LOCK_STRIPES];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
        return locks;
    }



    private record StoredPoint(Instant time, JsonValue value, long revision, @Nullable String unit,
                               Map<String, JsonValue> tags) {

        static StoredPoint read(final SessionDataSet.DataIterator row) throws StatementExecutionException {
            final JsonValue value = IoTDbValues.fromColumns(nullableDouble(row, IoTDbSql.COL_VALUE_DOUBLE),
                    nullableString(row, IoTDbSql.COL_VALUE_TEXT), nullableBoolean(row, IoTDbSql.COL_VALUE_BOOL));
            final Map<String, JsonValue> tags = new TreeMap<>();
            for (final String column : row.getColumnNameList()) {
                final Optional<String> tagKey = IoTDbSql.tagKeyOf(column);
                if (tagKey.isPresent() && !row.isNull(column)) {
                    final TagType type = IoTDbSql.tagTypeOf(column).orElseThrow();
                    tags.put(tagKey.get(), switch (type) {
                        case NUMBER -> IoTDbValues.number(row.getDouble(column));
                        case BOOLEAN -> JsonValue.of(row.getBoolean(column));
                        case STRING -> JsonValue.of(row.getString(column));
                    });
                }
            }
            final long revision = row.isNull(IoTDbSql.COL_REVISION) ? 0L : row.getLong(IoTDbSql.COL_REVISION);
            return new StoredPoint(Instant.ofEpochMilli(row.getLong(IoTDbSql.COL_TIME)), value, revision,
                    nullableString(row, IoTDbSql.COL_UNIT), tags);
        }
    }

    private record Bucket(Instant start, @Nullable JsonValue value, @Nullable String unit) {

        static Bucket read(final SessionDataSet.DataIterator row, final Aggregation aggregation)
                throws StatementExecutionException {

            final JsonValue value;
            if (row.isNull("v")) {
                value = null;
            } else if (aggregation == Aggregation.COUNT) {
                value = IoTDbValues.number(row.getLong("v"));
            } else {
                value = JsonValue.of(row.getDouble("v"));
            }
            return new Bucket(Instant.ofEpochMilli(row.getLong("b")), value, nullableString(row, "u"));
        }
    }

    /**
     * A cross-Thing group dimension: either a plain key column ({@code p}/{@code t}) or the typed columns of a
     * tag, which are folded back into one JSON value (JSON {@code null} for points without the tag).
     */
    private record GroupDimension(String groupKey, @Nullable String keyColumn, Map<TagType, String> tagAliases) {

        JsonValue read(final SessionDataSet.DataIterator row) throws StatementExecutionException {
            if (keyColumn != null) {
                return JsonValue.of(row.getString(keyColumn));
            }
            final String number = tagAliases.get(TagType.NUMBER);
            final String bool = tagAliases.get(TagType.BOOLEAN);
            final String string = tagAliases.get(TagType.STRING);
            return IoTDbValues.fromColumns(number == null ? null : nullableDouble(row, number),
                    string == null ? null : nullableString(row, string),
                    bool == null ? null : nullableBoolean(row, bool));
        }
    }

    record CrossThingStatement(String sql, List<String> keyColumns, List<GroupDimension> dimensions) {

        List<JsonValue> readKey(final SessionDataSet.DataIterator row) throws StatementExecutionException {
            final List<JsonValue> key = new ArrayList<>(keyColumns.size());
            for (final String column : keyColumns) {
                key.add(row.isNull(column) ? JsonValue.nullLiteral() : JsonValue.of(row.getObject(column).toString()));
            }
            return key;
        }

        GroupIdentity groupOf(final SessionDataSet.DataIterator row) throws StatementExecutionException {
            final Map<String, JsonValue> group = new LinkedHashMap<>();
            for (final GroupDimension dimension : dimensions) {
                group.put(dimension.groupKey(), dimension.read(row));
            }
            return new GroupIdentity(JsonPointer.of(row.getString("p")), group);
        }
    }

    private record GroupIdentity(JsonPointer path, Map<String, JsonValue> group) {}

    private static final class SeriesBuilder {

        private final GroupIdentity identity;
        private final LinkedHashMap<Instant, JsonValue> byBucket = new LinkedHashMap<>();
        @Nullable private String unit;

        private SeriesBuilder(final GroupIdentity identity) {
            this.identity = identity;
        }

        void add(final SessionDataSet.DataIterator row, final Aggregation aggregation)
                throws StatementExecutionException {

            final Bucket bucket = Bucket.read(row, aggregation);
            byBucket.put(bucket.start(), bucket.value());
            if (unit == null) {
                unit = bucket.unit();
            }
        }

        AggregatedTimeseriesResult build(final CrossThingTimeseriesQuery query) {
            final List<TimeseriesDataValue> data =
                    TimeseriesComputeKernel.fillBuckets(byBucket, query.getStep(), null, null);
            final TimeseriesResultMeta meta = TimeseriesResultMeta.of(data.size(), unit, aggregatedDataType(data));
            return AggregatedTimeseriesResult.of(identity.group(), identity.path(), meta, data);
        }
    }

    private static final class State {

        private final HealthStatus health;
        @Nullable private final IoTDbTimeseriesAdapterConfig config;
        @Nullable private final ITableSessionPool pool;
        @Nullable private final ExecutorService executor;

        private State(final HealthStatus health, @Nullable final IoTDbTimeseriesAdapterConfig config,
                @Nullable final ITableSessionPool pool, @Nullable final ExecutorService executor) {

            this.health = health;
            this.config = config;
            this.pool = pool;
            this.executor = executor;
        }

        static State notInitialized() {
            return new State(HealthStatus.DOWN, null, null, null);
        }

        static State initialized(final IoTDbTimeseriesAdapterConfig config, final ITableSessionPool pool,
                final ExecutorService executor) {

            return new State(HealthStatus.UP, config, pool, executor);
        }

        static State shutDown() {
            return new State(HealthStatus.DOWN, null, null, null);
        }
    }
}
