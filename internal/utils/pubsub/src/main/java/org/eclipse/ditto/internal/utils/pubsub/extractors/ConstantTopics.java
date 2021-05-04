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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Extract the same topics for all messages of the type.
 *
 * @param <T> type of messages.
 */
public final class ConstantTopics<T> implements PubSubTopicExtractor<T> {

    private final List<String> topics;

    private ConstantTopics(final List<String> topics) {
        this.topics = topics;
    }

    /**
     * Create an extractor of the same topics for all messages.
     *
     * @param <T> type of messages.
     * @param topics the constant topics.
     * @return a read-subject extractor.
     */
    public static <T> ConstantTopics<T> of(final String... topics) {
        return new ConstantTopics<>(Collections.unmodifiableList(Arrays.asList(topics)));
    }

    @Override
    public Collection<String> getTopics(final T event) {
        return topics;
    }
}
