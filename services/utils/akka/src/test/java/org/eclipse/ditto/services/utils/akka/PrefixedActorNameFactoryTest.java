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
