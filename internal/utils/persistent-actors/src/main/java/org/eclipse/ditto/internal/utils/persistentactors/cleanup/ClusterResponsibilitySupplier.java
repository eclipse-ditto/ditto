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
package org.eclipse.ditto.internal.utils.persistentactors.cleanup;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import akka.cluster.Cluster;
import akka.cluster.Member;
import akka.cluster.UniqueAddress;
import akka.japi.Pair;

/**
 * Responsibility supplier according to cluster role.
 */
public final class ClusterResponsibilitySupplier implements Supplier<Pair<Integer, Integer>> {

    private final Cluster cluster;
    private final String myRole;

    private ClusterResponsibilitySupplier(final Cluster cluster, final String myRole) {
        this.cluster = cluster;
        this.myRole = myRole;
        if (!cluster.getSelfRoles().contains(myRole)) {
            throw new IllegalArgumentException("This node does not have the requested role '" + myRole + "'");
        }
    }

    /**
     * Create a cluster responsibility supplier. Persistence IDs are divided according to hash code for each member
     * with the given role. The remainder assigned to this node is equal to this node's position in the list of members
     * with the given role sorted by address.
     *
     * @param cluster Cluster object to read the current cluster state.
     * @param myRole The role of this node according to which responsibility is divided.
     * @return The responsibility supplier for this cluster member.
     */
    public static ClusterResponsibilitySupplier of(final Cluster cluster, final String myRole) {
        return new ClusterResponsibilitySupplier(cluster, myRole);
    }

    /**
     * Returns a pair where the first integer is the index of the cluster node itself and the second is the number of
     * all cluster nodes with the same role.
     *
     * @return the pair of index and count.
     */
    @Override
    public Pair<Integer, Integer> get() {
        final List<UniqueAddress> membersOfMyRole =
                StreamSupport.stream(cluster.state().getMembers().spliterator(), false)
                        .filter(member -> member.getRoles().contains(myRole))
                        .map(Member::uniqueAddress)
                        .sorted()
                        .toList();
        final int myIndex = membersOfMyRole.indexOf(cluster.selfUniqueAddress());
        return Pair.create(myIndex, membersOfMyRole.size());
    }
}
