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
package org.eclipse.ditto.messages.model;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import java.time.Duration;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;

/**
 * Unit test for {@link ImmutableMessageBuilder}.
 */
public final class ImmutableMessageBuilderTest {

    private final static ThingId KNOWN_THING_ID = ThingId.of("bla", "foo-bar");
    private final static String KNOWN_FEATURE_ID = "plop";
    private final static String KNOWN_SUBJECT = "this.is.a.subject";
    private final static String KNOWN_STRING_PAYLOAD = "some string payload;\nirrelevant what!";
    private final static JsonObject KNOWN_EXTRA = JsonObject.newBuilder().set("foo", false).build();
    private static final String KNOWN_CONTENT_TYPE_TEXT_PLAIN = "text/plain";
    private static final Duration KNOWN_TIMEOUT = Duration.ofMinutes(1);

    @Test
    public void buildToThingMessageWithEmptyPayload() {
        final MessageHeaders messageHeaders =
                MessageHeaders.newBuilder(MessageDirection.TO, KNOWN_THING_ID, KNOWN_SUBJECT).build();

        final Message<?> message = ImmutableMessageBuilder.newInstance(messageHeaders).build();

        assertThat(message.getDirection()).isEqualTo(MessageDirection.TO);
        assertThat((CharSequence) message.getEntityId()).isEqualTo(KNOWN_THING_ID);
        assertThat(message.getSubject()).isEqualTo(KNOWN_SUBJECT);
        assertThat(message.getFeatureId()).isEmpty();
        assertThat(message.getPayload()).isEmpty();
        assertThat(message.getContentType()).isEmpty();
    }

    @Test
    public void buildFromFeatureMessageWithStringPayload() {
        final MessageHeaders messageHeaders =
                MessageHeaders.newBuilder(MessageDirection.FROM, KNOWN_THING_ID, KNOWN_SUBJECT)
                        .featureId(KNOWN_FEATURE_ID)
                        .contentType(KNOWN_CONTENT_TYPE_TEXT_PLAIN)
                        .timeout(KNOWN_TIMEOUT)
                        .build();

        final Message<String> message = ImmutableMessageBuilder.<String>newInstance(messageHeaders)
                .payload(KNOWN_STRING_PAYLOAD)
                .extra(KNOWN_EXTRA)
                .build();

        assertThat(message.getDirection()).isEqualTo(MessageDirection.FROM);
        assertThat((CharSequence) message.getEntityId()).isEqualTo(KNOWN_THING_ID);
        assertThat(message.getSubject()).isEqualTo(KNOWN_SUBJECT);
        assertThat(message.getFeatureId()).hasValue(KNOWN_FEATURE_ID);
        assertThat(message.getPayload()).hasValue(KNOWN_STRING_PAYLOAD);
        assertThat(message.getExtra()).hasValue(KNOWN_EXTRA);
        assertThat(message.getContentType()).hasValue(KNOWN_CONTENT_TYPE_TEXT_PLAIN);
        assertThat(message.getTimeout()).hasValue(KNOWN_TIMEOUT);
    }

}
