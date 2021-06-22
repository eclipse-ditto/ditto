/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.tunnel;

import java.util.concurrent.CompletableFuture;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.common.PropertyResolverUtils;
import org.apache.sshd.common.auth.UserAuthMethodFactory;
import org.apache.sshd.common.session.SessionHeartbeatController;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.config.AllowTcpForwardingValue;
import org.apache.sshd.server.forward.ForwardingFilter;
import org.eclipse.ditto.connectivity.service.config.DittoConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.TunnelConfig;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;

import akka.Done;
import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.CoordinatedShutdown;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;

/**
 * Provider for {@link org.apache.sshd.client.SshClient SshClient} used to tunnel connections via SSH.
 * <p>
 * The {@link org.apache.sshd.client.SshClient SshClient} is designed to have one instance of the client per
 * application which is used it to create multiple different ssh session.
 * <p>
 * See also javadocs of {@link org.apache.sshd.client.SshClient SshClient}:
 * <blockquote>
 * the idea is to have one SshClient instance for the entire application and re-use it repeatedly in order to
 * create as many sessions as necessary - possibly with different hosts, ports, users, passwords, etc. - including
 * concurrently.
 * </blockquote>
 *
 * @see org.apache.sshd.client.SshClient
 */
public final class SshClientProvider implements Extension {

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(SshClientProvider.class);
    private final SshClient sshClient;
    private static final ForwardingFilter FORWARDING_FILTER =
            ForwardingFilter.asForwardingFilter(null, null, AllowTcpForwardingValue.LOCAL);

    private SshClientProvider(final ActorSystem actorSystem) {

        final DittoConnectivityConfig connectivityConfig = DittoConnectivityConfig.of(
                DefaultScopedConfig.dittoScoped(actorSystem.settings().config()));
        final TunnelConfig tunnelConfig = connectivityConfig.getTunnelConfig();

        sshClient = SshClient.setUpDefaultClient();
        // allow only local port forwarding
        sshClient.setForwardingFilter(FORWARDING_FILTER);
        // allow only public-key and password authentication
        sshClient.setUserAuthFactoriesNames(UserAuthMethodFactory.PUBLIC_KEY, UserAuthMethodFactory.PASSWORD);

        final long idleTimeoutMs = tunnelConfig.getIdleTimeout().toMillis();
        PropertyResolverUtils.updateProperty(sshClient, CoreModuleProperties.IDLE_TIMEOUT.getName(), idleTimeoutMs);
        LOGGER.debug("Configuring socket keepalive for ssh client: {}", tunnelConfig.getSocketKeepAlive());
        PropertyResolverUtils.updateProperty(sshClient, CoreModuleProperties.SOCKET_KEEPALIVE.getName(),
                tunnelConfig.getSocketKeepAlive());

        if (tunnelConfig.getWorkers() > 0) {
            LOGGER.debug("Configuring nio workers for ssh client: {}", tunnelConfig.getWorkers());
            sshClient.setNioWorkers(tunnelConfig.getWorkers());
        }
        if (tunnelConfig.getHeartbeatInterval().toMillis() > 0) {
            LOGGER.debug("Configuring session heartbeat for ssh client: {}", tunnelConfig.getHeartbeatInterval());
            sshClient.setSessionHeartbeat(SessionHeartbeatController.HeartbeatType.IGNORE,
                    tunnelConfig.getHeartbeatInterval());
        }

        CoordinatedShutdown.get(actorSystem)
                .addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate(), "close_ssh_client",
                        () -> {
                            LOGGER.info("Closing ssh client before shutdown.");
                            final CompletableFuture<Done> done = new CompletableFuture<>();
                            sshClient.close(true).addListener(f -> done.complete(Done.getInstance()));
                            return done;
                        });

        sshClient.start();
    }

    /**
     * @return th {@link SshClient} instance
     */
    public SshClient getSshClient() {
        return sshClient;
    }

    /**
     * Load the {@code SshClientProvider}.
     *
     * @param actorSystem The actor system in which to load the provider.
     * @return the {@link SshClientProvider}.
     */
    public static SshClientProvider get(final ActorSystem actorSystem) {
        return ExtensionId.INSTANCE.get(actorSystem);
    }

    /**
     * ID of the actor system extension to provide a ssh client.
     */
    private static final class ExtensionId extends AbstractExtensionId<SshClientProvider> {

        private static final ExtensionId INSTANCE = new ExtensionId();

        @Override
        public SshClientProvider createExtension(final ExtendedActorSystem system) {
            return new SshClientProvider(system);
        }
    }

}
