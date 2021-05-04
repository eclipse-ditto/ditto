/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pubsub.api;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.internal.utils.pubsub.ddata.ack.Grouped;

import akka.actor.Address;

/**
 * Notification that the distributed data of acknowledgement label declaration changed.
 */
public final class RemoteAcksChanged {

    private final Map<Address, Map<String, Set<String>>> groupedAckLabelMap;
    private final Set<String> allDeclaredAcks;

    private RemoteAcksChanged(final Map<Address, Map<String, Set<String>>> groupedAckLabelMap,
            final Set<String> allDeclaredAcks) {
        this.groupedAckLabelMap = groupedAckLabelMap;
        this.allDeclaredAcks = allDeclaredAcks;
    }

    /**
     * Create a change notification from a deserialized multimap of remote acknowledgement label declarations.
     *
     * @param mmap the multimap.
     * @return the change notification.
     */
    public static RemoteAcksChanged of(final Map<Address, List<Grouped<String>>> mmap) {
        return new RemoteAcksChanged(createGroupedAckLabelMap(mmap), createAllDeclaredAcks(mmap));
    }

    /**
     * Stream acknowledgement labels declared on an address for the given groups.
     *
     * @param address the address.
     * @param groups the groups for which the address is chosen.
     * @return the set of acknowledgement labels that will be checked at the address.
     */
    public Stream<String> streamDeclaredAcksForGroup(final Address address, final Collection<String> groups) {
        final Map<String, Set<String>> addressGroups = groupedAckLabelMap.getOrDefault(address, Map.of());
        return Stream.concat(Stream.of(""), groups.stream())
                .flatMap(group -> addressGroups.getOrDefault(group, Set.of()).stream());
    }

    /**
     * Test if an acknowledgement label is declared anywhere.
     *
     * @param ackLabel the acknowledgement label.
     * @return whether it is declared anywhere.
     */
    public boolean contains(final String ackLabel) {
        return allDeclaredAcks.contains(ackLabel);
    }

    private static Map<Address, Map<String, Set<String>>> createGroupedAckLabelMap(
            final Map<Address, List<Grouped<String>>> mmap) {

        final Map<Address, Map<String, Set<String>>> result = new HashMap<>();
        mmap.forEach((address, list) -> {
            final Map<String, Set<String>> groups = new HashMap<>();
            list.forEach(group ->
                    groups.compute(group.getGroup().orElse(""), (k, set) -> {
                        final Set<String> nonNullSet = set == null ? new HashSet<>() : set;
                        nonNullSet.addAll(group.getValues());
                        return nonNullSet;
                    })
            );
            result.put(address, groups);
        });
        return result;
    }

    private static Set<String> createAllDeclaredAcks(final Map<Address, List<Grouped<String>>> mmap) {
        return mmap.values()
                .stream()
                .flatMap(List::stream)
                .map(Grouped::getValues)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }
}
