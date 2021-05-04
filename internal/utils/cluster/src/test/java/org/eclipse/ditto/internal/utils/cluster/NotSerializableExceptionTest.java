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
 * Unit test for {@link SerializerExceptions.NotSerializable}.
 */
public final class NotSerializableExceptionTest {

    @Test
    public void getMessageReturnsExpected() {
        final var serializerName = "MySerializer";
        final var notSerializableObject = new Object();

        final var underTest = new SerializerExceptions.NotSerializable(serializerName, notSerializableObject);

        assertThat(underTest).hasMessage("Serializer <%s> can't serialize object of type <%s>.",
                serializerName, notSerializableObject.getClass());
    }

}
