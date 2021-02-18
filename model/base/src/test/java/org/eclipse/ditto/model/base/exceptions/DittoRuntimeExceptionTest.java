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
package org.eclipse.ditto.model.base.exceptions;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DittoRuntimeException}.
 */
public final class DittoRuntimeExceptionTest {

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DittoRuntimeException.class)
                .withIgnoredFields("detailMessage", "cause", "stackTrace", "suppressedExceptions")
                .withRedefinedSuperclass()
                .usingGetClass()
                .verify();
    }

}
