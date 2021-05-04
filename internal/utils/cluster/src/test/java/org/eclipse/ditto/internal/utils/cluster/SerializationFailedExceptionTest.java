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
package org.eclipse.ditto.internal.utils.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * Unit test for {@link SerializerExceptions.SerializationFailed}.
 */
public final class SerializationFailedExceptionTest {

    @Test
    public void exceptionHasExpectedMessageAndCause() {
        final var message = "I was not in the mood to serialize this!";
        final var cause = new IllegalStateException("unmotivated");

        final var underTest = new SerializerExceptions.SerializationFailed(message, cause);

        assertThat(underTest).hasMessage(message).hasCause(cause);
    }

}
