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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import akka.actor.ExtendedActorSystem;
import akka.discovery.Lookup;
import akka.discovery.ServiceDiscovery;
import akka.dispatch.Futures;
import akka.pattern.Patterns;
import scala.Option;
import scala.collection.JavaConverters;
import scala.concurrent.Future;
import scala.concurrent.duration.FiniteDuration;

/**
 * ServiceDiscovery usable for a Docker swarm based cluster.
 * <p>
 * One speciality of a Docker swarm based cluster is that the Docker swarm DNS sets a TTL of DNS entries to 600 seconds
 * (10 minutes) - so if a cluster forms and not all DNS entries are "there" from the beginning, it takes 10 minutes
 * until DNS caches used by the default Akka {@link akka.discovery.dns.DnsServiceDiscovery DnsServiceDiscovery} are
 * evicted and another DNS lookup is done.
 * </p>
 * This implementation does not cache DNS entries and can therefore be used for bootstrapping in Docker swarm.
 */
public final class DockerSwarmServiceDiscovery extends ServiceDiscovery {

    private final ExtendedActorSystem system;

    /**
     * Constructs a new instance of DockerSwarmServiceDiscovery.
     *
     * @param system the ActorSystem.
     */
    public DockerSwarmServiceDiscovery(final ExtendedActorSystem system) {
        this.system = system;
    }

    @Override
    public Future<Resolved> lookup(final Lookup lookup, final FiniteDuration resolveTimeout) {

        return Futures.<Resolved>firstCompletedOf(Arrays.asList(
                resolveService(lookup),
                scheduleTimeout(lookup, resolveTimeout)
        ), system.dispatcher());
    }

    private Future<Resolved> resolveService(final Lookup lookup) {

        final String serviceName = lookup.serviceName();
        return Futures.<Resolved>future(() -> {
            final InetAddress[] allResolvedHosts;
            try {
                allResolvedHosts = InetAddress.getAllByName(serviceName);
            } catch (final UnknownHostException e) {
                return new Resolved(serviceName,
                        JavaConverters.<ResolvedTarget>asScalaBuffer(Collections.emptyList()).toList());
            }
            final List<ResolvedTarget> resolvedTargets = Arrays.stream(allResolvedHosts)
                    .map(a -> new ResolvedTarget(a.getCanonicalHostName(), Option.empty(), Option.apply(a)))
                    .collect(Collectors.toList());

            final Resolved resolved = new Resolved(serviceName, JavaConverters.asScalaBuffer(resolvedTargets).toList());
            system.log().debug("[DockerSwarmServiceDiscovery] Resolved <{}> via InetAddress to: {}", lookup,
                            resolved);
            return resolved;
        }, system.dispatcher());
    }

    private Future<Resolved> scheduleTimeout(final Lookup lookup, final FiniteDuration resolveTimeout) {

        return Patterns.<Resolved>after(resolveTimeout, system.scheduler(), system.dispatcher(),
                Futures.<Resolved>failed(
                        new TimeoutException("Lookup <" + lookup + "> timed out after " + resolveTimeout)));
    }
}
