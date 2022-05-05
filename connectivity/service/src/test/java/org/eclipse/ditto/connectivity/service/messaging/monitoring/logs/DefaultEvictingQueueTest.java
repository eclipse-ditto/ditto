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

package org.eclipse.ditto.connectivity.service.messaging.monitoring.logs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
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
                .hasSameElementsAs(remainingStrings)
                .hasSize(remainingStrings.size());
        assertThat(queue.stream().count())
                .isEqualTo(remainingStrings.size());
    }

    @Test
    public void verifyEvictionUnderHighLoad() throws InterruptedException {
        final EvictingQueue<String> queue = DefaultEvictingQueue.withCapacity(CAPACITY);

        final CountDownLatch latch = new CountDownLatch(100000);

        IntStream.range(0, 100000).parallel().forEach(i -> {
            queue.add(Integer.toString(i));
            latch.countDown();
        });

        latch.await();

        assertThat(queue.size()).isEqualTo(CAPACITY);
        assertThat(queue.stream().count()).isEqualTo(CAPACITY);
    }

    private List<String> createRandomStrings(final int n) {
        return Stream.iterate(0, UnaryOperator.identity())
                .limit(n)
                .map(unused -> UUID.randomUUID().toString())
                .toList();
    }

    @Test
    public void testEqualsAndHashcode() {
        EqualsVerifier.forClass(
                DefaultEvictingQueue.class)
                .verify();
    }

}
