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
package org.eclipse.ditto.services.base;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.base.config.ServiceSpecificConfig;
import org.eclipse.ditto.services.base.config.limits.LimitsConfig;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.config.DittoConfigError;
import org.eclipse.ditto.services.utils.config.InstanceIdentifierSupplier;
import org.eclipse.ditto.services.utils.config.ScopedConfig;
import org.eclipse.ditto.services.utils.config.raw.RawConfigSupplier;
import org.eclipse.ditto.services.utils.devops.DevOpsCommandsActor;
import org.eclipse.ditto.services.utils.devops.LogbackLoggingFacade;
import org.eclipse.ditto.services.utils.health.status.StatusSupplierActor;
import org.eclipse.ditto.services.utils.metrics.config.MetricsConfig;
import org.eclipse.ditto.services.utils.metrics.prometheus.PrometheusReporterRoute;
import org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultSuffixBuilderConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.SuffixBuilderConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.WithMongoDbConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.suffixes.NamespaceSuffixCollectionNames;
import org.eclipse.ditto.signals.commands.messages.MessageCommandSizeValidator;
import org.eclipse.ditto.signals.commands.policies.PolicyCommandSizeValidator;
import org.eclipse.ditto.signals.commands.things.ThingCommandSizeValidator;
import org.slf4j.Logger;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import akka.actor.CoordinatedShutdown;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.pubsub.DistributedPubSub;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.server.Route;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.management.javadsl.AkkaManagement;
import akka.stream.ActorMaterializer;
import kamon.Kamon;
import kamon.prometheus.PrometheusReporter;
import kamon.system.SystemMetrics;

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
 * <li>{@link #startServiceRootActors(akka.actor.ActorSystem, org.eclipse.ditto.services.base.config.ServiceSpecificConfig)}.
 * <ol>
 * <li>{@link #getMainRootActorProps(org.eclipse.ditto.services.base.config.ServiceSpecificConfig, akka.actor.ActorRef, akka.stream.ActorMaterializer)},</li>
 * <li>{@link #startMainRootActor(akka.actor.ActorSystem, akka.actor.Props)},</li>
 * <li>{@link #getAdditionalRootActorsInformation(org.eclipse.ditto.services.base.config.ServiceSpecificConfig, akka.actor.ActorRef, akka.stream.ActorMaterializer)} and</li>
 * <li>{@link #startAdditionalRootActors(akka.actor.ActorSystem, Iterable)}.</li>
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

    private final Logger logger;
    private final String serviceName;
    private final String rootActorName;
    private final Config rawConfig;
    private final C serviceSpecificConfig;

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
     * Determines the {@link com.typesafe.config.Config} of this service. May be overridden to change the way how the config is determined.
     *
     * @return the config of this service.
     */
    protected Config determineRawConfig() {
        final Config loadedConfig = RawConfigSupplier.of(serviceName).get();

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
            final String msgPattern = "Value at <{0}> was not of type Config!";
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
     * Starts this service. Any thrown {@code Throwable}s will be logged and re-thrown.
     *
     * @return the created ActorSystem during startup
     */
    public ActorSystem start() {
        return MainMethodExceptionHandler.getInstance(logger).call(this::doStart);
    }

    /**
     * Starts this service.
     * <p>
     * May be overridden to <em>completely</em> change the way how this service is started.
     * <em>Note: If this method is overridden, no other method of this class will be called automatically.</em>
     * </p>
     *
     * @return the created ActorSystem during startup
     */
    protected ActorSystem doStart() {
        logRuntimeParameters();
        final Config actorSystemConfig = appendDittoInfo(appendAkkaPersistenceMongoUriToRawConfig());
        configureMongoDbSuffixBuilder();
        startKamon();
        final ActorSystem actorSystem = createActorSystem(actorSystemConfig);
        initializeActorSystem(actorSystem);
        startKamonPrometheusHttpEndpoint(actorSystem);
        return actorSystem;
    }

    private Config appendAkkaPersistenceMongoUriToRawConfig() {
        if (!isServiceWithMongoDbConfig()) {
            return rawConfig;
        }
        final String configPath = "akka.contrib.persistence.mongodb.mongo.mongouri";
        final MongoDbConfig mongoDbConfig = ((WithMongoDbConfig) serviceSpecificConfig).getMongoDbConfig();
        final String mongoDbUri = mongoDbConfig.getMongoDbUri();
        return rawConfig.withValue(configPath, ConfigValueFactory.fromAnyRef(mongoDbUri));
    }

    private boolean isServiceWithMongoDbConfig() {
        return serviceSpecificConfig instanceof WithMongoDbConfig;
    }

    private void logRuntimeParameters() {
        final RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
        logger.info("Running with following runtime parameters: <{}>.", bean.getInputArguments());
        logger.info("Available processors: <{}>.", Runtime.getRuntime().availableProcessors());
    }

    private void configureMongoDbSuffixBuilder() {
        if (isServiceWithMongoDbConfig()) {
            final SuffixBuilderConfig suffixBuilderConfig = DefaultSuffixBuilderConfig.of(rawConfig);
            NamespaceSuffixCollectionNames.setSupportedPrefixes(suffixBuilderConfig.getSupportedPrefixes());
        }
    }

    private void startKamon() {
        final Config kamonConfig = ConfigFactory.load("kamon");
        Kamon.reconfigure(kamonConfig);

        final MetricsConfig metricsConfig = serviceSpecificConfig.getMetricsConfig();

        if (metricsConfig.isSystemMetricsEnabled()) {
            // start system metrics collection
            SystemMetrics.startCollecting();
        }
        if (metricsConfig.isPrometheusEnabled()) {
            // start prometheus reporter
            startPrometheusReporter();
        }
    }

    private void startPrometheusReporter() {
        try {
            prometheusReporter = new PrometheusReporter();
            Kamon.addReporter(prometheusReporter);
            logger.info("Successfully added Prometheus reporter to Kamon.");
        } catch (final Throwable ex) {
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
     * <li>{@link #startServiceRootActors(ActorSystem, ServiceSpecificConfig)}.</li>
     * </ul>
     *
     * @param actorSystem the Akka ActorSystem to be initialized.
     */
    protected void initializeActorSystem(final ActorSystem actorSystem) {
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
        final MetricsConfig metricsConfig = serviceSpecificConfig.getMetricsConfig();
        if (metricsConfig.isPrometheusEnabled() && null != prometheusReporter) {
            final String prometheusHostname = metricsConfig.getPrometheusHostname();
            final int prometheusPort = metricsConfig.getPrometheusPort();
            final ActorMaterializer materializer = createActorMaterializer(actorSystem);
            final Route prometheusReporterRoute = PrometheusReporterRoute
                    .buildPrometheusReporterRoute(prometheusReporter);
            final CompletionStage<ServerBinding> binding = Http.get(actorSystem)
                    .bindAndHandle(prometheusReporterRoute.flow(actorSystem, materializer),
                            ConnectHttp.toHost(prometheusHostname, prometheusPort), materializer);

            binding.thenAccept(theBinding -> CoordinatedShutdown.get(actorSystem).addTask(
                    CoordinatedShutdown.PhaseServiceUnbind(), "shutdown_prometheus_http_endpoint", () -> {
                        logger.info("Gracefully shutting down Prometheus HTTP endpoint ...");
                        // prometheus requests don't get the luxury of being processed a long time after shutdown:
                        return theBinding.terminate(Duration.ofSeconds(1))
                                .handle((httpTerminated, e) -> Done.getInstance());
                    })
            ).exceptionally(failure -> {
                logger.error("Kamon Prometheus HTTP endpoint could not be started: {}", failure.getMessage(), failure);
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
    protected ActorSystem createActorSystem(final Config config) {
        return ActorSystem.create(CLUSTER_NAME, config);
    }

    private void startAkkaManagement(final ActorSystem actorSystem) {
        logger.info("Starting AkkaManagement ...");
        final AkkaManagement akkaManagement = AkkaManagement.get(actorSystem);
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
        final ClusterBootstrap clusterBootstrap = ClusterBootstrap.get(actorSystem);
        clusterBootstrap.start();
    }

    /**
     * Starts the {@link org.eclipse.ditto.services.utils.health.status.StatusSupplierActor}.
     * May be overridden to change the way how the actor is started.
     *
     * @param actorSystem Akka actor system for starting actors.
     */
    protected void startStatusSupplierActor(final ActorSystem actorSystem) {
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
     * Starts the {@link org.eclipse.ditto.services.utils.devops.DevOpsCommandsActor}.
     * May be overridden to change the way how the actor is started.
     *
     * @param actorSystem Akka actor system for starting actors.
     */
    protected void startDevOpsCommandsActor(final ActorSystem actorSystem) {
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
     * <li>{@link #getMainRootActorProps(org.eclipse.ditto.services.base.config.ServiceSpecificConfig, akka.actor.ActorRef, akka.stream.ActorMaterializer)},</li>
     * <li>{@link #startMainRootActor(akka.actor.ActorSystem, akka.actor.Props)},</li>
     * <li>{@link #getAdditionalRootActorsInformation(org.eclipse.ditto.services.base.config.ServiceSpecificConfig, akka.actor.ActorRef, akka.stream.ActorMaterializer)} and</li>
     * <li>{@link #startAdditionalRootActors(akka.actor.ActorSystem, Iterable)}.</li>
     * </ul>
     *
     * @param actorSystem Akka actor system for starting actors.
     * @param serviceSpecificConfig the configuration settings of this service.
     */
    protected void startServiceRootActors(final ActorSystem actorSystem, final C serviceSpecificConfig) {
        logger.info("Waiting for member to be up before proceeding with further initialisation.");
        Cluster.get(actorSystem).registerOnMemberUp(() -> {
            logger.info("Member successfully joined the cluster, instantiating remaining actors.");

            final ActorRef pubSubMediator = getDistributedPubSubMediatorActor(actorSystem);
            final ActorMaterializer materializer = createActorMaterializer(actorSystem);

            injectSystemPropertiesLimits(serviceSpecificConfig);

            startMainRootActor(actorSystem, getMainRootActorProps(serviceSpecificConfig, pubSubMediator, materializer));
            startAdditionalRootActors(actorSystem, getAdditionalRootActorsInformation(serviceSpecificConfig,
                    pubSubMediator, materializer));
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
    protected void injectSystemPropertiesLimits(final C serviceSpecificConfig) {
        final LimitsConfig limitsConfig = serviceSpecificConfig.getLimitsConfig();
        System.setProperty(ThingCommandSizeValidator.DITTO_LIMITS_THINGS_MAX_SIZE_BYTES,
                Long.toString(limitsConfig.getThingsMaxSize()));
        System.setProperty(PolicyCommandSizeValidator.DITTO_LIMITS_POLICIES_MAX_SIZE_BYTES,
                Long.toString(limitsConfig.getPoliciesMaxSize()));
        System.setProperty(MessageCommandSizeValidator.DITTO_LIMITS_MESSAGES_MAX_SIZE_BYTES,
                Long.toString(limitsConfig.getMessagesMaxSize()));
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
     * @param serviceSpecificConfig the configuration of this service.
     * @param pubSubMediator ActorRef of the distributed pub-sub-mediator.
     * @param materializer the materializer for the Akka actor system.
     * @return the Props.
     */
    protected abstract Props getMainRootActorProps(C serviceSpecificConfig, ActorRef pubSubMediator,
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
     * May be overridden to return information of additional root actors of this service.
     * <em>The base implementation returns an empty collection.</em>
     *
     * @param serviceSpecificConfig the specific configuration of this service.
     * @param pubSubMediator ActorRef of the distributed pub-sub-mediator.
     * @param materializer the materializer for the Akka actor system.
     * @return the additional root actors information.
     */
    protected Collection<RootActorInformation> getAdditionalRootActorsInformation(final C serviceSpecificConfig,
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

    private void setUpCoordinatedShutdown(final ActorSystem actorSystem) {
        final CoordinatedShutdown coordinatedShutdown = CoordinatedShutdown.get(actorSystem);
        coordinatedShutdown.addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind(), "log_shutdown_initiation", () -> {
            logger.info("Initiated coordinated shutdown; gracefully shutting down ...");
            return CompletableFuture.completedFuture(Done.getInstance());
        });
        coordinatedShutdown.addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate(),
                "log_successful_graceful_shutdown", () -> {
                    logger.info("Graceful shutdown completed.");
                    return CompletableFuture.completedFuture(Done.getInstance());
                });
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
