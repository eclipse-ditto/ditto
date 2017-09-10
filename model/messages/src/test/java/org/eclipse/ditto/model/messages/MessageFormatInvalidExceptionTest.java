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
package org.eclipse.ditto.model.messages;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link MessageFormatInvalidException}.
 */
public final class MessageFormatInvalidExceptionTest {


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(MessageFormatInvalidException.class)
                .usingGetClass()
                .withIgnoredFields("detailMessage", "cause", "stackTrace", "suppressedExceptions")
                .verify();
    }

}
