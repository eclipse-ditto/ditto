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
 * Unit test for {@link SerializerExceptions.UnsupportedManifest}.
 */
public final class UnsupportedManifestExceptionTest {

    @Test
    public void getMessageReturnsExpected() {
        final var manifest = " We are uncovering better ways of developing\n" +
                "software by doing it and helping others do it.\n" +
                "Through this work we have come to value:\n" +
                "\n" +
                "Individuals and interactions over processes and tools\n" +
                "Working software over comprehensive documentation\n" +
                "Customer collaboration over contract negotiation\n" +
                "Responding to change over following a plan\n" +
                "\n" +
                "That is, while there is value in the items on\n" +
                "the right, we value the items on the left more.";

        final var underTest = new SerializerExceptions.UnsupportedManifest(manifest);

        assertThat(underTest).hasMessage("Serializer does not support manifest <%s>.", manifest);
    }

}
