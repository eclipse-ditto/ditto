/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.concierge.starter.actors;

import static akka.http.javadsl.server.Directives.logRequest;
import static akka.http.javadsl.server.Directives.logResult;
import static java.util.Objects.requireNonNull;

import java.net.ConnectException;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.services.base.config.HealthConfigReader;
import org.eclipse.ditto.services.base.config.HttpConfigReader;
import org.eclipse.ditto.services.concierge.batch.actors.BatchSupervisorActor;
import org.eclipse.ditto.services.concierge.starter.proxy.AbstractEnforcerActorFactory;
import org.eclipse.ditto.services.concierge.util.config.AbstractConciergeConfigReader;
import org.eclipse.ditto.services.models.concierge.ConciergeMessagingConstants;
import org.eclipse.ditto.services.models.concierge.actors.ConciergeForwarderActor;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.cluster.ClusterStatusSupplier;
import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.eclipse.ditto.services.utils.config.MongoConfig;
import org.eclipse.ditto.services.utils.health.DefaultHealthCheckingActorFactory;
import org.eclipse.ditto.services.utils.health.HealthCheckingActorOptions;
import org.eclipse.ditto.services.utils.health.routes.StatusRoute;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientActor;

import akka.actor.AbstractActor;
import akka.actor.ActorInitializationException;
import akka.actor.ActorKilledException;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.InvalidActorNameException;
import akka.actor.OneForOneStrategy;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.Status;
import akka.actor.SupervisorStrategy;
import akka.cluster.Cluster;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.cluster.sharding.ShardRegion;
import akka.cluster.singleton.ClusterSingletonManager;
import akka.cluster.singleton.ClusterSingletonManagerSettings;
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

    private final ActorRef conciergeShardRegion;

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

    private <C extends AbstractConciergeConfigReader> ConciergeRootActor(final C configReader,
            final ActorRef pubSubMediator,
            final AbstractEnforcerActorFactory<C> authorizationProxyPropsFactory,
            final ActorMaterializer materializer) {

        requireNonNull(configReader);
        requireNonNull(pubSubMediator);
        requireNonNull(authorizationProxyPropsFactory);
        requireNonNull(materializer);

        final ActorContext context = getContext();

        conciergeShardRegion = authorizationProxyPropsFactory.startEnforcerActor(context, configReader, pubSubMediator);

        final ActorRef conciergeForwarder = startChildActor(context, ConciergeForwarderActor.ACTOR_NAME,
                ConciergeForwarderActor.props(pubSubMediator, conciergeShardRegion));

        pubSubMediator.tell(new DistributedPubSubMediator.Put(getSelf()), getSelf());
        pubSubMediator.tell(new DistributedPubSubMediator.Put(conciergeForwarder), getSelf());

        startClusterSingletonActor(context, BatchSupervisorActor.ACTOR_NAME,
                BatchSupervisorActor.props(pubSubMediator, conciergeForwarder));

        final ActorRef healthCheckingActor = startHealthCheckingActor(context, configReader);

        final HttpConfigReader httpConfig = configReader.http();

        bindHttpStatusRoute(healthCheckingActor, httpConfig, materializer);
    }

    /**
     * Creates Akka configuration object Props for this actor.
     *
     * @param configReader the config reader.
     * @param pubSubMediator the PubSub mediator Actor.
     * @param authorizationProxyPropsFactory the {@link AbstractEnforcerActorFactory}.
     * @param materializer the materializer for the Akka actor system.
     * @return the Akka configuration Props object.
     */
    public static  <C extends AbstractConciergeConfigReader> Props props(final C configReader,
            final ActorRef pubSubMediator,
            final AbstractEnforcerActorFactory<C> authorizationProxyPropsFactory,
            final ActorMaterializer materializer) {

        return Props.create(ConciergeRootActor.class,
                () -> new ConciergeRootActor(configReader, pubSubMediator, authorizationProxyPropsFactory,
                        materializer));
    }


    private static void startClusterSingletonActor(final akka.actor.ActorContext context, final String actorName,
            final Props props) {

        final ClusterSingletonManagerSettings settings =
                ClusterSingletonManagerSettings.create(context.system())
                        .withRole(ConciergeMessagingConstants.CLUSTER_ROLE);
        context.actorOf(ClusterSingletonManager.props(props, PoisonPill.getInstance(), settings), actorName);
    }

    private static ActorRef startHealthCheckingActor(final ActorContext context,
            final AbstractConciergeConfigReader config) {

        final HealthConfigReader healthConfig = config.health();
        final String mongoUri = MongoConfig.getMongoUri(config.getRawConfig());
        final HealthCheckingActorOptions.Builder hcBuilder = HealthCheckingActorOptions
                .getBuilder(healthConfig.enabled(), healthConfig.getInterval());

        final ActorRef mongoClient = startChildActor(context, MongoClientActor.ACTOR_NAME, MongoClientActor
                .props(mongoUri, healthConfig.getPersistenceTimeout()));

        if (healthConfig.persistenceEnabled()) {
            hcBuilder.enablePersistenceCheck();
        }

        final HealthCheckingActorOptions healthCheckingActorOptions = hcBuilder.build();
        return startChildActor(context, DefaultHealthCheckingActorFactory.ACTOR_NAME,
                DefaultHealthCheckingActorFactory.props(healthCheckingActorOptions, mongoClient));

    }

    private static ActorRef startChildActor(final akka.actor.ActorContext context, final String actorName,
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
                .matchEquals(ShardRegion.getShardRegionStateInstance(), getShardRegionState ->
                        conciergeShardRegion.forward(getShardRegionState, getContext()))
                .match(Status.Failure.class, f -> log.error(f.cause(), "Got failure <{}>!", f))
                .matchAny(m -> {
                    log.warning("Unknown message <{}>.", m);
                    unhandled(m);
                }).build();
    }

    private void bindHttpStatusRoute(final ActorRef healthCheckingActor, final HttpConfigReader httpConfig,
            final ActorMaterializer materializer) {
        String hostname = httpConfig.getHostname();
        if (hostname.isEmpty()) {
            hostname = ConfigUtil.getLocalHostAddress();
            log.info("No explicit hostname configured, using HTTP hostname: {}", hostname);
        }

        final CompletionStage<ServerBinding> binding = Http.get(getContext().system())
                .bindAndHandle(createRoute(getContext().system(), healthCheckingActor).flow(getContext().system(),
                        materializer), ConnectHttp.toHost(hostname, httpConfig.getPort()), materializer);

        binding.thenAccept(this::logServerBinding)
                .exceptionally(failure -> {
                    log.error(failure, "Something very bad happened: {}", failure.getMessage());
                    getContext().system().terminate();
                    return null;
                });
    }

    private void logServerBinding(final ServerBinding serverBinding) {
        log.info("Bound to address {}:{}", serverBinding.localAddress().getHostString(),
                serverBinding.localAddress().getPort());
    }

}
