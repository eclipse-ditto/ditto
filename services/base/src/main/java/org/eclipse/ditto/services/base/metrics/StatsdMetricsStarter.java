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
package org.eclipse.ditto.services.base.metrics;

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.net.InetSocketAddress;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.services.base.config.ServiceConfigReader;

import akka.actor.ActorSystem;

/**
 * This Runnable activates or deactivates the {@link StatsdMetricsReporter}, depending on whether the
 * appropriate configuration settings for StatsD are provided.
 */
@NotThreadSafe
public final class StatsdMetricsStarter implements Runnable {

    @Nullable
    private final InetSocketAddress socketAddress;
    private final String serviceName;

    private StatsdMetricsStarter(@Nullable final InetSocketAddress socketAddress, final String serviceName) {
        this.socketAddress = socketAddress;
        this.serviceName = requireNonNull(serviceName);
    }

    /**
     * Returns a new instance of {@link StatsdMetricsStarter}.
     *
     * @param configReader the configuration settings of the service which sends StatsD metrics.
     * @param actorSystem the Akka actor system to be used for creating the metric registry.
     * @param serviceName the name of the service which sends StatsD metrics.
     * @return the new instance.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code serviceName} is empty.
     */
    public static StatsdMetricsStarter newInstance(final ServiceConfigReader configReader,
            final ActorSystem actorSystem,
            final String serviceName) {

        checkNotNull(configReader, "config");
        checkNotNull(actorSystem, "Akka actor system");
        argumentNotEmpty(serviceName, "service name");

        final InetSocketAddress socketAddress = configReader.statsd().address().orElse(null);

        return new StatsdMetricsStarter(socketAddress, serviceName);
    }

    @Override
    public void run() {
        if (socketAddress == null) {
            StatsdMetricsReporter.getInstance().deactivate();
        } else {
            StatsdMetricsReporter.getInstance().activate(socketAddress, serviceName);
        }
    }

}
