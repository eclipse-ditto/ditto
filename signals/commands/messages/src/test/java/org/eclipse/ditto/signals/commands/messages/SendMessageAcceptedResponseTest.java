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
package org.eclipse.ditto.signals.commands.messages;

import java.util.UUID;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.messages.MessageDirection;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingIdInvalidException;
import org.junit.Test;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SendMessageAcceptedResponse}.
 */
public final class SendMessageAcceptedResponseTest {

    private static final ThingId THING_ID = ThingId.of("test.ns","theThingId");
    private static final String SUBJECT = "theSubject";
    private static final MessageDirection DIRECTION = MessageDirection.TO;
    private static final String CORRELATION_ID = UUID.randomUUID().toString();
    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder().correlationId(CORRELATION_ID).build();
    private static final MessageHeaders MESSAGE_HEADERS = MessageHeaders.newBuilder(DIRECTION, THING_ID, SUBJECT).build();

    @Test(expected = ThingIdInvalidException.class)
    public void tryCreateWithNullThingIdString() {
        SendMessageAcceptedResponse.newInstance((String) null, MESSAGE_HEADERS, DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryCreateWithNullThingId() {
        SendMessageAcceptedResponse.newInstance((ThingId) null, MESSAGE_HEADERS, DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryCreateWithNullMessageHeaders() {
        SendMessageAcceptedResponse.newInstance(THING_ID, null, DITTO_HEADERS);
    }

    @Test(expected = NullPointerException.class)
    public void tryCreateWithNullDittoHeaders() {
        SendMessageAcceptedResponse.newInstance(THING_ID, MESSAGE_HEADERS, null);
    }

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(SendMessageAcceptedResponse.class, MutabilityMatchers.areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SendMessageAcceptedResponse.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .verify();
    }
}
