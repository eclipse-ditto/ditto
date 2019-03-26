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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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

    private static final String MY_HOSTNAME = System.getenv("HOSTNAME");

    /**
     *
     */
    public DockerSwarmServiceDiscovery(final ExtendedActorSystem system) {
        this.system = system;
    }

    @Override
    public Future<Resolved> lookup(final Lookup lookup, final FiniteDuration resolveTimeout) {
        final String serviceName = lookup.serviceName();

        final Optional<String> portName = lookup.getPortName();
        final Optional<String> protocol = lookup.getProtocol();
        if (portName.isPresent() || protocol.isPresent()) {
            throw new IllegalStateException("DockerSwarmServiceDiscovery cannot lookup by port <" +
                    portName.orElse("") + "> or protocol <" + protocol.orElse("") + ">");
        }

        return Futures.<Resolved>future(() -> {
            final InetAddress[] allResolvedHosts;
            try {
                allResolvedHosts = InetAddress.getAllByName(serviceName);
            } catch (final UnknownHostException e) {
                return new Resolved(serviceName,
                        JavaConverters.<ResolvedTarget>asScalaBuffer(Collections.emptyList()).toList());
            }
            final List<ResolvedTarget> resolvedTargets = Arrays.stream(allResolvedHosts)
                    .filter(a -> !a.getCanonicalHostName().equals(MY_HOSTNAME))
                    .filter(a -> !a.getHostName().equals(MY_HOSTNAME))
                    .map(a -> new ResolvedTarget(a.getCanonicalHostName(), Option.empty(), Option.empty()))
                    .collect(Collectors.toList());

            system.log().warning("[DockerSwarmServiceDiscovery] Resolved via InetAddress: {}", resolvedTargets);

            return new Resolved(serviceName, JavaConverters.asScalaBuffer(resolvedTargets).toList());
        }, system.dispatcher());
    }
}
