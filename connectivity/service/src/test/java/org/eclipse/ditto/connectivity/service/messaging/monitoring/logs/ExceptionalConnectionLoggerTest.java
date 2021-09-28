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
package org.eclipse.ditto.connectivity.service.messaging.monitoring.logs;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link ExceptionalConnectionLogger}.
 */
public final class ExceptionalConnectionLoggerTest {

    @Test
    public void testEqualsAndHashcode() {
        EqualsVerifier.forClass(ExceptionalConnectionLogger.class)
                .withIgnoredFields("logger")
                .verify();
    }

}
