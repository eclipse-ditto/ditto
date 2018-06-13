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
package org.eclipse.ditto.services.base;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.base.config.ServiceConfigReader;
import org.eclipse.ditto.services.utils.cluster.ClusterMemberAwareActor;
import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.eclipse.ditto.services.utils.devops.DevOpsCommandsActor;
import org.eclipse.ditto.services.utils.devops.LogbackLoggingFacade;
import org.eclipse.ditto.services.utils.health.status.StatusSupplierActor;
import org.slf4j.Logger;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import akka.actor.CoordinatedShutdown;
import akka.actor.Props;
import akka.actor.Scheduler;
import akka.cluster.Cluster;
import akka.cluster.pubsub.DistributedPubSub;
import akka.management.AkkaManagement;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.stream.ActorMaterializer;
import akka.stream.stage.TimerMessages;
import kamon.Kamon;

/**
 * Abstract base implementation of a Ditto service which takes care of the complete startup procedure.
 * <p>
 * This class provides the template method {@link #startActorSystem()} which by default starts the Akka actor system as
 * well as all Akka actors of this service which are required for startup.
 * </p>
 * <p>
 * Each hook method may be overridden to change this particular part of the startup procedure. Please have a look at
 * the Javadoc comment before overriding a hook method. The hook methods are automatically called in the following
 * order:
 * </p>
 * <ol>
 * <li>{@link #determineConfig()},</li>
 * <li>{@link #createActorSystem(Config)},</li>
 * <li>{@link #startStatusSupplierActor(ActorSystem, Config)},</li>
 * <li>{@link #startClusterMemberAwareActor(ActorSystem, ServiceConfigReader)} and</li>
 * <li>{@link #startServiceRootActors(ActorSystem, ServiceConfigReader)}.
 * <ol>
 * <li>{@link #startStatsdMetricsReporter(ActorSystem, ServiceConfigReader)},</li>
 * <li>{@link #getMainRootActorProps(ServiceConfigReader, ActorRef, ActorMaterializer)},</li>
 * <li>{@link #startMainRootActor(ActorSystem, Props)},</li>
 * <li>{@link #getAdditionalRootActorsInformation(ServiceConfigReader, ActorRef, ActorMaterializer)} and</li>
 * <li>{@link #startAdditionalRootActors(ActorSystem, Iterable)}.</li>
 * </ol>
 * </li>
 * </ol>
 *
 * @param <C> type of configuration reader for the service.
 */
@NotThreadSafe
public abstract class DittoService<C extends ServiceConfigReader> {

    /**
     * Name of the cluster of this service.
     */
    public static final String CLUSTER_NAME = "ditto-cluster";

    private final Logger logger;
    private final String serviceName;
    private final String rootActorName;
    private final C configReader;

    /**
     * Constructs a new {@code DittoService} object.
     *
     * @param logger the Logger to be used for logging.
     * @param serviceName the name of this service.
     * @param rootActorName the name of this service's root actor.
     * @param configReaderCreator creator of a service config reader.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws java.lang.IllegalArgumentException if {@code serviceName} or {@code rootActorName} is empty.
     */
    protected DittoService(final Logger logger,
            final String serviceName,
            final String rootActorName,
            final Function<Config, C> configReaderCreator) {

        this.logger = checkNotNull(logger, "logger");
        this.serviceName = argumentNotEmpty(serviceName, "service name");
        this.rootActorName = argumentNotEmpty(rootActorName, "root actor name");
        final Config config = determineConfig();
        this.configReader = checkNotNull(configReaderCreator, "config reader creator").apply(config);
    }

    /**
     * Starts this service. Any thrown {@code Throwable}s will be logged and re-thrown.
     */
    public void start() {
        MainMethodExceptionHandler.getInstance(logger).run(this::doStart);
    }

    /**
     * Starts this service.
     * <p>
     * May be overridden to <em>completely</em> change the way how this service is started.
     * <em>Note: If this method is overridden, no other method of this class will be called automatically.</em>
     * </p>
     */
    protected void doStart() {
        logRuntimeParameters();
        startKamon();
        startActorSystem();
    }

    private void logRuntimeParameters() {
        final RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
        logger.info("Running with following runtime parameters: {}", bean.getInputArguments());
        logger.info("Available processors: {}", Runtime.getRuntime().availableProcessors());
    }

    private static void startKamon() {
        Kamon.start(ConfigFactory.load("kamon"));
    }

    /**
     * Starts the Akka actor system as well as all required actors.
     * <p>
     * May be overridden to change the way how the Akka actor system and actors are started. <em>Note: If this
     * method is overridden, none of the following mentioned methods and their descendant methods will be called
     * automatically:</em>
     * </p>
     * <ul>
     * <li>{@link #determineConfig()},</li>
     * <li>{@link #createActorSystem(Config)},</li>
     * <li>{@link #startStatusSupplierActor(ActorSystem, Config)},</li>
     * <li>{@link #startClusterMemberAwareActor(ActorSystem, ServiceConfigReader)} and</li>
     * <li>{@link #startServiceRootActors(ActorSystem, ServiceConfigReader)}.</li>
     * </ul>
     */
    protected void startActorSystem() {
        final Config config = configReader.getRawConfig();
        final double parallelismMax =
                config.getDouble("akka.actor.default-dispatcher.fork-join-executor.parallelism-max");
        logger.info("Running 'default-dispatcher' with 'parallelism-max': <{}>", parallelismMax);
        final ActorSystem actorSystem = createActorSystem(config);

        AkkaManagement.get(actorSystem).start();
        ClusterBootstrap.get(actorSystem).start();

        startStatusSupplierActor(actorSystem, config);
        startDevOpsCommandsActor(actorSystem, config);
        startClusterMemberAwareActor(actorSystem, configReader);
        startServiceRootActors(actorSystem, configReader);

        final AtomicBoolean gracefulShutdown = new AtomicBoolean(false);

        CoordinatedShutdown.get(actorSystem).addTask(
                CoordinatedShutdown.PhaseBeforeServiceUnbind(), "Log shutdown initiation",
                () -> {
                    logger.info("Initiated coordinated shutdown - gracefully shutting down..");
                    return CompletableFuture.completedFuture(Done.getInstance());
                });

        CoordinatedShutdown.get(actorSystem).addTask(
                CoordinatedShutdown.PhaseBeforeActorSystemTerminate(), "Log successful graceful shutdown",
                () -> {
                    logger.info("Graceful shutdown completed.");
                    gracefulShutdown.set(true);
                    return CompletableFuture.completedFuture(Done.getInstance());
                });

        actorSystem.registerOnTermination(() -> {
            if (gracefulShutdown.get()) {
                exit(0);
            } else {
                exit(-1);
            }
        });
    }

    private void exit(int status) {
        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        final String message = String.format("Exiting JVM with status code '%d'", status);
        scheduler.schedule(() -> {
            if(status == 0) {
                logger.info(message);
            }else {
                logger.warn(message);
            }
            System.exit(status);
        }, 0, TimeUnit.SECONDS);
    }

    /**
     * Determines the {@link Config} of this service. May be overridden to change the way how the config is determined.
     *
     * @return the config of this service.
     */
    protected Config determineConfig() {
        return ConfigUtil.determineConfig(serviceName);
    }

    /**
     * Creates the Akka actor system. May be overridden to change the way how the actor system is created.
     *
     * @param config the configuration settings of this service.
     * @return the actor system.
     */
    protected ActorSystem createActorSystem(final Config config) {
        return ActorSystem.create(CLUSTER_NAME, config);
    }

    /**
     * Starts the {@link StatusSupplierActor}. May be overridden to change the way how the actor is started.
     *
     * @param actorSystem Akka actor system for starting actors.
     * @param config the configuration settings of this service.
     */
    protected void startStatusSupplierActor(final ActorSystem actorSystem, final Config config) {
        startActor(actorSystem, StatusSupplierActor.props(rootActorName), StatusSupplierActor.ACTOR_NAME);
    }

    private void startActor(final ActorSystem actorSystem, final Props actorProps, final String actorName) {
        logStartingActor(actorName);
        actorSystem.actorOf(actorProps, actorName);
    }

    private void logStartingActor(final String actorName) {
        logger.info("Starting actor <{}>.", actorName);
    }

    /**
     * Starts the {@link DevOpsCommandsActor}. May be overridden to change the way how the actor is started.
     *
     * @param actorSystem Akka actor system for starting actors.
     * @param config the configuration settings of this service.
     */
    protected void startDevOpsCommandsActor(final ActorSystem actorSystem, final Config config) {
        startActor(actorSystem, DevOpsCommandsActor.props(LogbackLoggingFacade.newInstance(), serviceName,
                ConfigUtil.instanceIndex()), DevOpsCommandsActor.ACTOR_NAME);
    }

    /**
     * Starts the {@link ClusterMemberAwareActor}. May be overridden to change the way how the actor is started.
     *
     * @param actorSystem Akka actor system for starting actors.
     * @param configReader the config reader of this service.
     */
    protected void startClusterMemberAwareActor(final ActorSystem actorSystem, final C configReader) {
        startActor(actorSystem, ClusterMemberAwareActor.props(serviceName, isMajorityCheckEnabled(configReader),
                getMajorityCheckDelay(configReader)), ClusterMemberAwareActor.ACTOR_NAME);
    }

    private boolean isMajorityCheckEnabled(final ServiceConfigReader configReader) {
        return configReader.cluster().majorityCheckEnabled();

    }

    private Duration getMajorityCheckDelay(final ServiceConfigReader configReader) {
        return configReader.cluster().majorityCheckDelay();
    }

    /**
     * Starts the root actor(s) of this service.
     * <p>
     * May be overridden to change the way how the root actor(s) of this service are started. <em>Note: If this
     * method is overridden, the following methods will not be called automatically:</em>
     * </p>
     * <ul>
     * <li>{@link #startStatsdMetricsReporter(ActorSystem, ServiceConfigReader)},</li>
     * <li>{@link #getMainRootActorProps(ServiceConfigReader, ActorRef, ActorMaterializer)},</li>
     * <li>{@link #startMainRootActor(ActorSystem, Props)},</li>
     * <li>{@link #getAdditionalRootActorsInformation(ServiceConfigReader, ActorRef, ActorMaterializer)} and</li>
     * <li>{@link #startAdditionalRootActors(ActorSystem, Iterable)}.</li>
     * </ul>
     *
     * @param actorSystem Akka actor system for starting actors.
     * @param configReader the configuration settings of this service.
     */
    protected void startServiceRootActors(final ActorSystem actorSystem, final C configReader) {

        logger.info("Waiting for member to be up before proceeding with further initialisation.");
        Cluster.get(actorSystem).registerOnMemberUp(() -> {
            logger.info("Member successfully joined the cluster, instantiating remaining actors.");

            startStatsdMetricsReporter(actorSystem, configReader);

            final ActorRef pubSubMediator = getDistributedPubSubMediatorActor(actorSystem);
            final ActorMaterializer materializer = createActorMaterializer(actorSystem);

            startMainRootActor(actorSystem, getMainRootActorProps(configReader, pubSubMediator, materializer));
            startAdditionalRootActors(actorSystem, getAdditionalRootActorsInformation(configReader, pubSubMediator,
                    materializer));
        });
    }

    /**
     * May be overridden to start a StatsD metrics reporter. <em>The base implementation does nothing.</em>
     *
     * @param actorSystem Akka actor system for starting actors.
     * @param configReader the configuration reader of this service.
     */
    protected void startStatsdMetricsReporter(final ActorSystem actorSystem, final C configReader) {
        // Does nothing by default.
    }

    private static ActorRef getDistributedPubSubMediatorActor(final ActorSystem actorSystem) {
        return DistributedPubSub.get(actorSystem).mediator();
    }

    private static ActorMaterializer createActorMaterializer(final ActorRefFactory actorSystem) {
        return ActorMaterializer.create(actorSystem);
    }

    /**
     * Returns the Props of this service's main root actor.
     *
     * @param configReader the configuration reader of this service.
     * @param pubSubMediator ActorRef of the distributed pub-sub-mediator.
     * @param materializer the materializer for the Akka actor system.
     * @return the Props.
     */
    protected abstract Props getMainRootActorProps(C configReader, ActorRef pubSubMediator,
            ActorMaterializer materializer);

    /**
     * Starts the main root actor of this service. May be overridden to change the way of starting this service's root
     * actor.
     *
     * @param actorSystem Akka actor system for starting actors.
     * @param mainRootActorProps the Props of the main root actor.
     */
    protected void startMainRootActor(final ActorSystem actorSystem, final Props mainRootActorProps) {
        startActor(actorSystem, mainRootActorProps, rootActorName);
    }

    /**
     * May be overridden to return information of additional root actors of this service. <em>The base implementation
     * returns an empty collection.</em>
     *
     * @param configReader the configuration reader of this service.
     * @param pubSubMediator ActorRef of the distributed pub-sub-mediator.
     * @param materializer the materializer for the Akka actor system.
     * @return the additional root actors information.
     */
    protected Collection<RootActorInformation> getAdditionalRootActorsInformation(final C configReader,
            final ActorRef pubSubMediator, final ActorMaterializer materializer) {

        return Collections.emptyList();
    }

    /**
     * Starts additional root actors of this service. May be overridden to change the way how additional root actors
     * will be started.
     *
     * @param actorSystem Akka actor system for starting actors.
     * @param additionalRootActorsInformation information of additional root actors to be started.
     */
    protected void startAdditionalRootActors(final ActorSystem actorSystem,
            final Iterable<RootActorInformation> additionalRootActorsInformation) {

        for (final RootActorInformation rootActorInformation : additionalRootActorsInformation) {
            startActor(actorSystem, rootActorInformation.props, rootActorInformation.name);
        }
    }

    /**
     * This class bundles meta information of this service's root actor.
     */
    @Immutable
    public static final class RootActorInformation {

        private final Props props;
        private final String name;

        private RootActorInformation(final Props theProps, final String theName) {
            props = theProps;
            name = theName;
        }

        /**
         * Returns an instance of {@code RootActorInformation}.
         *
         * @param props the Props of the root actor.
         * @param name the name of the root actor.
         * @return the instance.
         * @throws NullPointerException if any argument is {@code null}.
         * @throws IllegalArgumentException if {@code name} is empty.
         */
        public static RootActorInformation getInstance(final Props props, final String name) {
            checkNotNull(props, "root actor props");
            argumentNotEmpty(name, "root actor name");

            return new RootActorInformation(props, name);
        }

    }

}
