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
package org.eclipse.ditto.messages.model;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Unit test for {@link MessageFormatInvalidException}.
 */
public final class MessageFormatInvalidExceptionTest {

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(MessageFormatInvalidException.class)
                .usingGetClass()
                .withIgnoredFields("cause", "stackTrace", "suppressedExceptions")
                .suppress(Warning.NONFINAL_FIELDS)
                .verify();
    }

}
