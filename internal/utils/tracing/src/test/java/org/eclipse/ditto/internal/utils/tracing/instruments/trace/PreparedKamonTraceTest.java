/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.tracing.instruments.trace;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.internal.utils.tracing.TracingTags;
import org.junit.Before;
import org.junit.Test;

import kamon.context.Context;

public class PreparedKamonTraceTest {

    private PreparedTrace underTest;

    @Before
    public void setup() {
        underTest = new PreparedKamonTrace(Context.Empty(), "prepared");
    }

    @Test
    public void taggingWorks() {
        underTest.tag("stringTag", "2");
        underTest.tag("longTag", 2L);
        underTest.tag("booleanTag", true);
        underTest.tag("doubleTag", 2.0);

        assertThat(underTest.getTags()).hasSize(4);
        assertThat(underTest.getTag("stringTag")).isEqualTo("2");
        assertThat(underTest.getTag("longTag")).isEqualTo("2");
        assertThat(underTest.getTag("booleanTag")).isEqualTo("true");
        assertThat(underTest.getTag("doubleTag")).isEqualTo("2.0");
    }

    @Test
    public void traceTags() {
        underTest.correlationId("12345");
        underTest.connectionType("test");
        underTest.connectionId("connection-1");

        assertThat(underTest.getTags()).hasSize(3);
        assertThat(underTest.getTag(TracingTags.CORRELATION_ID)).isEqualTo("12345");
        assertThat(underTest.getTag(TracingTags.CONNECTION_TYPE)).isEqualTo("test");
        assertThat(underTest.getTag(TracingTags.CONNECTION_ID)).isEqualTo("connection-1");
    }

    @Test
    public void nullTraceTagsAreIgnored() {
        underTest.correlationId(null);
        underTest.connectionType(null);
        underTest.connectionId(null);
        assertThat(underTest.getTags()).isEmpty();
    }

    @Test
    public void selfReturnsSelf() {
        assertThat(underTest.self()).isSameAs(underTest);
    }

}
