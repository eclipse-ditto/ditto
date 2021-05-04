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
import java.util.HashSet;

/**
 * Functional interface for extractors of topics from messages.
 *
 * @param <T> type of topics.
 */
@FunctionalInterface
public interface PubSubTopicExtractor<T> {

    /**
     * Extract topics from a typed message.
     *
     * @param message the message.
     * @return the collection of topics the message was published to.
     */
    Collection<String> getTopics(T message);

    /**
     * Combine 2 topic extractors.
     *
     * @param that the other topic extractor.
     * @return a topic extractor that delivers topics extracted by both this and that.
     */
    default PubSubTopicExtractor<T> with(final PubSubTopicExtractor<T> that) {
        return with(Collections.singletonList(that));
    }

    /**
     * Combine many topic extractors.
     *
     * @param those other topic extractors.
     * @return a topic extractor that delivers topics extracted by both this and that.
     */
    default PubSubTopicExtractor<T> with(final Collection<PubSubTopicExtractor<T>> those) {
        return message -> {
            final Collection<String> topicsFromThis = this.getTopics(message);
            final Collection<String> result = new HashSet<>(topicsFromThis);
            for (final PubSubTopicExtractor<T> that : those) {
                result.addAll(that.getTopics(message));
            }
            return result;
        };
    }
}
