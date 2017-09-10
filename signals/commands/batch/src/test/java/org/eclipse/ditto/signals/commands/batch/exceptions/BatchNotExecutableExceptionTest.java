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
package org.eclipse.ditto.signals.commands.batch.exceptions;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link BatchNotExecutableException}.
 */
public final class BatchNotExecutableExceptionTest {


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(BatchNotExecutableException.class)
                .withRedefinedSuperclass()
                // Actually DittoRuntimeException checks these fields using the appropriate getter
                .withIgnoredFields("detailMessage", "cause", "stackTrace", "suppressedExceptions")
                .usingGetClass()
                .verify();
    }

}
