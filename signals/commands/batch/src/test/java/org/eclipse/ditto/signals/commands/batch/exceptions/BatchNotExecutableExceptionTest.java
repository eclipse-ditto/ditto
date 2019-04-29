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
