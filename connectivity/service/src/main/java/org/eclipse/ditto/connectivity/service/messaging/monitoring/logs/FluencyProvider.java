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
package org.eclipse.ditto.connectivity.service.messaging.monitoring.logs;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.FluencyLoggerPublisherConfig;
import org.eclipse.ditto.connectivity.service.config.LoggerPublisherConfig;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;

import org.apache.pekko.Done;
import org.apache.pekko.actor.AbstractExtensionId;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.CoordinatedShutdown;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.actor.Extension;
import org.komamitsu.fluency.Fluency;

/**
 * Pekko Extension that provides a shared {@link Fluency} instance for publishing connection logs to Fluentd/FluentBit.
 * <p>
 * The Fluency client is designed to have one instance per application. This extension ensures exactly one instance
 * per ActorSystem and registers a {@link CoordinatedShutdown} task to flush and close the forwarder on graceful
 * shutdown.
 *
 * @see org.eclipse.ditto.connectivity.service.messaging.tunnel.SshClientProvider
 */
final class FluencyProvider implements Extension {

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(FluencyProvider.class);

    @Nullable private final Fluency fluency;
    @Nullable private final FluentPublishingConnectionLoggerContext context;

    private FluencyProvider(final ActorSystem actorSystem) {
        final ConnectivityConfig connectivityConfig =
                ConnectivityConfig.of(actorSystem.settings().config());
        final LoggerPublisherConfig publisherConfig =
                connectivityConfig.getMonitoringConfig().logger().getLoggerPublisherConfig();

        if (publisherConfig.isEnabled()) {
            final FluencyLoggerPublisherConfig fluencyConfig = publisherConfig.getFluencyLoggerPublisherConfig();
            fluency = fluencyConfig.buildFluencyLoggerPublisher();
            context = ConnectionLoggerFactory.newPublishingLoggerContext(fluency,
                    fluencyConfig.getWaitUntilAllBufferFlushedDurationOnClose(),
                    publisherConfig.getLogLevels(),
                    publisherConfig.isLogHeadersAndPayload(),
                    publisherConfig.getLogTag().orElse(null),
                    publisherConfig.getAdditionalLogContext()
            );

            LOGGER.info("Fluency connection log publisher enabled.");

            CoordinatedShutdown.get(actorSystem)
                    .addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate(),
                            "close_fluency_forwarder",
                            () -> {
                                LOGGER.info("Flushing and closing Fluency forwarder before shutdown.");
                                try {
                                    fluency.waitUntilAllBufferFlushed(
                                            (int) fluencyConfig
                                                    .getWaitUntilAllBufferFlushedDurationOnClose()
                                                    .getSeconds());
                                } catch (final InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                } catch (final Exception e) {
                                    LOGGER.warn("Error flushing Fluency forwarder: <{}>: {}",
                                            e.getClass().getSimpleName(), e.getMessage());
                                }
                                try {
                                    fluency.close();
                                } catch (final Exception e) {
                                    LOGGER.warn("Error closing Fluency forwarder: <{}>: {}",
                                            e.getClass().getSimpleName(), e.getMessage());
                                }
                                return CompletableFuture.completedFuture(Done.getInstance());
                            });
        } else {
            fluency = null;
            context = null;
            LOGGER.info("Fluency connection log publisher is disabled.");
        }
    }

    /**
     * @return the {@link FluentPublishingConnectionLoggerContext} if the publisher is enabled, empty otherwise.
     */
    Optional<FluentPublishingConnectionLoggerContext> getContext() {
        return Optional.ofNullable(context);
    }

    /**
     * Load the {@code FluencyProvider} extension.
     *
     * @param actorSystem the actor system in which to load the provider.
     * @return the {@link FluencyProvider}.
     */
    static FluencyProvider get(final ActorSystem actorSystem) {
        return ExtensionId.INSTANCE.get(actorSystem);
    }

    private static final class ExtensionId extends AbstractExtensionId<FluencyProvider> {

        private static final ExtensionId INSTANCE = new ExtensionId();

        @Override
        public FluencyProvider createExtension(final ExtendedActorSystem system) {
            return new FluencyProvider(system);
        }
    }

}
