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

import org.eclipse.ditto.internal.utils.pubsub.ddata.ack.GroupedSnapshot;

import akka.actor.ActorRef;

/**
 * Local message sent to whomever is interested in changes to local acknowledgement label declarations.
 */
public final class LocalAcksChanged {

    private final GroupedSnapshot<ActorRef, String> snapshot;

    private LocalAcksChanged(final GroupedSnapshot<ActorRef, String> snapshot) {
        this.snapshot = snapshot;
    }

    /**
     * Create local change notification from a snapshot of a grouped relation of acknowledgement label declarations.
     *
     * @param snapshot the snapshot.
     * @return the change notification.
     */
    public static LocalAcksChanged of(final GroupedSnapshot<ActorRef, String> snapshot) {
        return new LocalAcksChanged(snapshot);
    }

    /**
     * The snapshot of local acknowledgement label declarations.
     *
     * @return the snapshot.
     */
    public GroupedSnapshot<ActorRef, String> getSnapshot() {
        return snapshot;
    }
}
