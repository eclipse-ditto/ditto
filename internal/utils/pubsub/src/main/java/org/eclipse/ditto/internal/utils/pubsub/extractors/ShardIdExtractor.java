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
package org.eclipse.ditto.internal.utils.pubsub.extractors;

import java.util.Collection;
import java.util.Collections;

import org.eclipse.ditto.internal.utils.cluster.ShardRegionExtractor;

/**
 * Extract read-subjects of events as topics.
 *
 * @param <T> type of messages.
 */
public final class ShardIdExtractor<T> implements PubSubTopicExtractor<T> {

    private final ShardRegionExtractor shardRegionExtractor;

    private ShardIdExtractor(final ShardRegionExtractor shardRegionExtractor) {
        this.shardRegionExtractor = shardRegionExtractor;
    }

    /**
     * Create an extractor of shard-ids as topics.
     *
     * @param shardRegionExtractor the ShardRegionExtractor to use in order to determine shard-ids from messages.
     * @param <T> type of messages.
     * @return a shard-id extractor.
     */
    public static <T> ShardIdExtractor<T> of(final ShardRegionExtractor shardRegionExtractor) {
        return new ShardIdExtractor<>(shardRegionExtractor);
    }

    @Override
    public Collection<String> getTopics(final T message) {
        return Collections.singletonList(shardRegionExtractor.shardId(message));
    }
}
