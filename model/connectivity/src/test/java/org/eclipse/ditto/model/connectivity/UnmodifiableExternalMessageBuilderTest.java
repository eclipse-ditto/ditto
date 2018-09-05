/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */

package org.eclipse.ditto.model.connectivity;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.junit.Test;
import org.mockito.Mockito;

public class UnmodifiableExternalMessageBuilderTest {

    private static final String PAYLOAD = "payload";
    private static final byte[] BYTES = PAYLOAD.getBytes(StandardCharsets.UTF_8);

    @Test
    public void testBuildExternalMessageWithTextPayload() {
        testBuildExternalMessage(false);
    }

    @Test
    public void testBuildExternalMessageWithBytePayload() {
        testBuildExternalMessage(true);
    }

    public void testBuildExternalMessage(final boolean bytePayload) {
        final AuthorizationContext authorizationContext = Mockito.mock(AuthorizationContext.class);
        final Map<String, String> headers = new HashMap<>();
        headers.put("eclipse", "ditto");

        final UnmodifiableExternalMessageBuilder messageBuilder =
                new UnmodifiableExternalMessageBuilder(headers);

        messageBuilder.withAdditionalHeaders("ditto", "eclipse");
        messageBuilder.withAuthorizationContext(authorizationContext);
        if (bytePayload) {
            messageBuilder.withBytes(BYTES);
        } else {
            messageBuilder.withText(PAYLOAD);
        }

        final ExternalMessage externalMessage = messageBuilder.build();

        assertThat(externalMessage.getHeaders()).containsEntry("eclipse", "ditto");
        assertThat(externalMessage.getHeaders()).containsEntry("ditto", "eclipse");
        assertThat(externalMessage.getAuthorizationContext()).contains(authorizationContext);
        assertThat(externalMessage.isError()).isFalse();
        assertThat(externalMessage.isResponse()).isFalse();

        if (bytePayload) {
            assertThat(externalMessage.getTextPayload()).isEmpty();
            assertThat(externalMessage.getBytePayload()).contains(ByteBuffer.wrap(BYTES));
        } else {
            assertThat(externalMessage.getTextPayload()).contains(PAYLOAD);
            assertThat(externalMessage.getBytePayload()).isEmpty();
        }
    }

}