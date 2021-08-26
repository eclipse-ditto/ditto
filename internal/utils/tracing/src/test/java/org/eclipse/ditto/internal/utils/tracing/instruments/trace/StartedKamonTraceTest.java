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
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.Before;
import org.junit.Test;

import kamon.context.Context;

public class StartedKamonTraceTest {

    private StartedTrace underTest;

    @Before
    public void setup() {
        underTest = new PreparedKamonTrace(Context.Empty(), "started").start();
    }

    @Test
    public void taggingWorks() {
        assertThatNoException().isThrownBy(() -> {
            underTest.tag("stringTag", "2");
            underTest.tag("longTag", 2L);
            underTest.tag("booleanTag", true);
            underTest.tag("doubleTag", 2.0);
        });
    }

    @Test
    public void traceTags() {
        assertThatNoException().isThrownBy(() -> {
            underTest.correlationId("12345");
            underTest.connectionType("test");
            underTest.connectionId("connection-1");
        });
    }

    @Test
    public void nullTraceTagsAreIgnored() {
        assertThatNoException().isThrownBy(() -> {
            underTest.correlationId(null);
            underTest.connectionType(null);
            underTest.connectionId(null);
        });
    }

    @Test
    public void selfReturnsSelf() {
        assertThat(underTest.self()).isSameAs(underTest);
    }
}
