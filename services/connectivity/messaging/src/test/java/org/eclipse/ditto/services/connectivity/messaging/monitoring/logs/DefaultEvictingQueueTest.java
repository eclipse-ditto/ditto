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

package org.eclipse.ditto.services.connectivity.messaging.monitoring.logs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultEvictingQueue}.
 */
public final class DefaultEvictingQueueTest {

    private static final int CAPACITY = 6;

    @Test
    public void verifyEviction() {
        final EvictingQueue<String> queue = DefaultEvictingQueue.withCapacity(CAPACITY);

        final List<String> fallingOutStrings = createRandomStrings(13);
        final List<String> remainingStrings = createRandomStrings(CAPACITY);

        queue.addAll(fallingOutStrings);
        queue.addAll(remainingStrings);

        assertThat(queue)
                .containsOnlyElementsOf(remainingStrings)
                .hasSize(remainingStrings.size());
    }

    private List<String> createRandomStrings(final int n) {
        return Stream.iterate(0, UnaryOperator.identity())
                .limit(n)
                .map(unused -> UUID.randomUUID().toString())
                .collect(Collectors.toList());
    }

    @Test
    public void testEqualsAndHashcode() {
        EqualsVerifier.forClass(
                DefaultEvictingQueue.class)
                .verify();
    }

}
