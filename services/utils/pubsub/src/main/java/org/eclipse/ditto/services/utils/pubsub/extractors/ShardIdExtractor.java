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
package org.eclipse.ditto.services.utils.pubsub.extractors;

import java.util.Collection;
import java.util.Collections;

import org.eclipse.ditto.services.utils.cluster.ShardRegionExtractor;

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

    public static <T> ShardIdExtractor<T> of(final ShardRegionExtractor shardRegionExtractor) {
        return new ShardIdExtractor<>(shardRegionExtractor);
    }

    @Override
    public Collection<String> getTopics(final T message) {
        return Collections.singletonList(shardRegionExtractor.shardId(message));
    }
}
