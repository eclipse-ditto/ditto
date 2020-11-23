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
package org.eclipse.ditto.services.utils.pubsub.api;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.ditto.services.utils.pubsub.ddata.ack.Grouped;

import akka.actor.Address;
import akka.japi.Pair;

/**
 * Notification that the distributed data of acknowledgement label declaration changed.
 */
public final class RemoteAcksChanged {

    private final Map<Address, List<Grouped<String>>> mmap;

    private RemoteAcksChanged(final Map<Address, List<Grouped<String>>> mmap) {
        this.mmap = mmap;
    }

    /**
     * Create a change notification from a deserialized multimap of remote acknowledgement label declarations.
     *
     * @param mmap the multimap.
     * @return the change notification.
     */
    public static RemoteAcksChanged of(final Map<Address, List<Grouped<String>>> mmap) {
        return new RemoteAcksChanged(mmap);
    }

    // TODO: group acks in order to send weak acks according to group
    // TODO: send weak acks only for the chosen groups on the subscriber side.
    public Map<Address, Set<String>> getMultiMap() {
        return mmap.entrySet()
                .stream()
                .map(entry -> Pair.create(entry.getKey(),
                        entry.getValue()
                                .stream()
                                .flatMap(Grouped<String>::streamValues)
                                .collect(Collectors.toSet())
                ))
                .collect(Collectors.toMap(Pair::first, Pair::second));
    }

}
