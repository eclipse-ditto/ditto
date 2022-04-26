/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * Unit test for {@link ListZipperTest}.
 */
public final class ListZipperTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ListZipper.class, areImmutable());
    }

    @Test
    public void zipListsWithNullListAThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> ListZipper.zipLists(null, Collections.emptyList()))
                .withMessage("The listA must not be null!")
                .withNoCause();
    }

    @Test
    public void zipListsWithNullListBThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> ListZipper.zipLists(Collections.emptyList(), null))
                .withMessage("The listB must not be null!")
                .withNoCause();
    }

    @Test
    public void zipListsReturnsStreamWithSameLengthAsListAIfListBIsLonger() {
        final var listA = List.of("a", "b", "c");
        final var listB = List.of(0, 1, 2, 3, 4);

        final var zipStream = ListZipper.zipLists(listA, listB);

        assertThat(zipStream).containsExactly(new Zipped<>("a", 0), new Zipped<>("b", 1), new Zipped<>("c", 2));
    }

    @Test
    public void zipListsReturnsStreamWithSameLengthAsListBIfListAIsLonger() {
        final var listA = List.of(0, 1, 2, 3, 4);
        final var listB = List.of("a", "b", "c");

        final var zipStream = ListZipper.zipLists(listA, listB);

        assertThat(zipStream).containsExactly(new Zipped<>(0, "a"), new Zipped<>(1, "b"), new Zipped<>(2, "c"));
    }

    @Test
    public void zipListReturnsEmptyStreamIfListAIsEmpty() {
        final var zipStream = ListZipper.zipLists(Collections.emptyList(), List.of("a", "b", "c"));

        assertThat(zipStream).isEmpty();
    }

    @Test
    public void zipListReturnsEmptyStreamIfListBIsEmpty() {
        final var zipStream = ListZipper.zipLists(List.of("a", "b", "c"), Collections.emptyList());

        assertThat(zipStream).isEmpty();
    }

}