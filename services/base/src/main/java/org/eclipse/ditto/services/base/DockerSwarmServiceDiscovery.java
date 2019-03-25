/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.base;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import akka.actor.ExtendedActorSystem;
import akka.discovery.Lookup;
import akka.discovery.ServiceDiscovery;
import akka.dispatch.Futures;
import scala.Option;
import scala.collection.JavaConverters;
import scala.concurrent.Future;
import scala.concurrent.duration.FiniteDuration;

/**
 *
 */
public final class DockerSwarmServiceDiscovery extends ServiceDiscovery {

    private final ExtendedActorSystem system;

    /**
     *
     * @param system
     */
    public DockerSwarmServiceDiscovery(final ExtendedActorSystem system) {
        this.system = system;
    }

    @Override
    public Future<Resolved> lookup(final Lookup lookup, final FiniteDuration resolveTimeout) {
        final String serviceName = lookup.serviceName();

        final InetAddress[] allResolvedHosts;
        try {
            allResolvedHosts = InetAddress.getAllByName(serviceName);
        } catch (final UnknownHostException e) {
            throw new IllegalStateException("Could not resolve service name " + serviceName, e);
        }
        final List<ResolvedTarget> resolvedTargets = Arrays.stream(allResolvedHosts)
                .map(a -> new ResolvedTarget(a.getCanonicalHostName(), Option.empty(), Option.empty()))
                .collect(Collectors.toList());

        final Resolved resolved = new Resolved(serviceName, JavaConverters.asScalaBuffer(resolvedTargets).toList());
        system.log().warning("[DockerSwarmServiceDiscovery] Resolved via InetAddress: {}", resolved);
        return Futures.<Resolved>successful(resolved);
    }
}
