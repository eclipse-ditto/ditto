/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pubsub;

import org.eclipse.ditto.base.model.signals.Signal;

/**
 * Interface for pub-sub factory.
 *
 * @param <T> type of messages.
 */
public interface PubSubFactory<T extends Signal<?>> {

    /**
     * Start a pub-supervisor under the user guardian. Will fail when called a second time in an actor system.
     *
     * @return access to distributed publication.
     */
    DistributedPub<T> startDistributedPub();

    /**
     * Start a sub-supervisor under the user guardian. Will fail when called a second time in an actor system.
     *
     * @return access to distributed subscription.
     */
    DistributedSub startDistributedSub();

    /**
     * Retrieve the DistributedAcks with which the DistributedPub and DistributedSub were constructed.
     *
     * @return the given DistributedAcks.
     */
    DistributedAcks getDistributedAcks();

    /**
     * Hash a key for pubsub.
     * A subscriber of each group is chosen according to this hash.
     * The result is guaranteed to be a non-negative.
     *
     * @param hashKey the hash key.
     * @return the hash code for pubsub.
     */
    static int hashForPubSub(final CharSequence hashKey) {
        // Using the string hashcode to ensure that the final byte affects the hash code additively.
        final int stringHashCode = hashKey.toString().hashCode();
        if (stringHashCode == Integer.MIN_VALUE) {
            return 0;
        } else {
            return Math.abs(stringHashCode);
        }
    }
}
