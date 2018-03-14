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

package org.eclipse.ditto.services.connectivity.messaging;



import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.connectivity.messaging.MessageHeaderFilter.Mode.*;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.junit.Test;

public class MessageHeaderFilterTest {

    private static final String[] headerNames = new String[]{"A", "B", "C"};
    private static final ExternalMessage textMessage = ConnectivityModelFactory.newExternalMessageBuilder(
            Arrays.stream(headerNames).collect(Collectors.toMap(n -> n, n-> n))).withText("text").build();
    private static final ExternalMessage bytesMessage = ConnectivityModelFactory.newExternalMessageBuilder(
            Arrays.stream(headerNames).collect(Collectors.toMap(n -> n, n-> n))).withBytes("bytes".getBytes()).build();

    @Test
    public void excludeTextMessage() {
        final MessageHeaderFilter filter = new MessageHeaderFilter(EXCLUDE, headerNames[0]);

        assertThat(textMessage.findHeader(headerNames[0]).isPresent());
        assertThat(textMessage.findHeader(headerNames[1])).isPresent();
        assertThat(textMessage.findHeader(headerNames[2])).isPresent();
        ExternalMessage actual = filter.apply(textMessage);
        assertThat(actual.findHeader(headerNames[0])).isEmpty();
        assertThat(actual.findHeader(headerNames[1])).isPresent();
        assertThat(actual.findHeader(headerNames[2])).isPresent();

        assertTypeAndPayloadAreNotModified(actual, textMessage);
    }

    @Test
    public void excludeBytesMessage() {
        final MessageHeaderFilter filter = new MessageHeaderFilter(EXCLUDE, headerNames[0]);

        assertThat(bytesMessage.findHeader(headerNames[0]).isPresent());
        assertThat(bytesMessage.findHeader(headerNames[1])).isPresent();
        assertThat(bytesMessage.findHeader(headerNames[2])).isPresent();
        ExternalMessage actual = filter.apply(bytesMessage);
        assertThat(actual.findHeader(headerNames[0])).isEmpty();
        assertThat(actual.findHeader(headerNames[1])).isPresent();
        assertThat(actual.findHeader(headerNames[2])).isPresent();

        assertTypeAndPayloadAreNotModified(actual, bytesMessage);
    }

    @Test
    public void includeTextMessage() {
        final MessageHeaderFilter filter = new MessageHeaderFilter(INCLUDE, headerNames[0]);

        assertThat(textMessage.findHeader(headerNames[0]).isPresent());
        assertThat(textMessage.findHeader(headerNames[1])).isPresent();
        assertThat(textMessage.findHeader(headerNames[2])).isPresent();
        ExternalMessage actual = filter.apply(textMessage);
        assertThat(actual.findHeader(headerNames[0])).isPresent();
        assertThat(actual.findHeader(headerNames[1])).isEmpty();
        assertThat(actual.findHeader(headerNames[2])).isEmpty();

        assertTypeAndPayloadAreNotModified(actual, textMessage);
    }

    @Test
    public void includeTextBytesMessage() {
        final MessageHeaderFilter filter = new MessageHeaderFilter(INCLUDE, headerNames[0]);

        assertThat(bytesMessage.findHeader(headerNames[0]).isPresent());
        assertThat(bytesMessage.findHeader(headerNames[1])).isPresent();
        assertThat(bytesMessage.findHeader(headerNames[2])).isPresent();
        ExternalMessage actual = filter.apply(bytesMessage);

        assertThat(actual.findHeader(headerNames[0])).isPresent();
        assertThat(actual.findHeader(headerNames[1])).isEmpty();
        assertThat(actual.findHeader(headerNames[2])).isEmpty();

        assertTypeAndPayloadAreNotModified(actual, bytesMessage);
    }

    private void assertTypeAndPayloadAreNotModified(final ExternalMessage actual, final ExternalMessage expected) {
        assertThat(actual.getMessageType()).isEqualTo(expected.getMessageType());
        assertThat(actual.getBytePayload()).isEqualTo(expected.getBytePayload());
        assertThat(actual.getTextPayload()).isEqualTo(expected.getTextPayload());
    }
}