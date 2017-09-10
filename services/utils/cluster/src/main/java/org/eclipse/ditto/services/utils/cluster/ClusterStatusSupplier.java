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

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.eclipse.ditto.services.utils.health.cluster.ClusterRoleStatus;
import org.eclipse.ditto.services.utils.health.cluster.ClusterStatus;

import akka.actor.Address;
import akka.cluster.Cluster;
import akka.cluster.Member;

/**
 * Util class that helps to retrieve the cluster status.
 */
public final class ClusterStatusSupplier implements Supplier<ClusterStatus> {

    private final Cluster cluster;

    /**
     * Creates a new {@code ClusterStatusSupplier} instance with the given {@code cluster} to get the state from.
     *
     * @param cluster the given cluster to extract the information from.
     */
    public ClusterStatusSupplier(final Cluster cluster) {
        this.cluster = cluster;
    }

    @Override
    public ClusterStatus get() {
        final Function<Member, String> mapMemberToString = member -> member.address().toString();

        final Set<Member> unreachable = cluster.state().getUnreachable(); //
        final Set<Member> all =
                StreamSupport.stream(cluster.state().getMembers().spliterator(), false).collect(Collectors.toSet());
        final Set<Member> reachable = all.stream().filter(m -> !unreachable.contains(m)).collect(Collectors.toSet());

        final Set<ClusterRoleStatus> roles = new HashSet<>(cluster.state().getAllRoles().size());
        cluster.state().getAllRoles().forEach(role -> { //
            final Predicate<Member> filterRole = member -> member.getRoles().contains(role);
            roles.add(ClusterRoleStatus.of( //
                    role, //
                    reachable.stream().filter(filterRole).map(mapMemberToString).collect(Collectors.toSet()), //
                    unreachable.stream().filter(filterRole).map(mapMemberToString).collect(Collectors.toSet()), //
                    Optional.ofNullable(cluster.state().getRoleLeader(role)).map(Address::toString).orElse(null) //
            ));
        });

        return ClusterStatus.of( //
                reachable.stream().map(mapMemberToString).collect(Collectors.toSet()), //
                unreachable.stream().map(mapMemberToString).collect(Collectors.toSet()), //
                cluster.state().getSeenBy().stream().map(Address::toString).collect(Collectors.toSet()), //
                Optional.ofNullable(cluster.state().getLeader()).map(Address::toString).orElse(null), //
                cluster.getSelfRoles(), //
                roles //
        );
    }

}
