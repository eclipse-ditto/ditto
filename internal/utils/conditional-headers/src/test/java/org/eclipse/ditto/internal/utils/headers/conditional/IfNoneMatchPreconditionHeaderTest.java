/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.headers.conditional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatchers.fromCommaSeparatedString;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTag;
import org.eclipse.ditto.base.model.headers.entitytag.EntityTagMatchers;
import org.junit.Test;

/**
 * Tests {@link IfNoneMatchPreconditionHeader}.
 */
public class IfNoneMatchPreconditionHeaderTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(IfNoneMatchPreconditionHeader.class, areImmutable());
    }

    @Test
    public void getKey() {
        final IfNoneMatchPreconditionHeader ifNoneMatchPreconditionHeader =
                createIfNoneMatchPreconditionHeader(EntityTagMatchers.fromCommaSeparatedString("\"test\""));
        assertThat(ifNoneMatchPreconditionHeader.getKey()).isEqualTo("if-none-match");
    }

    @Test
    public void getValue() {
        final IfNoneMatchPreconditionHeader ifNoneMatchPreconditionHeader =
                createIfNoneMatchPreconditionHeader(EntityTagMatchers.fromCommaSeparatedString("\"4711\""));
        assertThat(ifNoneMatchPreconditionHeader.getValue()).isEqualTo("\"4711\"");
    }

    @Test
    public void doesNotMeetConditionForEqualOpaqueTag() {
        final IfNoneMatchPreconditionHeader ifNoneMatchPreconditionHeader =
                createIfNoneMatchPreconditionHeader(EntityTagMatchers.fromCommaSeparatedString("\"4711\""));
        assertThat(ifNoneMatchPreconditionHeader.meetsConditionFor(EntityTag.fromString("\"4711\""))).isFalse();
        assertThat(ifNoneMatchPreconditionHeader.meetsConditionFor(EntityTag.fromString("W/\"4711\""))).isFalse();
    }

    @Test
    public void meetsConditionForNull() {
        final IfNoneMatchPreconditionHeader ifNoneMatchPreconditionHeader =
                createIfNoneMatchPreconditionHeader(EntityTagMatchers.fromCommaSeparatedString("\"4711\""));
        assertThat(ifNoneMatchPreconditionHeader.meetsConditionFor(null)).isTrue();
    }

    @Test
    public void asteriskMeetsConditionForNull() {
        final IfNoneMatchPreconditionHeader ifNoneMatchPreconditionHeader =
                createIfNoneMatchPreconditionHeader(EntityTagMatchers.fromCommaSeparatedString("*"));
        assertThat(ifNoneMatchPreconditionHeader.meetsConditionFor(null)).isTrue();
    }

    @Test
    public void asteriskDoesNotMeetConditionForNonNull() {
        final IfNoneMatchPreconditionHeader ifNoneMatchPreconditionHeader =
                createIfNoneMatchPreconditionHeader(EntityTagMatchers.fromCommaSeparatedString("*"));
        assertThat(ifNoneMatchPreconditionHeader.meetsConditionFor(EntityTag.fromString("\"4711\""))).isFalse();
        assertThat(ifNoneMatchPreconditionHeader.meetsConditionFor(EntityTag.fromString("\"foo\""))).isFalse();
    }

    @Test
    public void ListMeetsConditionIfNotContained() {
        final IfNoneMatchPreconditionHeader ifNoneMatchPreconditionHeader =
                createIfNoneMatchPreconditionHeader(fromCommaSeparatedString("\"foo\",\"bar\""));
        assertThat(ifNoneMatchPreconditionHeader.meetsConditionFor(EntityTag.fromString("\"baz\""))).isTrue();
    }

    @Test
    public void ListDoesNotMeetConditionIfContained() {
        final IfNoneMatchPreconditionHeader ifNoneMatchPreconditionHeader =
                createIfNoneMatchPreconditionHeader(fromCommaSeparatedString("\"foo\",\"bar\""));
        assertThat(ifNoneMatchPreconditionHeader.meetsConditionFor(EntityTag.fromString("\"bar\""))).isFalse();
    }

    @Test
    public void fromDittoHeaders() {
        final DittoHeaders dittoHeaders =
                DittoHeaders.newBuilder().ifNoneMatch(EntityTagMatchers.fromCommaSeparatedString("*")).build();

        final Optional<IfNoneMatchPreconditionHeader> ifNoneMatchPreconditionHeader =
                IfNoneMatchPreconditionHeader.fromDittoHeaders(dittoHeaders);

        Assertions.assertThat(ifNoneMatchPreconditionHeader).isPresent();
        assertThat(ifNoneMatchPreconditionHeader.get().getKey()).isEqualTo("if-none-match");
        assertThat(ifNoneMatchPreconditionHeader.get().getValue()).isEqualTo("*");
    }

    private IfNoneMatchPreconditionHeader createIfNoneMatchPreconditionHeader(final EntityTagMatchers entityTags) {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().ifNoneMatch(entityTags).build();
        return IfNoneMatchPreconditionHeader.fromDittoHeaders(dittoHeaders).get();
    }
}
