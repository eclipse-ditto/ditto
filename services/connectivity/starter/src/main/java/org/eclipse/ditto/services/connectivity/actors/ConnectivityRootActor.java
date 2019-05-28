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
package org.eclipse.ditto.services.connectivity.actors;

import static akka.http.javadsl.server.Directives.logRequest;
import static akka.http.javadsl.server.Directives.logResult;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ConnectException;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import javax.annotation.Nullable;
import javax.jms.JMSRuntimeException;
import javax.naming.NamingException;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.services.base.config.ServiceConfigReader;
import org.eclipse.ditto.services.connectivity.messaging.ClientActorPropsFactory;
import org.eclipse.ditto.services.connectivity.messaging.ConnectionSupervisorActor;
import org.eclipse.ditto.services.connectivity.messaging.DefaultClientActorPropsFactory;
import org.eclipse.ditto.services.connectivity.messaging.ReconnectActor;
import org.eclipse.ditto.services.connectivity.util.ConfigKeys;
import org.eclipse.ditto.services.models.concierge.actors.ConciergeEnforcerClusterRouterFactory;
import org.eclipse.ditto.services.models.concierge.actors.ConciergeForwarderActor;
import org.eclipse.ditto.services.models.connectivity.ConnectivityMessagingConstants;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cluster.ClusterStatusSupplier;
import org.eclipse.ditto.services.utils.cluster.ClusterUtil;
import org.eclipse.ditto.services.utils.cluster.ShardRegionExtractor;
import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.eclipse.ditto.services.utils.health.DefaultHealthCheckingActorFactory;
import org.eclipse.ditto.services.utils.health.HealthCheckingActorOptions;
import org.eclipse.ditto.services.utils.health.routes.StatusRoute;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoHealthChecker;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.connectivity.ConnectivityCommandInterceptor;

import com.typesafe.config.Config;

import akka.Done;
import akka.actor.AbstractActor;
import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.CoordinatedShutdown;
import akka.actor.InvalidActorNameException;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.SupervisorStrategy;
import akka.cluster.Cluster;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.contrib.persistence.mongodb.JavaDslMongoReadJournal;
import akka.event.DiagnosticLoggingAdapter;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Route;
import akka.japi.Creator;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.AskTimeoutException;
import akka.persistence.query.PersistenceQuery;
import akka.stream.ActorMaterializer;

/**
 * Parent Actor which takes care of supervision of all other Actors in our system.
 */
public final class ConnectivityRootActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "connectivityRoot";

    private static final String CLUSTER_ROLE = "connectivity";
    private static final String RECONNECT_READ_JOURNAL_PLUGIN_ID =
            "akka-contrib-mongodb-persistence-reconnect-readjournal";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final SupervisorStrategy strategy = new OneForOneStrategy(true, DeciderBuilder
            .match(NullPointerException.class, e -> {
                log.error(e, "NullPointer in child actor: {}", e.getMessage());
                return restartChild();
            }).match(IllegalArgumentException.class, e -> {
                log.warning("Illegal Argument in child actor: {}", e.getMessage());

                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);

                log.warning("Illegal Argument in child actor: {}", sw.toString());
                return SupervisorStrategy.resume();
            }).match(IllegalStateException.class, e -> {
                log.warning("Illegal State in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(IndexOutOfBoundsException.class, e -> {
                log.warning("IndexOutOfBounds in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(NoSuchElementException.class, e -> {
                log.warning("NoSuchElement in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(AskTimeoutException.class, e -> {
                log.warning("AskTimeoutException in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(ConnectException.class, e -> {
                log.warning("ConnectException in child actor: {}", e.getMessage());
                return restartChild();
            }).match(InvalidActorNameException.class, e -> {
                log.warning("InvalidActorNameException in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(ActorKilledException.class, e -> {
                log.error(e, "ActorKilledException in child actor: {}", e.message());
                return restartChild();
            }).match(DittoRuntimeException.class, e -> {
                log.error(e,
                        "DittoRuntimeException '{}' should not be escalated to ConnectivityRootActor. Simply resuming Actor.",
                        e.getErrorCode());
                return SupervisorStrategy.resume();
            }).match(JMSRuntimeException.class, e -> {
                log.warning("JMSRuntimeException '{}' occurred.", e.getMessage());
                return restartChild();
            }).match(NamingException.class, e -> {
                log.warning("NamingException '{}' occurred.", e.getMessage());
                return restartChild();
            }).match(Throwable.class, e -> {
                log.error(e, "Escalating above root actor!");
                return SupervisorStrategy.escalate();
            }).matchAny(e -> {
                log.error("Unknown message:'{}'! Escalating above root actor!", e);
                return SupervisorStrategy.escalate();
            }).build());

    private ConnectivityRootActor(final ServiceConfigReader configReader, final ActorRef pubSubMediator,
            final ActorMaterializer materializer,
            final Function<Signal<?>, Signal<?>> conciergeForwarderSignalTransformer,
            @Nullable final ConnectivityCommandInterceptor commandValidator) {

        final Config config = configReader.getRawConfig();
        final boolean healthCheckEnabled = config.getBoolean(ConfigKeys.HealthCheck.ENABLED);
        final Duration healthCheckInterval = config.getDuration(ConfigKeys.HealthCheck.INTERVAL);

        final HealthCheckingActorOptions.Builder hcBuilder =
                HealthCheckingActorOptions.getBuilder(healthCheckEnabled, healthCheckInterval);
        if (config.getBoolean(ConfigKeys.HealthCheck.PERSISTENCE_ENABLED)) {
            hcBuilder.enablePersistenceCheck();
        }

        final HealthCheckingActorOptions healthCheckingActorOptions = hcBuilder.build();
        final ActorRef healthCheckingActor = startChildActor(DefaultHealthCheckingActorFactory.ACTOR_NAME,
                DefaultHealthCheckingActorFactory.props(healthCheckingActorOptions, MongoHealthChecker.props()));

        final Duration minBackoff = config.getDuration(ConfigKeys.Connection.SUPERVISOR_EXPONENTIAL_BACKOFF_MIN);
        final Duration maxBackoff = config.getDuration(ConfigKeys.Connection.SUPERVISOR_EXPONENTIAL_BACKOFF_MAX);
        final double randomFactor =
                config.getDouble(ConfigKeys.Connection.SUPERVISOR_EXPONENTIAL_BACKOFF_RANDOM_FACTOR);
        final int numberOfShards = config.getInt(ConfigKeys.Cluster.NUMBER_OF_SHARDS);

        final ActorSystem actorSystem = getContext().system();

        final ActorRef conciergeEnforcerRouter =
                ConciergeEnforcerClusterRouterFactory.createConciergeEnforcerClusterRouter(getContext(),
                        configReader.cluster().numberOfShards());

        final ActorRef conciergeForwarder = startChildActor(ConciergeForwarderActor.ACTOR_NAME,
                ConciergeForwarderActor.props(pubSubMediator, conciergeEnforcerRouter,
                        conciergeForwarderSignalTransformer));

        final ClientActorPropsFactory propsFactory = DefaultClientActorPropsFactory.getInstance();
        final Props connectionSupervisorProps =
                ConnectionSupervisorActor.props(minBackoff, maxBackoff, randomFactor, pubSubMediator,
                        conciergeForwarder, propsFactory, commandValidator);

        final ClusterShardingSettings shardingSettings =
                ClusterShardingSettings.create(actorSystem)
                        .withRole(ConnectivityMessagingConstants.CLUSTER_ROLE);

        final ActorRef connectionShardRegion = ClusterSharding.get(this.getContext().system())
                .start(ConnectivityMessagingConstants.SHARD_REGION,
                        connectionSupervisorProps,
                        shardingSettings,
                        ShardRegionExtractor.of(numberOfShards, getContext().getSystem()));

        final JavaDslMongoReadJournal mongoReadJournal = PersistenceQuery
                .get(getContext().getSystem())
                .getReadJournalFor(JavaDslMongoReadJournal.class, RECONNECT_READ_JOURNAL_PLUGIN_ID);

        startClusterSingletonActor(ReconnectActor.ACTOR_NAME, ReconnectActor.props(connectionShardRegion,
                mongoReadJournal::currentPersistenceIds));

        String hostname = config.getString(ConfigKeys.Http.HOSTNAME);
        if (hostname.isEmpty()) {
            hostname = ConfigUtil.getLocalHostAddress();
            log.info("No explicit hostname configured, using HTTP hostname: {}", hostname);
        }

        final CompletionStage<ServerBinding> binding = Http.get(getContext().system()).bindAndHandle( //
                createRoute(getContext().system(), healthCheckingActor).flow(getContext().system(), materializer),
                ConnectHttp.toHost(hostname, config.getInt(ConfigKeys.Http.PORT)),
                materializer);

        binding.thenAccept(theBinding -> CoordinatedShutdown.get(getContext().getSystem()).addTask(
                CoordinatedShutdown.PhaseServiceUnbind(), "shutdown_health_http_endpoint", () -> {
                    log.info("Gracefully shutting down status/health HTTP endpoint..");
                    return theBinding.terminate(Duration.ofSeconds(1))
                            .handle((httpTerminated, e) -> Done.getInstance());
                })
        ).exceptionally(failure -> {
            log.error("Something very bad happened! " + failure.getMessage(), failure);
            getContext().system().terminate();
            return null;
        });
    }

    /**
     * Creates Akka configuration object Props for this ConnectivityRootActor.
     *
     * @param configReader the configuration reader of this service.
     * @param pubSubMediator the PubSub mediator Actor.
     * @param materializer the materializer for the akka actor system.
     * @param conciergeForwarderSignalTransformer a function which transforms signals before forwarding them to the
     * concierge service
     * @param commandValidator custom command validator for connectivity commands
     * @return the Akka configuration Props object.
     */
    public static Props props(final ServiceConfigReader configReader, final ActorRef pubSubMediator,
            final ActorMaterializer materializer,
            final Function<Signal<?>, Signal<?>> conciergeForwarderSignalTransformer,
            final ConnectivityCommandInterceptor commandValidator) {
        return Props.create(ConnectivityRootActor.class, new Creator<ConnectivityRootActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ConnectivityRootActor create() {
                return new ConnectivityRootActor(configReader, pubSubMediator, materializer,
                        conciergeForwarderSignalTransformer, commandValidator);
            }
        });
    }

    /**
     * Creates Akka configuration object Props for this ConnectivityRootActor.
     *
     * @param configReader the configuration reader of this service.
     * @param pubSubMediator the PubSub mediator Actor.
     * @param materializer the materializer for the akka actor system.
     * @param conciergeForwarderSignalTransformer a function which transforms signals before forwarding them to the
     * concierge service
     * @return the Akka configuration Props object.
     */
    public static Props props(final ServiceConfigReader configReader, final ActorRef pubSubMediator,
            final ActorMaterializer materializer,
            final Function<Signal<?>, Signal<?>> conciergeForwarderSignalTransformer) {
        return Props.create(ConnectivityRootActor.class, new Creator<ConnectivityRootActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ConnectivityRootActor create() {
                return new ConnectivityRootActor(configReader, pubSubMediator, materializer,
                        conciergeForwarderSignalTransformer, null);
            }
        });
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return strategy;
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Status.Failure.class, f -> log.error(f.cause(), "Got failure: {}", f))
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private SupervisorStrategy.Directive restartChild() {
        log.info("Restarting child ...");
        return SupervisorStrategy.restart();
    }

    private ActorRef startChildActor(final String actorName, final Props props) {
        log.info("Starting child actor '{}'", actorName);
        return getContext().actorOf(props, actorName);
    }

    private void startClusterSingletonActor(final String actorName, final Props props) {
        ClusterUtil.startSingleton(getContext(), CLUSTER_ROLE, actorName, props);
    }

    private static Route createRoute(final ActorSystem actorSystem, final ActorRef healthCheckingActor) {
        final StatusRoute statusRoute = new StatusRoute(new ClusterStatusSupplier(Cluster.get(actorSystem)),
                healthCheckingActor, actorSystem);

        return logRequest("http-request", () -> logResult("http-response", statusRoute::buildStatusRoute));
    }

}
