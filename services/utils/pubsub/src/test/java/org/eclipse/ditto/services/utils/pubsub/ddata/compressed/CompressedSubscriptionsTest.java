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
package org.eclipse.ditto.services.utils.pubsub.ddata.compressed;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.IntStream;

import org.eclipse.ditto.services.utils.pubsub.ddata.AbstractSubscriptionsTest;
import org.junit.Test;

import akka.util.ByteString;

/**
 * Tests {@link org.eclipse.ditto.services.utils.pubsub.ddata.compressed.CompressedSubscriptions}.
 */
public final class CompressedSubscriptionsTest
        extends AbstractSubscriptionsTest<ByteString, CompressedUpdate, CompressedSubscriptions> {

    @Override
    protected CompressedSubscriptions newSubscriptions() {
        return CompressedSubscriptions.of(Arrays.asList(1, 2, 3));
    }

    @Test
    public void exportAllSubscriptions() {
        final CompressedSubscriptions underTest = getVennDiagram();
        final CompressedUpdate update = underTest.export(true);
        assertThat(update.shouldReplaceAll()).isTrue();
        assertThat(update.getDeletes()).isEmpty();
        assertThat(update.getInserts()).containsExactlyInAnyOrder(
                IntStream.rangeClosed(1, 7)
                        .mapToObj(i -> underTest.hashTopic(String.valueOf(i)))
                        .toArray(ByteString[]::new)
        );
    }

    @Test
    public void exportIncrementalUpdates() {
        final CompressedSubscriptions underTest = getVennDiagram();

        // update 1: construction of Venn diagram.
        final CompressedUpdate update1 = underTest.export(false);
        assertThat(update1.shouldReplaceAll()).isFalse();
        assertThat(update1.getInserts()).containsExactlyInAnyOrder(
                IntStream.rangeClosed(1, 7)
                        .mapToObj(i -> underTest.hashTopic(String.valueOf(i)))
                        .toArray(ByteString[]::new)
        );
        assertThat(update1.getDeletes()).isEmpty();

        // update 2: unsubscription of topic.
        underTest.unsubscribe(ACTOR1, singleton("1"));
        final CompressedUpdate update2 = underTest.export(false);
        assertThat(update2.shouldReplaceAll()).isFalse();
        assertThat(update2.getInserts()).isEmpty();
        assertThat(update2.getDeletes()).containsExactlyInAnyOrder(underTest.hashTopic("1"));

        // update 3: removal of subscriber.
        underTest.removeSubscriber(ACTOR3);
        final CompressedUpdate update3 = underTest.export(false);
        assertThat(update3.shouldReplaceAll()).isFalse();
        assertThat(update3.getInserts()).isEmpty();
        assertThat(update3.getDeletes()).containsExactlyInAnyOrder(underTest.hashTopic("7"));

        // update 4: changes that do not affect the ddata
        underTest.subscribe(ACTOR2, singleton("4"));
        underTest.removeSubscriber(ACTOR1);
        final CompressedUpdate update4 = underTest.export(false);
        assertThat(update4.shouldReplaceAll()).isFalse();
        assertThat(update4.getInserts()).isEmpty();
        assertThat(update4.getDeletes()).isEmpty();
    }
}
