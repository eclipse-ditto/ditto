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
package org.eclipse.ditto.signals.commands.messages;

import java.util.UUID;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.messages.MessageDirection;
import org.eclipse.ditto.model.messages.MessageHeaders;
import org.junit.Test;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SendMessageAcceptedResponse}.
 */
public final class SendMessageAcceptedResponseTest {

    private static final String THING_ID = "test.ns:theThingId";
    private static final String SUBJECT = "theSubject";
    private static final MessageDirection DIRECTION = MessageDirection.TO;
    private static final String CORRELATION_ID = UUID.randomUUID().toString();
    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.newBuilder().correlationId(CORRELATION_ID).build();
    private static final MessageHeaders MESSAGE_HEADERS = MessageHeaders.newBuilder(DIRECTION, THING_ID, SUBJECT).build();

    @Test(expected = NullPointerException.class)
    public void tryCreateWithNullThingId() {
        SendMessageAcceptedResponse.newInstance(null, MESSAGE_HEADERS, DITTO_HEADERS);
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
