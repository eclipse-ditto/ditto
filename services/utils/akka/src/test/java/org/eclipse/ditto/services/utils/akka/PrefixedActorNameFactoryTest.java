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
package org.eclipse.ditto.services.utils.akka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * Tests {@link PrefixedActorNameFactory}.
 */
public class PrefixedActorNameFactoryTest {

    private static final String PREFIX = "prefix";

    private PrefixedActorNameFactory underTest = PrefixedActorNameFactory.of(PREFIX);

    @Test
    public void createActorNames() {
        // first
        assertThat(underTest.createActorName()).isEqualTo(createExpectedName("a"));

        // second
        assertThat(underTest.createActorName()).isEqualTo(createExpectedName("b"));
    }

    private String createExpectedName(final String suffix) {
        return PREFIX + "-" + suffix;
    }
}
