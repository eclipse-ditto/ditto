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
package org.eclipse.ditto.internal.utils.metrics.instruments.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.List;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link KamonTagSetConverter}.
 */
public final class KamonTagSetConverterTest {

    private static TagSet dittoTagSet;
    private static kamon.tag.TagSet kamonTagSet;

    @BeforeClass
    public static void beforeClass() {
        final var genericTag1 = Map.entry("foo", "bar");
        final var genericTag2 = Map.entry("bar", "baz");
        final var genericTag3 = Map.entry("voku", "hila");

        dittoTagSet = TagSet.ofTagCollection(List.of(
                Tag.of(genericTag1.getKey(), genericTag1.getValue()),
                Tag.of(genericTag2.getKey(), genericTag2.getValue()),
                Tag.of(genericTag3.getKey(), genericTag3.getValue())
        ));

        kamonTagSet = kamon.tag.TagSet.builder()
                .add(genericTag1.getKey(), genericTag1.getValue())
                .add(genericTag2.getKey(), genericTag2.getValue())
                .add(genericTag3.getKey(), genericTag3.getValue())
                .build();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(KamonTagSetConverter.class, areImmutable());
    }

    @Test
    public void getKamonTagSetWithNullThrowsNullPointerException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> KamonTagSetConverter.getKamonTagSet(null))
                .withMessage("The dittoTagSet must not be null!")
                .withNoCause();
    }

    @Test
    public void getKamonTagSetReturnsExpected() {
        assertThat(KamonTagSetConverter.getKamonTagSet(dittoTagSet)).isEqualTo(kamonTagSet);
    }

    @Test
    public void getDittoTagSetWithNullThrowsNullPointerException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> KamonTagSetConverter.getDittoTagSet(null))
                .withMessage("The kamonTagSet must not be null!")
                .withNoCause();
    }

    @Test
    public void getDittoTagSetReturnsExpected() {
        assertThat(KamonTagSetConverter.getDittoTagSet(kamonTagSet)).isEqualTo(dittoTagSet);
    }

}