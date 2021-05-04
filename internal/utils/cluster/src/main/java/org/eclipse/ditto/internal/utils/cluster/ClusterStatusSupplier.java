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
package org.eclipse.ditto.internal.utils.cluster;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.eclipse.ditto.internal.utils.cluster.config.DefaultClusterConfig;
import org.eclipse.ditto.internal.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.health.cluster.ClusterRoleStatus;
import org.eclipse.ditto.internal.utils.health.cluster.ClusterStatus;

import akka.actor.Address;
import akka.cluster.Cluster;
import akka.cluster.Member;

/**
 * Util class that helps to retrieve the cluster status.
 */
public final class ClusterStatusSupplier implements Supplier<ClusterStatus> {

    private final Cluster cluster;
    private final ClusterConfig clusterConfig;

    /**
     * Creates a new {@code ClusterStatusSupplier} instance with the given {@code cluster} to get the state from.
     *
     * @param cluster the given cluster to extract the information from.
     */
    public ClusterStatusSupplier(final Cluster cluster) {
        this.cluster = cluster;
        clusterConfig = DefaultClusterConfig.of(DefaultScopedConfig.dittoScoped(cluster.system().settings().config()));
    }

    @Override
    public ClusterStatus get() {
        final Function<Member, String> mapMemberToString = member -> member.address().toString();

        final Set<String> allRoles = cluster.state().getAllRoles()
                .stream()
                .filter(role -> !clusterConfig.getClusterStatusRolesBlocklist().contains(role))
                .collect(Collectors.toSet());
        final Set<Member> unreachable = cluster.state().getUnreachable();
        final Set<Member> all =
                StreamSupport.stream(cluster.state().getMembers().spliterator(), false).collect(Collectors.toSet());
        final Set<Member> reachable = all.stream().filter(m -> !unreachable.contains(m)).collect(Collectors.toSet());

        final Set<ClusterRoleStatus> roles = new HashSet<>(allRoles.size());
        allRoles.forEach(role -> {
            final Predicate<Member> filterRole = member -> member.getRoles().contains(role);

            // only add role if member has reachable or unreachable entries
            if (all.stream().anyMatch(filterRole)) {
                roles.add(ClusterRoleStatus.of(
                        role,
                        reachable.stream()
                                .filter(filterRole)
                                .map(mapMemberToString)
                                .collect(Collectors.toSet()),
                        unreachable.stream()
                                .filter(filterRole)
                                .map(mapMemberToString)
                                .collect(Collectors.toSet()),
                        Optional.ofNullable(cluster.state().getRoleLeader(role))
                                .map(Address::toString)
                                .orElse(null)
                ));
            }
        });

        return ClusterStatus.of(
                reachable.stream().map(mapMemberToString).collect(Collectors.toSet()),
                unreachable.stream().map(mapMemberToString).collect(Collectors.toSet()),
                cluster.state().getSeenBy().stream().map(Address::toString).collect(Collectors.toSet()),
                Optional.ofNullable(cluster.state().getLeader()).map(Address::toString).orElse(null),
                cluster.getSelfRoles(),
                roles
        );
    }

}
