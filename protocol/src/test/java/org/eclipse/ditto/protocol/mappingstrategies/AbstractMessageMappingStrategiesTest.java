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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.messages.model.MessageDirection;
import org.eclipse.ditto.messages.model.MessageHeaders;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.InvalidPathException;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.adapter.UnknownTopicPathException;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;

public final class AbstractMessageMappingStrategiesTest {

    @Test
    public void messageHeadersFrom() {
        final Adaptable adaptable = Adaptable.newBuilder(
                TopicPath.newBuilder(ThingId.generateRandom()).live().messages().subject("test").build())
                .withPayload(Payload.newBuilder(JsonPointer.of("/inbox/messages/test")).build())
                .build();
        final MessageHeaders messageHeaders = AbstractMessageMappingStrategies.messageHeadersFrom(adaptable);
        assertThat(messageHeaders.getDirection()).isEqualTo(MessageDirection.TO);
        assertThat(messageHeaders.getSubject()).isEqualTo("test");
    }


    @Test
    public void messageHeadersFromThrowsWhenSubjectMissingInTopicPath() {
        final Adaptable adaptable =
                Adaptable.newBuilder(TopicPath.newBuilder(ThingId.generateRandom()).live().messages().build())
                        .withPayload(Payload.newBuilder(JsonPointer.of("/inbox/messages/test")).build())
                        .build();

        assertThatExceptionOfType(UnknownTopicPathException.class)
                .isThrownBy(() -> AbstractMessageMappingStrategies.messageHeadersFrom(adaptable));
    }

    @Test
    public void messageHeadersFromThrowsWhenDirectionMissingInPath() {
        final Adaptable adaptable = Adaptable.newBuilder(
                TopicPath.newBuilder(ThingId.generateRandom()).live().messages().subject("test").build())
                .withPayload(Payload.newBuilder(JsonPointer.of("/messages/test")).build())
                .build();

        assertThatExceptionOfType(InvalidPathException.class)
                .isThrownBy(() -> AbstractMessageMappingStrategies.messageHeadersFrom(adaptable));
    }

    @Test
    public void messageHeadersFromWithFeaturePath() {
        final Adaptable adaptable = Adaptable.newBuilder(
                        TopicPath.newBuilder(ThingId.generateRandom()).live().messages().subject("test").build())
                .withPayload(Payload.newBuilder(JsonPointer.of("/features/xy/inbox/messages/test")).build())
                .build();
        final MessageHeaders messageHeaders = AbstractMessageMappingStrategies.messageHeadersFrom(adaptable);
        assertThat(messageHeaders.getDirection()).isEqualTo(MessageDirection.TO);
        assertThat(messageHeaders.getSubject()).isEqualTo("test");
    }

    @Test
    public void messageHeadersFromThrowsWhenPointerIsNoFeaturePointerAndNotExplicitlyOutbox() {
        final Adaptable adaptable = Adaptable.newBuilder(
                        TopicPath.newBuilder(ThingId.generateRandom()).live().messages().subject("test").build())
                .withPayload(Payload.newBuilder(JsonPointer.of("test/outbox/messages/test")).build())
                .build();

        assertThatExceptionOfType(InvalidPathException.class)
                .isThrownBy(() -> AbstractMessageMappingStrategies.messageHeadersFrom(adaptable))
                .satisfies(invalidPathException -> {
                    assertThat(invalidPathException.getDescription().orElse("")).contains("pattern");
                });
    }

    @Test
    public void messageHeadersFromThrowsWhenPointerIsNoFeaturePointerAndNotExplicitlyInbox() {
        final Adaptable adaptable = Adaptable.newBuilder(
                        TopicPath.newBuilder(ThingId.generateRandom()).live().messages().subject("test").build())
                .withPayload(Payload.newBuilder(JsonPointer.of("test/inbox/messages/test")).build())
                .build();

        assertThatExceptionOfType(InvalidPathException.class)
                .isThrownBy(() -> AbstractMessageMappingStrategies.messageHeadersFrom(adaptable))
                .satisfies(invalidPathException -> {
                    assertThat(invalidPathException.getDescription().orElse("")).contains("pattern");
                });
    }

}
