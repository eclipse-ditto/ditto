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
package org.eclipse.ditto.internal.utils.headers.conditional;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.Test;

/**
 * Basic test for {@link ConditionalHeadersValidator}. The concrete functionality is tested in context of the using
 * service.
 */
public final class ConditionalHeadersValidatorTest {

    @Test
    public void creationFailsWithNullValidationSettings() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ConditionalHeadersValidator.of(null));
    }

}
