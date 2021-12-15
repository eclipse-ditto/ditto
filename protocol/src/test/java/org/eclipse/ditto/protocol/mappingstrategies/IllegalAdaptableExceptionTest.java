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
package org.eclipse.ditto.protocol.mappingstrategies;

import org.eclipse.ditto.protocol.Adaptable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Unit test for {@link IllegalAdaptableException}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class IllegalAdaptableExceptionTest {

    @Mock
    private Adaptable adaptable;

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(IllegalAdaptableException.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .suppress(Warning.NONFINAL_FIELDS, Warning.TRANSIENT_FIELDS)
                .withIgnoredFields("cause", "stackTrace", "suppressedExceptions")
                .verify();
    }

}