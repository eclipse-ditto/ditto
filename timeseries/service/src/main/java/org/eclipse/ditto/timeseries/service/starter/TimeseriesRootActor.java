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
package org.eclipse.ditto.timeseries.service.starter;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.apache.pekko.Done;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.CoordinatedShutdown;
import org.apache.pekko.actor.Props;
import org.apache.pekko.pattern.Patterns;
import org.eclipse.ditto.base.service.actors.DittoRootActor;
import org.eclipse.ditto.base.service.config.DittoServiceConfig;
import org.eclipse.ditto.internal.utils.cluster.ShardRegionCreator;
import org.eclipse.ditto.internal.utils.cluster.ShardRegionProxyActorFactory;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.health.DefaultHealthCheckingActorFactory;
import org.eclipse.ditto.internal.utils.health.HealthCheckingActorOptions;
import org.eclipse.ditto.internal.utils.health.config.DefaultHealthCheckConfig;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProviderExtension;
import org.eclipse.ditto.things.api.Permission;
import org.eclipse.ditto.things.api.ThingsMessagingConstants;
import org.eclipse.ditto.timeseries.api.HealthStatus;
import org.eclipse.ditto.timeseries.api.TimeseriesAdapter;
import org.eclipse.ditto.timeseries.api.TimeseriesMessagingConstants;
import org.eclipse.ditto.timeseries.mongodb.DefaultMongoDbTimeseriesAdapterConfig;
import org.eclipse.ditto.timeseries.mongodb.MongoDbTimeseriesAdapter;
import org.eclipse.ditto.timeseries.mongodb.MongoDbTimeseriesAdapterConfig;
import org.eclipse.ditto.timeseries.service.handlers.TimeseriesIngestActor;

import com.typesafe.config.Config;

/**
 * Root actor of the Timeseries service. Constructs the configured timeseries adapter, kicks off
 * its asynchronous initialization on startup, and starts the cluster-sharded
 * {@link TimeseriesIngestActor} region that handles both the ingest write path and the
 * {@code RetrieveTimeseries} read path for each Thing.
 */
public final class TimeseriesRootActor extends DittoRootActor {

    /**
     * The name of this actor in the actor system.
     */
    public static final String ACTOR_NAME = TimeseriesMessagingConstants.ROOT_ACTOR_NAME;

    /**
     * Config path used by the default MongoDB adapter to load its connection settings.
     */
    static final String MONGODB_CONFIG_PATH = "ditto.timeseries.adapter.mongodb";

    /**
     * Config path used to look up whether timeseries reads accept the standard {@code READ}
     * permission as a stand-in for {@code READ_TS}. Two-mode contract:
     * <ul>
     *   <li>{@code false} (default): the read-path enforcer requires an explicit
     *       {@link Permission#READ_TS READ_TS} grant on every requested path. This is the
     *       fine-grained model — historical data access can be granted separately from live
     *       access.</li>
     *   <li>{@code true}: the enforcer instead checks {@link Permission#READ}. Useful for
     *       deployments preferring "if you can read the current value, you can read its
     *       history" semantics, without rewriting existing policies to add READ_TS grants.</li>
     * </ul>
     * A boolean rather than a free-form permission-name string deliberately constrains the
     * config surface to the two modes the design contract permits — any other permission value
     * would have been a deployment footgun (typo'd or unrelated permissions would be silently
     * treated as "no permission" by the enforcer, locking every caller out — see auth-authz.md
     * "Mistyped names are silently ignored — the #1 footgun").
     */
    static final String SIMPLIFIED_READ_PERMISSION_CONFIG_PATH =
            "ditto.timeseries.simplified-read-permission";

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final TimeseriesAdapter adapter;

    @SuppressWarnings("unused")
    private TimeseriesRootActor(final DittoServiceConfig timeseriesConfig,
            final ActorRef pubSubMediator) {

        final Config rootConfig = getContext().system().settings().config();
        adapter = createAdapter(rootConfig);

        // Register the adapter shutdown so its MongoDB connection pool closes cleanly
        // at SIGTERM. Without this, the JVM exits and the MongoDB driver's background
        // threads are interrupted mid-flight; ops sees driver-side warnings on every
        // rolling restart. Phase 3 will pull the adapter onto Ditto's MongoClientExtension
        // and remove this dedicated task.
        final TimeseriesAdapter adapterForShutdown = adapter;
        CoordinatedShutdown.get(getContext().getSystem())
                .addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate(),
                        "close_timeseries_adapter",
                        () -> adapterForShutdown.shutdown()
                                .<Done>thenApply(ignored -> Done.done())
                                .exceptionally(throwable -> {
                                    log.error(throwable,
                                            "Error closing timeseries adapter; proceeding " +
                                                    "with shutdown regardless.");
                                    return Done.done();
                                }));

        // Proxy to the things shard region (lives on cluster nodes with role "things"); the
        // per-Thing TimeseriesIngestActor asks it for SudoRetrieveThing to resolve the thing's
        // policy id during read-path enforcement. Built via the lower-level
        // ShardRegionProxyActorFactory so the timeseries-service module does not have to depend
        // on edge/service (which would be a cross-service-tier dep).
        final ActorRef thingsShardRegion = ShardRegionProxyActorFactory
                .newInstance(getContext().system(), timeseriesConfig.getClusterConfig())
                .getShardRegionProxyActor(ThingsMessagingConstants.CLUSTER_ROLE,
                        ThingsMessagingConstants.SHARD_REGION);

        // Resolve the (cached) policy enforcer provider once at root-actor construction; it pulls
        // policies from the policies shard region under the hood.
        final PolicyEnforcerProvider policyEnforcerProvider =
                PolicyEnforcerProviderExtension.get(getContext().system()).getPolicyEnforcerProvider();

        // Resolve the read-permission mode (default: false → require READ_TS).
        final boolean simplifiedReadPermission = resolveSimplifiedReadPermission(rootConfig);

        // Start the timeseries shard region. Each entity is a per-Thing non-persistent actor
        // (TimeseriesIngestActor) that forwards each IngestDataPoints batch to the configured
        // adapter and serves RetrieveTimeseries reads for the same Thing. Co-locating reads on
        // the write entity lets the edge forwarder route via askWithRetryCommandForwarder
        // against this shard region instead of a separate pub/sub-registered handler. Cluster
        // sharding routes each command to the deterministic owner node and handles entity
        // migration on cluster topology changes; the publisher's retry covers in-flight batches
        // across migration (no journal needed — the durable truth lives in the MongoDB Time
        // Series collection itself).
        final int numberOfShards = timeseriesConfig.getClusterConfig().getNumberOfShards();
        ShardRegionCreator.start(getContext().getSystem(),
                TimeseriesMessagingConstants.SHARD_REGION,
                TimeseriesIngestActor.props(adapter, thingsShardRegion, policyEnforcerProvider,
                        simplifiedReadPermission),
                numberOfShards,
                TimeseriesMessagingConstants.CLUSTER_ROLE);

        // Wire the health-check actor + HTTP /status route so Kubernetes liveness/readiness probes
        // resolve. The persistence checker is null because timeseries-service has no shared MongoDB
        // persistence — the adapter manages its own connection (a future phase can surface
        // adapter.getHealth() here as an additional checker). The health-check section is read
        // under ditto.timeseries.health-check, falling back to library defaults if absent.
        final var healthCheckConfig = DefaultHealthCheckConfig.of(timeseriesConfig);
        final var healthCheckingActorOptions = HealthCheckingActorOptions
                .getBuilder(healthCheckConfig.isEnabled(), healthCheckConfig.getInterval())
                .build();
        final ActorRef healthCheckingActor = startChildActor(DefaultHealthCheckingActorFactory.ACTOR_NAME,
                DefaultHealthCheckingActorFactory.props(healthCheckingActorOptions, null));
        bindHttpStatusRoute(timeseriesConfig.getHttpConfig(), healthCheckingActor);

        // Initialise the adapter asynchronously and pipe the outcome back as an internal message
        // so that init failures are visible in the root actor's log without blocking the actor
        // thread (per actor-concurrency rules).
        final MongoDbTimeseriesAdapterConfig mongoConfig =
                DefaultMongoDbTimeseriesAdapterConfig.of(rootConfig.getConfig(MONGODB_CONFIG_PATH));
        final CompletionStage<Object> initStage = adapter.initialize(mongoConfig)
                .<Object>thenApply(ignored -> AdapterInitialised.INSTANCE)
                .exceptionally(AdapterInitFailed::new);
        Patterns.pipe(initStage, getContext().getDispatcher()).to(getSelf());
    }

    /**
     * Creates Pekko configuration for this actor.
     *
     * @param timeseriesConfig the resolved service configuration.
     * @param pubSubMediator the actor reference of the Pekko pub/sub mediator.
     * @return the Pekko configuration object.
     */
    public static Props props(final DittoServiceConfig timeseriesConfig, final ActorRef pubSubMediator) {
        return Props.create(TimeseriesRootActor.class, timeseriesConfig, pubSubMediator);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(AdapterInitialised.class, msg ->
                        log.info("Timeseries adapter initialised; health: {}", adapter.getHealth()))
                .match(AdapterInitFailed.class, msg ->
                        log.error(msg.getCause(),
                                "Timeseries adapter initialisation failed; health remains: {}",
                                adapter.getHealth()))
                .build()
                .orElse(super.createReceive());
    }

    /**
     * Reads {@value #SIMPLIFIED_READ_PERMISSION_CONFIG_PATH} from the root config, falling back
     * to {@code false} (= strict mode, require READ_TS) when the section is absent. The boolean
     * cast is HOCON-strict — a malformed value (e.g. {@code "READ"} left over from a pre-4.0
     * configuration) throws {@link com.typesafe.config.ConfigException} at start-up rather than
     * silently defaulting.
     */
    private static boolean resolveSimplifiedReadPermission(final Config rootConfig) {
        return rootConfig.hasPath(SIMPLIFIED_READ_PERMISSION_CONFIG_PATH)
                && rootConfig.getBoolean(SIMPLIFIED_READ_PERMISSION_CONFIG_PATH);
    }

    /**
     * Constructs the configured adapter. The default Ditto distribution ships only the MongoDB
     * Time Series adapter; custom adapters land in Phase 3 alongside the SPI-finalisation work.
     */
    private static TimeseriesAdapter createAdapter(final Config rootConfig) {
        final String type = rootConfig.hasPath("ditto.timeseries.adapter.type")
                ? rootConfig.getString("ditto.timeseries.adapter.type")
                : "mongodb";
        if (!"mongodb".equals(type)) {
            // DittoConfigError extends Error so the service crashes deterministically at start-up
            // instead of being absorbed by Pekko's actor restart loop. Bad startup config must
            // fail fast; warn-and-continue here would hide a misconfigured deployment.
            throw new DittoConfigError(
                    "Unsupported timeseries adapter type: <" + type + ">. Only \"mongodb\" is " +
                            "supported in Phase 1.");
        }
        return new MongoDbTimeseriesAdapter();
    }

    /**
     * Internal signal piped to {@link #getSelf()} when the adapter's initialise stage completes
     * successfully.
     */
    private static final class AdapterInitialised {

        static final AdapterInitialised INSTANCE = new AdapterInitialised();

        private AdapterInitialised() {
            // singleton
        }
    }

    /**
     * Internal signal piped to {@link #getSelf()} when the adapter's initialise stage completes
     * exceptionally. Carries the original cause for logging.
     */
    private static final class AdapterInitFailed {

        @Nullable private final Throwable cause;

        AdapterInitFailed(final Throwable cause) {
            this.cause = cause;
        }

        @Nullable
        Throwable getCause() {
            return cause;
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            return o instanceof AdapterInitFailed && Objects.equals(cause, ((AdapterInitFailed) o).cause);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(cause);
        }
    }

    /**
     * Visible for tests — allows asserting the adapter health from outside the actor.
     *
     * @return the current adapter health.
     */
    HealthStatus adapterHealth() {
        return adapter.getHealth();
    }
}
