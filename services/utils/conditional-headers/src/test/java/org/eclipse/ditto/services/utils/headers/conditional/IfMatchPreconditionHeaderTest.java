/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.headers.conditional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.base.headers.entitytag.EntityTagMatchers.fromCommaSeparatedString;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Optional;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTagMatchers;
import org.junit.Test;

/**
 * Tests {@link IfMatchPreconditionHeader}.
 */
public class IfMatchPreconditionHeaderTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(IfMatchPreconditionHeader.class, areImmutable());
    }

    @Test
    public void getKey() {
        final IfMatchPreconditionHeader ifMatchPreconditionHeader =
                createIfMatchPreconditionHeader(fromCommaSeparatedString("\"test\""));
        assertThat(ifMatchPreconditionHeader.getKey()).isEqualTo("if-match");
    }

    @Test
    public void getValue() {
        final IfMatchPreconditionHeader ifMatchPreconditionHeader =
                createIfMatchPreconditionHeader(fromCommaSeparatedString("\"4711\""));
        assertThat(ifMatchPreconditionHeader.getValue()).isEqualTo("\"4711\"");
    }

    @Test
    public void meetsCondition() {
        final IfMatchPreconditionHeader ifMatchPreconditionHeader =
                createIfMatchPreconditionHeader(fromCommaSeparatedString("\"4711\""));
        assertThat(ifMatchPreconditionHeader.meetsConditionFor(EntityTag.fromString("\"4711\""))).isTrue();
    }

    @Test
    public void doesNotMeetConditionForNull() {
        final IfMatchPreconditionHeader ifMatchPreconditionHeader =
                createIfMatchPreconditionHeader(fromCommaSeparatedString("\"4711\""));
        assertThat(ifMatchPreconditionHeader.meetsConditionFor(null)).isFalse();
    }

    @Test
    public void asteriskDoesNotMeetConditionForNull() {
        final IfMatchPreconditionHeader ifMatchPreconditionHeader =
                createIfMatchPreconditionHeader(fromCommaSeparatedString("*"));
        assertThat(ifMatchPreconditionHeader.meetsConditionFor(null)).isFalse();
    }

    @Test
    public void asteriskMeetsConditionForNonNull() {
        final IfMatchPreconditionHeader ifMatchPreconditionHeader =
                createIfMatchPreconditionHeader(fromCommaSeparatedString("*"));
        assertThat(ifMatchPreconditionHeader.meetsConditionFor(EntityTag.fromString("\"4711\""))).isTrue();
        assertThat(ifMatchPreconditionHeader.meetsConditionFor(EntityTag.fromString("\"foo\""))).isTrue();
    }

    @Test
    public void ListMeetsConditionIfContained() {
        final IfMatchPreconditionHeader ifMatchPreconditionHeader =
                createIfMatchPreconditionHeader(fromCommaSeparatedString("\"foo\",\"bar\""));
        assertThat(ifMatchPreconditionHeader.meetsConditionFor(EntityTag.fromString("\"bar\""))).isTrue();
    }

    @Test
    public void ListDoesNotMeetConditionIfNotContained() {
        final IfMatchPreconditionHeader ifMatchPreconditionHeader =
                createIfMatchPreconditionHeader(fromCommaSeparatedString("\"foo\",\"bar\""));
        assertThat(ifMatchPreconditionHeader.meetsConditionFor(EntityTag.fromString("\"baz\""))).isFalse();
    }

    @Test
    public void fromDittoHeaders() {
        final DittoHeaders dittoHeaders =
                DittoHeaders.newBuilder().ifMatch(fromCommaSeparatedString("*")).build();

        final Optional<IfMatchPreconditionHeader> ifMatchPreconditionHeader =
                IfMatchPreconditionHeader.fromDittoHeaders(dittoHeaders);

        assertThat(ifMatchPreconditionHeader).isPresent();
        assertThat(ifMatchPreconditionHeader.get().getKey()).isEqualTo("if-match");
        assertThat(ifMatchPreconditionHeader.get().getValue()).isEqualTo("*");
    }

    private IfMatchPreconditionHeader createIfMatchPreconditionHeader(final EntityTagMatchers entityTags) {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().ifMatch(entityTags).build();
        return IfMatchPreconditionHeader.fromDittoHeaders(dittoHeaders).get();
    }
}
