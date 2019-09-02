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

import org.eclipse.ditto.model.base.headers.WithDittoHeaders;

/**
 * Extract read-subjects of messages as topics.
 *
 * @param <T> type of messages.
 */
public final class ReadSubjectExtractor<T extends WithDittoHeaders> implements PubSubTopicExtractor<T> {

    private ReadSubjectExtractor() {}

    /**
     * Create an extractor of read-subjects as topics.
     *
     * @param <T> type of messages.
     * @return a read-subject extractor.
     */
    public static <T extends WithDittoHeaders> ReadSubjectExtractor<T> of() {
        return new ReadSubjectExtractor<>();
    }

    @Override
    public Collection<String> getTopics(final T event) {
        return event.getDittoHeaders().getReadSubjects();
    }
}
