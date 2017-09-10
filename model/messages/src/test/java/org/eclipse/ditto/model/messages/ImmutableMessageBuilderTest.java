/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.messages;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.junit.Test;

/**
 * Unit test for {@link ImmutableMessageBuilder}.
 */
public final class ImmutableMessageBuilderTest {

    private final static String KNOWN_THING_ID = "bla:foo-bar";
    private final static String KNOWN_FEATURE_ID = "plop";
    private final static String KNOWN_SUBJECT = "this.is.a.subject";
    private final static String KNOWN_STRING_PAYLOAD = "some string payload;\nirrelevant what!";
    private final static ByteBuffer KNOWN_RAW_PAYLOAD =
            ByteBuffer.wrap(KNOWN_STRING_PAYLOAD.getBytes(StandardCharsets.UTF_8));
    private static final String KNOWN_CONTENT_TYPE_TEXT_PLAIN = "text/plain";
    private static final Duration KNOWN_TIMEOUT = Duration.ofMinutes(1);
    private static final OffsetDateTime KNOWN_TIMESTAMP = OffsetDateTime.now();
    private static final String KNOWN_CORRELATION_ID = UUID.randomUUID().toString();
    private static final AuthorizationContext KNOWN_AUTH_CONTEXT = AuthorizationModelFactory.emptyAuthContext();
    private static final HttpStatusCode KNOWN_STATUS_CODE = HttpStatusCode.OK;

    @Test
    public void buildToThingMessageWithEmptyPayload() {
        final MessageHeaders messageHeaders =
                MessageHeaders.newBuilder(MessageDirection.TO, KNOWN_THING_ID, KNOWN_SUBJECT).build();

        final Message<?> message = ImmutableMessageBuilder.newInstance(messageHeaders).build();

        assertThat(message.getDirection()).isEqualTo(MessageDirection.TO);
        assertThat(message.getThingId()).isNotEmpty();
        assertThat(message.getThingId()).isEqualTo(KNOWN_THING_ID);
        assertThat(message.getSubject()).isEqualTo(KNOWN_SUBJECT);
        assertThat(message.getFeatureId()).isEmpty();
        assertThat(message.getPayload()).isEmpty();
        assertThat(message.getContentType()).isEmpty();
    }

    @Test
    public void buildFromFeatureMessageWithStringPayload() throws UnsupportedEncodingException {
        final MessageHeaders messageHeaders =
                MessageHeaders.newBuilder(MessageDirection.FROM, KNOWN_THING_ID, KNOWN_SUBJECT)
                        .featureId(KNOWN_FEATURE_ID)
                        .contentType(KNOWN_CONTENT_TYPE_TEXT_PLAIN)
                        .timeout(KNOWN_TIMEOUT)
                        .build();

        final Message<String> message = ImmutableMessageBuilder.<String>newInstance(messageHeaders)
                .payload(KNOWN_STRING_PAYLOAD)
                .build();

        assertThat(message.getDirection()).isEqualTo(MessageDirection.FROM);
        assertThat(message.getThingId()).isNotEmpty();
        assertThat(message.getThingId()).isEqualTo(KNOWN_THING_ID);
        assertThat(message.getSubject()).isEqualTo(KNOWN_SUBJECT);
        assertThat(message.getFeatureId()).hasValue(KNOWN_FEATURE_ID);
        assertThat(message.getPayload()).hasValue(KNOWN_STRING_PAYLOAD);
        assertThat(message.getContentType()).hasValue(KNOWN_CONTENT_TYPE_TEXT_PLAIN);
        assertThat(message.getTimeout()).hasValue(KNOWN_TIMEOUT);
    }

}
