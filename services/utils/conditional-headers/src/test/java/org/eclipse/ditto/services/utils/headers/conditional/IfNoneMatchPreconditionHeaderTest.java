/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.headers.conditional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.base.headers.entitytag.EntityTags.fromCommaSeparatedString;

import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTag;
import org.eclipse.ditto.model.base.headers.entitytag.EntityTags;
import org.junit.Test;

public class IfNoneMatchPreconditionHeaderTest {

    @Test
    public void getKey() {
        final IfNoneMatchPreconditionHeader ifNoneMatchPreconditionHeader =
                createIfNoneMatchPreconditionHeader(EntityTags.fromCommaSeparatedString("\"test\""));
        assertThat(ifNoneMatchPreconditionHeader.getKey()).isEqualTo("if-none-match");
    }

    @Test
    public void getValue() {
        final IfNoneMatchPreconditionHeader ifNoneMatchPreconditionHeader =
                createIfNoneMatchPreconditionHeader(EntityTags.fromCommaSeparatedString("\"4711\""));
        assertThat(ifNoneMatchPreconditionHeader.getValue()).isEqualTo("\"4711\"");
    }

    @Test
    public void doesNotMeetConditionForEqualOpaqueTag() {
        final IfNoneMatchPreconditionHeader ifNoneMatchPreconditionHeader =
                createIfNoneMatchPreconditionHeader(EntityTags.fromCommaSeparatedString("\"4711\""));
        assertThat(ifNoneMatchPreconditionHeader.meetsConditionFor(EntityTag.fromString("\"4711\""))).isFalse();
        assertThat(ifNoneMatchPreconditionHeader.meetsConditionFor(EntityTag.fromString("W/\"4711\""))).isFalse();
    }

    @Test
    public void meetsConditionForNull() {
        final IfNoneMatchPreconditionHeader ifNoneMatchPreconditionHeader =
                createIfNoneMatchPreconditionHeader(EntityTags.fromCommaSeparatedString("\"4711\""));
        assertThat(ifNoneMatchPreconditionHeader.meetsConditionFor(null)).isTrue();
    }

    @Test
    public void asteriskMeetsConditionForNull() {
        final IfNoneMatchPreconditionHeader ifNoneMatchPreconditionHeader =
                createIfNoneMatchPreconditionHeader(EntityTags.fromCommaSeparatedString("*"));
        assertThat(ifNoneMatchPreconditionHeader.meetsConditionFor(null)).isTrue();
    }

    @Test
    public void asteriskDoesNotMeetConditionForNonNull() {
        final IfNoneMatchPreconditionHeader ifNoneMatchPreconditionHeader =
                createIfNoneMatchPreconditionHeader(EntityTags.fromCommaSeparatedString("*"));
        assertThat(ifNoneMatchPreconditionHeader.meetsConditionFor(EntityTag.fromString("\"4711\""))).isFalse();
        assertThat(ifNoneMatchPreconditionHeader.meetsConditionFor(EntityTag.fromString("\"foo\""))).isFalse();
        assertThat(ifNoneMatchPreconditionHeader.meetsConditionFor(EntityTag.fromString("*"))).isFalse();
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
                DittoHeaders.newBuilder().ifNoneMatch(EntityTags.fromCommaSeparatedString("*")).build();

        final Optional<IfNoneMatchPreconditionHeader> ifNoneMatchPreconditionHeader =
                IfNoneMatchPreconditionHeader.fromDittoHeaders(dittoHeaders);

        Assertions.assertThat(ifNoneMatchPreconditionHeader).isPresent();
        assertThat(ifNoneMatchPreconditionHeader.get().getKey()).isEqualTo("if-none-match");
        assertThat(ifNoneMatchPreconditionHeader.get().getValue()).isEqualTo("*");
    }

    private IfNoneMatchPreconditionHeader createIfNoneMatchPreconditionHeader(final EntityTags entityTags) {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().ifNoneMatch(entityTags).build();
        return IfNoneMatchPreconditionHeader.fromDittoHeaders(dittoHeaders).get();
    }
}