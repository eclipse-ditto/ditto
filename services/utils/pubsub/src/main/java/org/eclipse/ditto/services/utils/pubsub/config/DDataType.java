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
package org.eclipse.ditto.services.utils.pubsub.config;

/**
 * Type of distributed data to store the relation between nodes and their subscribed topics.
 */
public enum DDataType {

    /**
     * {@code ORMultiMap<ActorRef, ByteString>} where each value is an array of hash codes of 1 topic.
     */
    COMPRESSED,

    /**
     * {@code LWWMap<ActorRef, ByteString>} where each value is the Bloom filter of all subscribed topics on 1 node.
     */
    BLOOM_FILTER
}
