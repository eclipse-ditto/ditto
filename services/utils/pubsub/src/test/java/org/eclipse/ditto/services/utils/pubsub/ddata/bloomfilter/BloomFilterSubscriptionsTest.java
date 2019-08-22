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
package org.eclipse.ditto.services.utils.pubsub.ddata.bloomfilter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.services.utils.pubsub.ddata.AbstractSubscriptionsTest;
import org.junit.Test;

import akka.util.ByteString;

/**
 * Tests {@link org.eclipse.ditto.services.utils.pubsub.ddata.bloomfilter.BloomFilterSubscriptions}.
 */
public final class BloomFilterSubscriptionsTest
        extends AbstractSubscriptionsTest<List<Integer>, ByteString, BloomFilterSubscriptions> {

    @Override
    protected BloomFilterSubscriptions newSubscriptions() {
        return BloomFilterSubscriptions.of("hello world", 10);
    }

    @Test
    public void hashesHaveExpectedSize() {
        final int hashFamilySize = 8;
        final BloomFilterSubscriptions underTest = BloomFilterSubscriptions.of("hello world", hashFamilySize);
        final List<Integer> hashes = underTest.getHashes("goodbye cruel world");
        assertThat(underTest.getSeeds().size()).isEqualTo(hashFamilySize);
        assertThat(hashes.size()).isEqualTo(hashFamilySize);
    }

    @Test
    public void testBloomFilter() {
        final BloomFilterSubscriptions underTest = (BloomFilterSubscriptions) getVennDiagram();
        final ByteString bloomFilter = underTest.export(true);
        IntStream.rangeClosed(1, 7)
                .mapToObj(String::valueOf)
                .forEach(topic ->
                        Assertions.assertThat(
                                ByteStringAsBitSet.contains(bloomFilter, underTest.getHashes(topic).stream()))
                                .describedAs("Bloom filter should not reject topic \"%s\"", topic)
                                .isTrue());

        // There is no false-positive among topics "8", "9", ..., "100" unless Scala changes its murmur-hash
        // implementation. The first false-positive is "402".
        IntStream.rangeClosed(8, 100)
                .mapToObj(String::valueOf)
                .forEach(topic ->
                        assertThat(ByteStringAsBitSet.contains(bloomFilter, underTest.getHashes(topic).stream()))
                                .describedAs("Bloom filter should not accept topic \"%s\"", topic)
                                .isFalse());
    }

    @Test
    public void testBloomFilterConsistency() {
        // WHEN: 2 Bloom filters are made from local subscriptions of identical content
        final ByteString filter1 = getVennDiagram().export(true);
        final ByteString filter2 = getVennDiagram().export(true);

        // THEN: The Bloom filters should be bit-wise identical.
        // This ensures that Bloom filters remain meaningful on other cluster members.
        assertThat(filter1).isEqualTo(filter2);
    }
}
