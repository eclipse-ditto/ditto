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
package org.eclipse.ditto.services.utils.cluster;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.eclipse.ditto.services.utils.config.ConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;

import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.actor.Terminated;
import akka.cluster.Cluster;
import akka.dispatch.ExecutionContexts;
import akka.dispatch.OnComplete;

/**
 * Utilities for Akka Cluster.
 */
public final class ClusterUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterUtil.class);

    private static final String CONFIG_CLUSTER_BECOME_LEADER = "ditto.cluster.become-leader";
    private static final int SOCKET_CONNECT_TIMEOUT = 500;

    private ClusterUtil() {
        // no instantiation
        throw new AssertionError();
    }

    /**
     * Joins the cluster using the passed {@code system} and {@code config}.
     * <p>
     * Registers for Akka System termination ({@link ActorSystem#registerOnTermination(Runnable)}) to to a
     * {@code System.exit(-1)} or {@code System.exit(0)} depending on whether it was a graceful shutdown (e.g. triggered
     * by {@code docker stop}) or a non graceful one (e.g. triggered by failing health checks).
     * </p>
     *
     * @param system the initialized ActorSystem
     * @param config the config to use
     */
    public static void joinCluster(final ActorSystem system, final Config config) {
        final String clusterName = system.name();
        final AtomicBoolean gracefulShutdown = new AtomicBoolean(false);

        final String localAddress = ConfigUtil.getLocalHostAddress();
        LOGGER.info("Determined my own local address: '{}'", localAddress);

        if (ConfigUtil.shouldManuallyJoinClusterSeedNodes(config)) {
            LOGGER.info("Manually joining cluster seed-nodes..");

            boolean becomeLeader;
            try {
                becomeLeader = config.getBoolean(CONFIG_CLUSTER_BECOME_LEADER);
            } catch (final ConfigException.Missing e) {
                becomeLeader = false;
            }

            List<Address> clusterSeedNodes = ConfigUtil.getClusterSeedNodesExceptOwn(clusterName, config);
            if (becomeLeader) {
                LOGGER.info("Becoming leader, checking if there are other hosts in cluster '{}'", clusterName);
                clusterSeedNodes = clusterSeedNodes.stream().parallel().filter(address -> {
                    final String host = address.host().get();
                    final int port = Integer.parseInt(address.port().get().toString());

                    boolean hasOpenPort;
                    try {
                        final Socket socket = new Socket();
                        socket.connect(new InetSocketAddress(host, port), SOCKET_CONNECT_TIMEOUT);
                        hasOpenPort = true;
                        socket.close();
                    } catch (final IOException e) {
                        hasOpenPort = false;
                    }

                    return hasOpenPort;
                }).collect(Collectors.toList());
            }

            final Address myAddress = ConfigUtil.buildAddress(clusterName, localAddress);
            if (becomeLeader && clusterSeedNodes.isEmpty()) {
                clusterSeedNodes.add(myAddress);
                LOGGER.info("Creating new cluster with myself ({}) as leader: {}", localAddress, clusterSeedNodes);
            } else {
                LOGGER.info("Joining cluster seed nodes: {}", clusterSeedNodes);
            }
            final Cluster cluster = Cluster.get(system);
            cluster.joinSeedNodes(clusterSeedNodes);

            final CompletableFuture<Terminated> systemTermination = new CompletableFuture<>();
            // when current cluster member is removed (typically by 'cluster.leave(cluster.selfAddress())'),
            // shutdown the JVM:
            cluster.registerOnMemberRemoved(() -> {
                // shut down ActorSystem
                LOGGER.info("Shutting down the ActorSystem ..");
                system.terminate().onComplete(new OnComplete<Terminated>() {
                    @Override
                    public void onComplete(final Throwable failure, final Terminated success) throws Throwable {
                        if (success != null) {
                            systemTermination.complete(success);
                        } else {
                            systemTermination.completeExceptionally(failure);
                        }
                    }
                }, ExecutionContexts.fromExecutorService(Executors.newSingleThreadExecutor()));
            });

            Runtime.getRuntime().addShutdownHook(gracefullyLeaveClusterShutdownHook(myAddress, cluster,
                    gracefulShutdown, systemTermination));
        }

        system.registerOnTermination(() -> {
            if (!gracefulShutdown.get()) {
                LOGGER.warn("ActorSystem was shutdown NOT gracefully - exiting JVM with status code '-1'");
                System.exit(-1);
            } else {
                LOGGER.info("ActorSystem has shutdown gracefully");
            }
        });
    }

    private static Thread gracefullyLeaveClusterShutdownHook(final Address myAddress, final Cluster cluster,
            final AtomicBoolean gracefulShutdown, final CompletableFuture<Terminated> systemTermination) {
        return new Thread(() -> {
            gracefulShutdown.set(true);
            LOGGER.info("Shutdown issued from outside (e.g. SIGTERM) - gracefully shutting down..");
            LOGGER.info("Leaving the cluster - my address: {}", myAddress);
            cluster.leave(myAddress);
            // after leaving the cluster, don't just end this Thread as this would end the process - wait for the
            // system termination by waiting for the passed future:
            try {
                systemTermination.get(8, TimeUnit.SECONDS);
            } catch (final InterruptedException | ExecutionException | TimeoutException e) {
                LOGGER.error("System termination was interrupted: {}", e.getMessage(), e);
            }
        });
    }
}
