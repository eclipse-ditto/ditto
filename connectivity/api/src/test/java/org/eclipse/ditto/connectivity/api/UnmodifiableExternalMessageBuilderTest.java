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
package org.eclipse.ditto.connectivity.api;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.protocol.TopicPath;
import org.junit.Test;
import org.mockito.Mockito;

public class UnmodifiableExternalMessageBuilderTest {

    private static final String PAYLOAD = "payload";
    private static final byte[] BYTES = PAYLOAD.getBytes(StandardCharsets.UTF_8);

    @Test
    public void testBuildExternalMessageWithTextPayload() {
        testBuildExternalMessage(true, false);
    }

    @Test
    public void testBuildExternalMessageWithBytePayload() {
        testBuildExternalMessage(false, true);
    }

    @Test
    public void testBuildExternalMessageWithTextAndBytePayload() {
        testBuildExternalMessage(true, true);
    }

    private void testBuildExternalMessage(final boolean textPayload, final boolean bytePayload) {
        final AuthorizationContext authorizationContext = Mockito.mock(AuthorizationContext.class);
        final TopicPath topicPath = Mockito.mock(TopicPath.class);
        final Map<String, String> headers = new HashMap<>();
        headers.put("eclipse", "ditto");

        final UnmodifiableExternalMessageBuilder messageBuilder =
                new UnmodifiableExternalMessageBuilder(headers);

        messageBuilder.withAdditionalHeaders("ditto", "eclipse");
        messageBuilder.withAuthorizationContext(authorizationContext);
        messageBuilder.withTopicPath(topicPath);
        if (textPayload && bytePayload) {
            messageBuilder.withTextAndBytes(PAYLOAD, BYTES);
        } else if (bytePayload) {
            messageBuilder.withBytes(BYTES);
        } else {
            messageBuilder.withText(PAYLOAD);
        }

        final ExternalMessage externalMessage = messageBuilder.build();

        Assertions.assertThat(externalMessage.getHeaders()).containsEntry("eclipse", "ditto");
        Assertions.assertThat(externalMessage.getHeaders()).containsEntry("ditto", "eclipse");
        Assertions.assertThat(externalMessage.getAuthorizationContext()).contains(authorizationContext);
        Assertions.assertThat(externalMessage.getTopicPath()).contains(topicPath);
        Assertions.assertThat(externalMessage.isError()).isFalse();
        Assertions.assertThat(externalMessage.isResponse()).isFalse();

        if (textPayload && bytePayload) {
            Assertions.assertThat(externalMessage.isBytesMessage()).isTrue();
            Assertions.assertThat(externalMessage.isTextMessage()).isTrue();
            Assertions.assertThat(externalMessage.getTextPayload()).contains(PAYLOAD);
            Assertions.assertThat(externalMessage.getBytePayload()).contains(ByteBuffer.wrap(BYTES));
        } else if (bytePayload) {
            Assertions.assertThat(externalMessage.isBytesMessage()).isTrue();
            Assertions.assertThat(externalMessage.isTextMessage()).isFalse();
            Assertions.assertThat(externalMessage.getTextPayload()).isEmpty();
            Assertions.assertThat(externalMessage.getBytePayload()).contains(ByteBuffer.wrap(BYTES));
        } else {
            Assertions.assertThat(externalMessage.isBytesMessage()).isFalse();
            Assertions.assertThat(externalMessage.isTextMessage()).isTrue();
            Assertions.assertThat(externalMessage.getTextPayload()).contains(PAYLOAD);
            Assertions.assertThat(externalMessage.getBytePayload()).isEmpty();
        }
    }

}
