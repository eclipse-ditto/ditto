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
package org.eclipse.ditto.services.concierge.starter.actors;

import static akka.http.javadsl.server.Directives.logRequest;
import static akka.http.javadsl.server.Directives.logResult;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.net.ConnectException;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.services.base.config.http.HttpConfig;
import org.eclipse.ditto.services.concierge.batch.actors.BatchSupervisorActor;
import org.eclipse.ditto.services.concierge.common.ConciergeConfig;
import org.eclipse.ditto.services.concierge.starter.proxy.AbstractEnforcerActorFactory;
import org.eclipse.ditto.services.models.concierge.ConciergeMessagingConstants;
import org.eclipse.ditto.services.models.concierge.actors.ConciergeForwarderActor;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cluster.ClusterStatusSupplier;
import org.eclipse.ditto.services.utils.cluster.ClusterUtil;
import org.eclipse.ditto.services.utils.config.LocalHostAddressSupplier;
import org.eclipse.ditto.services.utils.health.DefaultHealthCheckingActorFactory;
import org.eclipse.ditto.services.utils.health.HealthCheckingActorOptions;
import org.eclipse.ditto.services.utils.health.config.HealthCheckConfig;
import org.eclipse.ditto.services.utils.health.config.PersistenceConfig;
import org.eclipse.ditto.services.utils.health.routes.StatusRoute;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoHealthChecker;

import akka.Done;
import akka.actor.AbstractActor;
import akka.actor.ActorInitializationException;
import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import akka.actor.CoordinatedShutdown;
import akka.actor.InvalidActorNameException;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.SupervisorStrategy;
import akka.cluster.Cluster;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Route;
import akka.japi.pf.DeciderBuilder;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.AskTimeoutException;
import akka.stream.ActorMaterializer;

/**
 * The root actor of the concierge service.
 */
public final class ConciergeRootActor extends AbstractActor {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "conciergeRoot";

    private static final String RESTARTING_CHILD_MSG = "Restarting child...";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);
    private final SupervisorStrategy supervisorStrategy = new OneForOneStrategy(true, DeciderBuilder
            .match(NullPointerException.class, e -> {
                log.error(e, "NullPointer in child actor: {}", e.getMessage());
                log.info(RESTARTING_CHILD_MSG);
                return SupervisorStrategy.restart();
            }).match(IllegalArgumentException.class, e -> {
                log.warning("Illegal Argument in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(IndexOutOfBoundsException.class, e -> {
                log.warning("IndexOutOfBounds in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(IllegalStateException.class, e -> {
                log.warning("Illegal State in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(NoSuchElementException.class, e -> {
                log.warning("NoSuchElement in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(AskTimeoutException.class, e -> {
                log.warning("AskTimeoutException in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(ConnectException.class, e -> {
                log.warning("ConnectException in child actor: {}", e.getMessage());
                log.info(RESTARTING_CHILD_MSG);
                return SupervisorStrategy.restart();
            }).match(InvalidActorNameException.class, e -> {
                log.warning("InvalidActorNameException in child actor: {}", e.getMessage());
                return SupervisorStrategy.resume();
            }).match(ActorInitializationException.class, e -> {
                log.error(e, "ActorInitializationException in child actor: {}", e.getMessage());
                return SupervisorStrategy.stop();
            }).match(ActorKilledException.class, e -> {
                log.error(e, "ActorKilledException in child actor: {}", e.message());
                log.info(RESTARTING_CHILD_MSG);
                return SupervisorStrategy.restart();
            }).match(DittoRuntimeException.class, e -> {
                log.error(e,
                        "DittoRuntimeException '{}' should not be escalated to RootActor. Simply resuming Actor.",
                        e.getErrorCode());
                return SupervisorStrategy.resume();
            }).match(Throwable.class, e -> {
                log.error(e, "Escalating above root actor!");
                return SupervisorStrategy.escalate();
            }).matchAny(e -> {
                log.error("Unknown message:'{}'! Escalating above root actor!", e);
                return SupervisorStrategy.escalate();
            }).build());

    @SuppressWarnings("unused")
    private <C extends ConciergeConfig> ConciergeRootActor(final C conciergeConfig,
            final ActorRef pubSubMediator,
            final AbstractEnforcerActorFactory<C> enforcerActorFactory,
            final ActorMaterializer materializer) {

        pubSubMediator.tell(new DistributedPubSubMediator.Put(getSelf()), getSelf());

        final ActorContext context = getContext();

        enforcerActorFactory.startEnforcerActor(context, conciergeConfig, pubSubMediator);

        final ActorRef conciergeForwarder = context.findChild(ConciergeForwarderActor.ACTOR_NAME).orElseThrow(() ->
                new IllegalStateException("ConciergeForwarder could not be found"));

        startClusterSingletonActor(context, BatchSupervisorActor.props(pubSubMediator, conciergeForwarder));

        final ActorRef healthCheckingActor = startHealthCheckingActor(context, conciergeConfig);

        bindHttpStatusRoute(healthCheckingActor, conciergeConfig.getHttpConfig(), materializer);
    }

    /**
     * Creates Akka configuration object Props for this actor.
     *
     * @param conciergeConfig the config of Concierge.
     * @param pubSubMediator the PubSub mediator Actor.
     * @param enforcerActorFactory factory for creating sharded enforcer actors.
     * @param materializer the materializer for the Akka actor system.
     * @return the Akka configuration Props object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static <C extends ConciergeConfig> Props props(final C conciergeConfig,
            final ActorRef pubSubMediator,
            final AbstractEnforcerActorFactory<C> enforcerActorFactory,
            final ActorMaterializer materializer) {

        checkNotNull(conciergeConfig, "config of Concierge");
        checkNotNull(pubSubMediator, "pub-sub mediator");
        checkNotNull(enforcerActorFactory, "EnforcerActor factory");
        checkNotNull(materializer, "ActorMaterializer");

        return Props.create(ConciergeRootActor.class, conciergeConfig, pubSubMediator, enforcerActorFactory,
                materializer);
    }


    private static void startClusterSingletonActor(final akka.actor.ActorContext context, final Props props) {
        ClusterUtil.startSingleton(context, ConciergeMessagingConstants.CLUSTER_ROLE, BatchSupervisorActor.ACTOR_NAME,
                props);
    }

    private static ActorRef startHealthCheckingActor(final ActorRefFactory context,
            final ConciergeConfig conciergeConfig) {

        final HealthCheckConfig healthCheckConfig = conciergeConfig.getHealthCheckConfig();

        final HealthCheckingActorOptions.Builder hcBuilder =
                HealthCheckingActorOptions.getBuilder(healthCheckConfig.isEnabled(), healthCheckConfig.getInterval());

        final PersistenceConfig persistenceConfig = healthCheckConfig.getPersistenceConfig();
        if (persistenceConfig.isEnabled()) {
            hcBuilder.enablePersistenceCheck();
        }
        final HealthCheckingActorOptions healthCheckingActorOptions = hcBuilder.build();

        return startChildActor(context, DefaultHealthCheckingActorFactory.ACTOR_NAME,
                DefaultHealthCheckingActorFactory.props(healthCheckingActorOptions, MongoHealthChecker.props()));
    }

    private static ActorRef startChildActor(final ActorRefFactory context, final String actorName,
            final Props props) {

        return context.actorOf(props, actorName);
    }

    private static Route createRoute(final ActorSystem actorSystem, final ActorRef healthCheckingActor) {
        final StatusRoute statusRoute = new StatusRoute(new ClusterStatusSupplier(Cluster.get(actorSystem)),
                healthCheckingActor, actorSystem);

        return logRequest("http-request", () ->
                logResult("http-response", statusRoute::buildStatusRoute));
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return supervisorStrategy;
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Status.Failure.class, f -> log.error(f.cause(), "Got failure <{}>!", f))
                .matchAny(m -> {
                    log.warning("Unknown message <{}>.", m);
                    unhandled(m);
                }).build();
    }

    private void bindHttpStatusRoute(final ActorRef healthCheckingActor, final HttpConfig httpConfig,
            final ActorMaterializer materializer) {

        String hostname = httpConfig.getHostname();
        if (hostname.isEmpty()) {
            hostname = LocalHostAddressSupplier.getInstance().get();
            log.info("No explicit hostname configured, using HTTP hostname: {}", hostname);
        }

        final CompletionStage<ServerBinding> binding = Http.get(getContext().system())
                .bindAndHandle(createRoute(getContext().system(), healthCheckingActor).flow(getContext().system(),
                        materializer), ConnectHttp.toHost(hostname, httpConfig.getPort()), materializer);

        binding.thenAccept(theBinding -> CoordinatedShutdown.get(getContext().getSystem()).addTask(
                CoordinatedShutdown.PhaseServiceUnbind(), "shutdown_health_http_endpoint", () -> {
                    log.info("Gracefully shutting down status/health HTTP endpoint..");
                    return theBinding.terminate(Duration.ofSeconds(1))
                            .handle((httpTerminated, e) -> Done.getInstance());
                })
        ).exceptionally(failure -> {
            log.error(failure, "Something very bad happened: {}", failure.getMessage());
            getContext().system().terminate();
            return null;
        });
    }

}
