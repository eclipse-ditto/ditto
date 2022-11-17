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
package org.eclipse.ditto.internal.utils.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.net.URI;

import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;
import org.eclipse.ditto.internal.utils.tracing.span.SpanTagKey;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link TraceInformation}.
 */
public final class TraceInformationTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(TraceInformation.class, areImmutable(), provided(TagSet.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(TraceInformation.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void newInstanceWithNullTraceUriThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> TraceInformation.newInstance(null, TagSet.empty()))
                .withMessage("The traceUri must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceWithNullTagSetThrowsNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> TraceInformation.newInstance(URI.create("/foo"), null))
                .withMessage("The tagSet must not be null!")
                .withNoCause();
    }

    @Test
    public void getTraceUriReturnsExpected() {
        final var uri = URI.create("/foo/bar");
        final var underTest = TraceInformation.newInstance(uri, TagSet.empty());

        assertThat(underTest.getTraceUri()).isEqualTo(uri);
    }

    @Test
    public void getTagSetReturnsExpected() {
        final var uri = URI.create("/foo/bar");
        final var tagSet = TagSet.ofTag(SpanTagKey.REQUEST_URI.getTagForValue(uri));
        final var underTest = TraceInformation.newInstance(uri, tagSet);

        final var actualTagSet = underTest.getTagSet();

        assertThat(actualTagSet).isEqualTo(tagSet);
    }

}