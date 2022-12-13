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
package org.eclipse.ditto.base.service;

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.lang.management.ManagementFactory;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.common.DittoSystemProperties;
import org.eclipse.ditto.base.model.signals.FeatureToggle;
import org.eclipse.ditto.base.service.config.ServiceSpecificConfig;
import org.eclipse.ditto.base.service.devops.DevOpsCommandsActor;
import org.eclipse.ditto.base.service.devops.LogbackLoggingFacade;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.config.InstanceIdentifierSupplier;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.config.raw.RawConfigSupplier;
import org.eclipse.ditto.internal.utils.health.status.StatusSupplierActor;
import org.eclipse.ditto.internal.utils.metrics.prometheus.PrometheusReporterRoute;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.CoordinatedShutdown;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.pubsub.DistributedPubSub;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.Uri;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.management.javadsl.AkkaManagement;
import ch.qos.logback.classic.LoggerContext;
import kamon.Kamon;
import kamon.prometheus.PrometheusReporter;

/**
 * Abstract base implementation of a Ditto service which takes care of the complete startup procedure.
 * <p>
 * Each hook method may be overridden to change this particular part of the startup procedure. Please have a look at the
 * Javadoc comment before overriding a hook method. The hook methods are automatically called in the following order:
 * </p>
 * <ol>
 * <li>{@link #determineRawConfig()},</li>
 * <li>{@link #createActorSystem(com.typesafe.config.Config)},</li>
 * <li>{@link #startStatusSupplierActor(akka.actor.ActorSystem)},</li>
 * <li>{@link #startServiceRootActors(akka.actor.ActorSystem, org.eclipse.ditto.base.service.config.ServiceSpecificConfig)}.
 * <ol>
 * <li>{@link #getMainRootActorProps(org.eclipse.ditto.base.service.config.ServiceSpecificConfig, akka.actor.ActorRef)},</li>
 * <li>{@link #startMainRootActor(akka.actor.ActorSystem, akka.actor.Props)},</li>
 * </ol>
 * </li>
 * </ol>
 *
 * @param <C> type of service specific config.
 */
@NotThreadSafe
public abstract class DittoService<C extends ServiceSpecificConfig> {

    /**
     * Name of the cluster of this service.
     */
    public static final String CLUSTER_NAME = "ditto-cluster";

    /**
     * The config path expression which points to the supposed nested config with the Ditto settings.
     */
    public static final String DITTO_CONFIG_PATH = ScopedConfig.DITTO_SCOPE;

    protected static final String MONGO_URI_CONFIG_PATH = "akka.contrib.persistence.mongodb.mongo.mongouri";

    protected final Config rawConfig;
    protected final C serviceSpecificConfig;
    private final Logger logger;
    private final String serviceName;
    private final String rootActorName;

    @Nullable
    private PrometheusReporter prometheusReporter;

    /**
     * Constructs a new {@code DittoService} object.
     *
     * @param logger the Logger to be used for logging.
     * @param serviceName the name of this service.
     * @param rootActorName the name of this service's root actor.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code serviceName} or {@code rootActorName} is empty.
     */
    protected DittoService(final Logger logger, final String serviceName, final String rootActorName) {
        this.logger = checkNotNull(logger, "logger");
        this.serviceName = argumentNotEmpty(serviceName, "service name");
        this.rootActorName = argumentNotEmpty(rootActorName, "root actor name");
        rawConfig = determineRawConfig();
        serviceSpecificConfig = getServiceSpecificConfig(tryToGetDittoConfigOrEmpty(rawConfig));
        if (null == serviceSpecificConfig) {
            throw new DittoConfigError("The service specific config must not be null!");
        }

        logger.debug("Using service specific config: <{}>.", serviceSpecificConfig);
    }

    /**
     * Starts this service. Any thrown {@code Throwable}s will be logged and re-thrown.
     *
     * @return the created ActorSystem during startup
     */
    public ActorSystem start() {
        return MainMethodExceptionHandler.getInstance(logger).call(this::doStart);
    }

    /**
     * Determines the {@link com.typesafe.config.Config} of this service. May be overridden to change the way how the config is determined.
     *
     * @return the config of this service.
     */
    private Config determineRawConfig() {
        final var loadedConfig = RawConfigSupplier.of(serviceName).get();

        if (logger.isDebugEnabled()) {
            logger.debug("Using config <{}>", loadedConfig.root().render(ConfigRenderOptions.concise()));
        }
        return loadedConfig;
    }

    private Config appendDittoInfo(final Config config) {
        final String instanceId = InstanceIdentifierSupplier.getInstance().get();

        final ConfigValue service = ConfigFactory.empty()
                .withValue("name", ConfigValueFactory.fromAnyRef(serviceName))
                .withValue("instance-id", ConfigValueFactory.fromAnyRef(instanceId))
                .root();

        final ConfigValue vmArgs =
                ConfigValueFactory.fromIterable(ManagementFactory.getRuntimeMXBean().getInputArguments());

        final ConfigValue env = ConfigValueFactory.fromMap(System.getenv());

        return config.withValue("ditto.info",
                ConfigFactory.empty()
                        .withValue("service", service)
                        .withValue("vm-args", vmArgs)
                        .withValue("env", env)
                        .root());
    }

    private static ScopedConfig tryToGetDittoConfigOrEmpty(final Config rawConfig) {
        try {
            return getDittoConfigOrEmpty(rawConfig);
        } catch (final ConfigException.WrongType e) {
            final var msgPattern = "Value at <{0}> was not of type Config!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, DITTO_CONFIG_PATH), e);
        }
    }

    private static ScopedConfig getDittoConfigOrEmpty(final Config rawConfig) {
        if (rawConfig.hasPath(DITTO_CONFIG_PATH)) {
            return DefaultScopedConfig.dittoScoped(rawConfig);
        }
        return DefaultScopedConfig.empty(DITTO_CONFIG_PATH);
    }

    /**
     * Returns the service specific config based on the given Config.
     *
     * @param dittoConfig the general Config of the service at path {@value #DITTO_CONFIG_PATH}.
     * @return the service specific config.
     */
    protected abstract C getServiceSpecificConfig(ScopedConfig dittoConfig);

    /**
     * Starts this service.
     * <p>
     * May be overridden to <em>completely</em> change the way how this service is started.
     * <em>Note: If this method is overridden, no other method of this class will be called automatically.</em>
     * </p>
     *
     * @return the created ActorSystem during startup
     */
    private ActorSystem doStart() {
        logRuntimeParameters();
        final var actorSystemConfig = appendDittoInfo(appendAkkaPersistenceMongoUriToRawConfig());
        startKamon();
        final var actorSystem = createActorSystem(actorSystemConfig);
        initializeActorSystem(actorSystem);
        startKamonPrometheusHttpEndpoint(actorSystem);
        return actorSystem;
    }

    protected Config appendAkkaPersistenceMongoUriToRawConfig() {
        return rawConfig;
    }

    private void logRuntimeParameters() {
        final var bean = ManagementFactory.getRuntimeMXBean();
        logger.info("Running with following runtime parameters: <{}>.", bean.getInputArguments());
        logger.info("Available processors: <{}>.", Runtime.getRuntime().availableProcessors());
    }

    private void startKamon() {
        final var kamonConfig = ConfigFactory.load("kamon");
        Kamon.reconfigure(kamonConfig);

        final var metricsConfig = serviceSpecificConfig.getMetricsConfig();
        final var tracingConfig = serviceSpecificConfig.getTracingConfig();

        if (metricsConfig.isSystemMetricsEnabled() || tracingConfig.isTracingEnabled()) {
            // start system metrics collection
            Kamon.init();
        }
        if (metricsConfig.isPrometheusEnabled()) {
            // start prometheus reporter
            startPrometheusReporter();
        }

        DittoTracing.init(tracingConfig);
    }

    private void startPrometheusReporter() {
        try {
            prometheusReporter = PrometheusReporter.create();
            Kamon.addReporter("prometheus reporter", prometheusReporter);
            logger.info("Successfully added Prometheus reporter to Kamon.");
        } catch (final Exception ex) {
            logger.error("Error while adding Prometheus reporter to Kamon.", ex);
        }
    }

    /**
     * Starts the Akka actor system as well as all required actors.
     * <p>
     * May be overridden to change the way how the Akka actor system and actors are started. <em>Note: If this method is
     * overridden, none of the following mentioned methods and their descendant methods will be called
     * automatically:</em>
     * </p>
     * <ul>
     * <li>{@link #startStatusSupplierActor(ActorSystem)},</li>
     * <li>{@link #startDevOpsCommandsActor(ActorSystem)},</li>
     * <li>{@link #startServiceRootActors(ActorSystem, org.eclipse.ditto.base.service.config.ServiceSpecificConfig)}.</li>
     * </ul>
     *
     * @param actorSystem the Akka ActorSystem to be initialized.
     */
    private void initializeActorSystem(final ActorSystem actorSystem) {
        startAkkaManagement(actorSystem);
        startClusterBootstrap(actorSystem);

        startStatusSupplierActor(actorSystem);
        startDevOpsCommandsActor(actorSystem);
        startServiceRootActors(actorSystem, serviceSpecificConfig);

        setUpCoordinatedShutdown(actorSystem);
    }

    /**
     * Starts Prometheus HTTP endpoint on which Prometheus may scrape the data.
     */
    private void startKamonPrometheusHttpEndpoint(final ActorSystem actorSystem) {
        final var metricsConfig = serviceSpecificConfig.getMetricsConfig();
        if (metricsConfig.isPrometheusEnabled() && null != prometheusReporter) {
            final String prometheusHostname = metricsConfig.getPrometheusHostname();
            final int prometheusPort = metricsConfig.getPrometheusPort();
            final var prometheusReporterRoute = PrometheusReporterRoute
                    .buildPrometheusReporterRoute(prometheusReporter);

            Http.get(actorSystem)
                    .newServerAt(prometheusHostname, prometheusPort)
                    .bindFlow(prometheusReporterRoute.flow(actorSystem))
                    .thenAccept(theBinding -> {
                        // prometheus requests don't get the luxury of being processed a long time after shutdown
                        theBinding.addToCoordinatedShutdown(Duration.ofSeconds(1), actorSystem);
                        logger.info("Created new server binding for Kamon Prometheus HTTP endpoint.");
                    })
                    .exceptionally(failure -> {
                        logger.error("Kamon Prometheus HTTP endpoint could not be started: {}", failure.getMessage(),
                                failure);
                        logger.error("Terminating ActorSystem!");
                        actorSystem.terminate();
                        return null;
                    });
        }
    }

    /**
     * Creates the Akka actor system. May be overridden to change the way how the actor system is created.
     *
     * @param config the configuration settings of this service.
     * @return the actor system.
     */
    private ActorSystem createActorSystem(final Config config) {
        return ActorSystem.create(CLUSTER_NAME, config);
    }

    private void startAkkaManagement(final ActorSystem actorSystem) {
        logger.info("Starting AkkaManagement ...");
        final var akkaManagement = AkkaManagement.get(actorSystem);
        final CompletionStage<Uri> startPromise = akkaManagement.start();
        startPromise.whenComplete((uri, throwable) -> {
            if (null != throwable) {
                logger.error("Error during start of AkkaManagement: <{}>!", throwable.getMessage(), throwable);
            } else {
                logger.info("Started AkkaManagement on URI <{}>.", uri);
            }
        });
    }

    private void startClusterBootstrap(final ActorSystem actorSystem) {
        logger.info("Starting ClusterBootstrap ...");
        final var clusterBootstrap = ClusterBootstrap.get(actorSystem);
        clusterBootstrap.start();
    }

    /**
     * Starts the {@link org.eclipse.ditto.internal.utils.health.status.StatusSupplierActor}.
     * May be overridden to change the way how the actor is started.
     *
     * @param actorSystem Akka actor system for starting actors.
     */
    private void startStatusSupplierActor(final ActorSystem actorSystem) {
        startActor(actorSystem, StatusSupplierActor.props(rootActorName), StatusSupplierActor.ACTOR_NAME);
    }

    private ActorRef startActor(final ActorSystem actorSystem, final Props actorProps, final String actorName) {
        logStartingActor(actorName);
        return actorSystem.actorOf(actorProps, actorName);
    }

    private void logStartingActor(final String actorName) {
        logger.info("Starting actor <{}>.", actorName);
    }

    /**
     * Starts the {@link org.eclipse.ditto.base.service.devops.DevOpsCommandsActor}.
     * May be overridden to change the way how the actor is started.
     *
     * @param actorSystem Akka actor system for starting actors.
     */
    private void startDevOpsCommandsActor(final ActorSystem actorSystem) {
        startActor(actorSystem, DevOpsCommandsActor.props(LogbackLoggingFacade.newInstance(), serviceName,
                InstanceIdentifierSupplier.getInstance().get()), DevOpsCommandsActor.ACTOR_NAME);
    }

    /**
     * Starts the root actor(s) of this service.
     * <p>
     * May be overridden to change the way how the root actor(s) of this service are started. <em>Note: If this method
     * is overridden, the following methods will not be called automatically:</em>
     * </p>
     * <ul>
     * <li>{@link #getMainRootActorProps(org.eclipse.ditto.base.service.config.ServiceSpecificConfig, akka.actor.ActorRef)},</li>
     * <li>{@link #startMainRootActor(akka.actor.ActorSystem, akka.actor.Props)},</li>
     * </ul>
     *
     * @param actorSystem Akka actor system for starting actors.
     * @param serviceSpecificConfig the configuration settings of this service.
     */
    private void startServiceRootActors(final ActorSystem actorSystem, final C serviceSpecificConfig) {
        logger.info("Waiting for member to be up before proceeding with further initialisation.");

        Cluster.get(actorSystem).registerOnMemberUp(() -> {
            logger.info("Member successfully joined the cluster, instantiating remaining actors.");

            final ActorRef pubSubMediator = getDistributedPubSubMediatorActor(actorSystem);

            injectSystemPropertiesLimits(serviceSpecificConfig);

            startMainRootActor(actorSystem, getMainRootActorProps(serviceSpecificConfig, pubSubMediator));
            RootActorStarter.get(actorSystem, ScopedConfig.dittoExtension(actorSystem.settings().config())).execute();
        });
    }

    /**
     * Sets system properties with the limits of entities used in the Ditto services. Those limits are applied in
     * {@code Command}s, e.g. when creating a {@code Thing}.
     * <p>
     * May be overwritten to specify more/other limits.
     * </p>
     *
     * @param serviceSpecificConfig the Ditto serviceSpecificConfig providing the limits from configuration
     */
    private void injectSystemPropertiesLimits(final C serviceSpecificConfig) {
        final var limitsConfig = serviceSpecificConfig.getLimitsConfig();
        System.setProperty(DittoSystemProperties.DITTO_LIMITS_THINGS_MAX_SIZE_BYTES,
                Long.toString(limitsConfig.getThingsMaxSize()));
        System.setProperty(DittoSystemProperties.DITTO_LIMITS_POLICIES_MAX_SIZE_BYTES,
                Long.toString(limitsConfig.getPoliciesMaxSize()));
        System.setProperty(DittoSystemProperties.DITTO_LIMITS_MESSAGES_MAX_SIZE_BYTES,
                Long.toString(limitsConfig.getMessagesMaxSize()));
        System.setProperty(FeatureToggle.MERGE_THINGS_ENABLED,
                Boolean.toString(rawConfig.getBoolean(FeatureToggle.MERGE_THINGS_ENABLED)));
        System.setProperty(DittoSystemProperties.DITTO_LIMITS_POLICY_IMPORTS_LIMIT,
                Integer.toString(limitsConfig.getPolicyImportsLimit()));
    }

    private static ActorRef getDistributedPubSubMediatorActor(final ActorSystem actorSystem) {
        return DistributedPubSub.get(actorSystem).mediator();
    }

    /**
     * Returns the Props of this service's main root actor.
     *
     * @param serviceSpecificConfig the configuration of this service.
     * @param pubSubMediator ActorRef of the distributed pub-sub-mediator.
     * @return the Props.
     */
    protected abstract Props getMainRootActorProps(C serviceSpecificConfig, final ActorRef pubSubMediator);

    /**
     * Starts the main root actor of this service. May be overridden to change the way of starting this service's root
     * actor.
     *
     * @param actorSystem Akka actor system for starting actors.
     * @param mainRootActorProps the Props of the main root actor.
     */
    private ActorRef startMainRootActor(final ActorSystem actorSystem, final Props mainRootActorProps) {
        return startActor(actorSystem, mainRootActorProps, rootActorName);
    }

    private void setUpCoordinatedShutdown(final ActorSystem actorSystem) {
        final var coordinatedShutdown = CoordinatedShutdown.get(actorSystem);

        coordinatedShutdown.addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind(),
                "log_shutdown_initiation", () -> {
                    logger.info("Initiated coordinated shutdown; gracefully shutting down ...");
                    coordinatedShutdown.getShutdownReason().ifPresent(reason ->
                            logger.info("Shutdown reason was - <{}>", reason));
                    final CompletionStage<Done> stop = AkkaManagement.get(actorSystem).stop();
                    return stop.thenApply(done -> {
                        logger.info("AkkaManagement stopped!");
                        return done;
                    });

                });
        coordinatedShutdown.addTask(CoordinatedShutdown.PhaseActorSystemTerminate(),
                "log_successful_graceful_shutdown", () -> {
                    logger.info("Graceful shutdown completed.");
                    // close logback-classic logging correctly in order to flush/get the last logs:
                    final var loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
                    loggerContext.stop();
                    return CompletableFuture.completedFuture(Done.getInstance());
                });
    }

}
