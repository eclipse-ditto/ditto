/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.mapping.javascript;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.util.HashMap;

import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.ExternalMessageFactory;
import org.junit.Test;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.typedarrays.NativeArrayBuffer;

public class ScriptedIncomingMappingTest {

    private static final byte[] BYTES = "payload".getBytes();

    @Test
    public void mapExternalMessage() {
        mapExternalMessage(ByteBuffer.wrap(BYTES));
    }

    @Test
    public void mapExternalMessageFromReadOnlyBuffer() {
        mapExternalMessage(ByteBuffer.wrap(BYTES).asReadOnlyBuffer());
    }

    private void mapExternalMessage(final ByteBuffer source) {
        final ExternalMessage externalMessage = ExternalMessageFactory
                .newExternalMessageBuilder(new HashMap<>())
                .withBytes(source)
                .build();

        final NativeObject nativeObject = ScriptedIncomingMapping.mapExternalMessageToNativeObject(externalMessage);

        final NativeArrayBuffer bytePayload = (NativeArrayBuffer) nativeObject.get("bytePayload");
        assertThat(bytePayload.getBuffer()).isEqualTo(BYTES);
    }
}