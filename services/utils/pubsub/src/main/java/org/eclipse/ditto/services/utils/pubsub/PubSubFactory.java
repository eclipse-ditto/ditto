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
package org.eclipse.ditto.services.utils.pubsub;

/**
 * Interface for pub-sub factory.
 *
 * @param <T> type of messages.
 */
public interface PubSubFactory<T> {

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
}
