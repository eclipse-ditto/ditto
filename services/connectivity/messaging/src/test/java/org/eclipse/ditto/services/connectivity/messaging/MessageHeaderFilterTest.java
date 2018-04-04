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
import static org.eclipse.ditto.services.connectivity.messaging.MessageHeaderFilter.Mode.EXCLUDE;
import static org.eclipse.ditto.services.connectivity.messaging.MessageHeaderFilter.Mode.INCLUDE;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.ExternalMessage;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class MessageHeaderFilterTest {

    private static final String[] headerNames = new String[]{"A", "B", "C"};
    private static final ExternalMessage textMessage = ConnectivityModelFactory.newExternalMessageBuilder(
            Arrays.stream(headerNames).collect(Collectors.toMap(n -> n, n -> n))).withText("text").build();
    private static final ExternalMessage bytesMessage = ConnectivityModelFactory.newExternalMessageBuilder(
            Arrays.stream(headerNames).collect(Collectors.toMap(n -> n, n -> n))).withBytes("bytes".getBytes()).build();
    private static final MessageHeaderFilter EXCLUDE_FILTER = new MessageHeaderFilter(EXCLUDE, headerNames[0]);
    private static final MessageHeaderFilter EMPTY_EXCLUDE_FILTER = new MessageHeaderFilter(EXCLUDE);
    private static final MessageHeaderFilter INCLUDE_FILTER = new MessageHeaderFilter(INCLUDE, headerNames[0]);
    private static final MessageHeaderFilter EMPTY_INCLUDE_FILTER = new MessageHeaderFilter(INCLUDE);

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(MessageHeaderFilter.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(MessageHeaderFilter.class, areImmutable());
    }

    @Test
    public void emptyExcludeFilter() {
        final ExternalMessage filtered = doFilterMessage(textMessage, EMPTY_EXCLUDE_FILTER);
        assertExpectedHeadersArePresent(filtered, headerNames);
    }

    @Test
    public void emptyIncludeFilter() {
        final ExternalMessage filtered = doFilterMessage(textMessage, EMPTY_INCLUDE_FILTER);
        assertExpectedHeadersArePresent(filtered);
    }

    @Test
    public void excludeTextMessage() {
        final ExternalMessage filtered = doFilterMessage(textMessage, EXCLUDE_FILTER);
        assertExpectedHeadersArePresent(filtered, headerNames[1], headerNames[2]);
    }

    @Test
    public void excludeBytesMessage() {
        final ExternalMessage filtered = doFilterMessage(bytesMessage, EXCLUDE_FILTER);
        assertExpectedHeadersArePresent(filtered, headerNames[1], headerNames[2]);
    }

    @Test
    public void includeTextMessage() {
        final ExternalMessage filtered = doFilterMessage(textMessage, INCLUDE_FILTER);
        assertExpectedHeadersArePresent(filtered, headerNames[0]);
    }

    @Test
    public void includeTextBytesMessage() {
        final ExternalMessage filtered = doFilterMessage(bytesMessage, INCLUDE_FILTER);
        assertExpectedHeadersArePresent(filtered, headerNames[0]);
    }

    private ExternalMessage doFilterMessage(final ExternalMessage message, final MessageHeaderFilter filter) {
        assertExpectedHeadersArePresent(message, headerNames);
        final ExternalMessage actual = filter.apply(message);
        assertTypeAndPayloadAreNotModified(actual, message);
        return actual;
    }

    private void assertExpectedHeadersArePresent(final ExternalMessage message, final String... expectedHeaders) {
        for (final String expectedHeader : expectedHeaders) {
            assertThat(message.findHeader(expectedHeader)).isNotEmpty();
        }
        final Set<String> blacklistedHeaders = new HashSet<>(Arrays.asList(headerNames));
        blacklistedHeaders.removeAll(Arrays.asList(expectedHeaders));
        for (final String blacklisted : blacklistedHeaders) {
            assertThat(message.findHeader(blacklisted)).isEmpty();
        }
    }

    private void assertTypeAndPayloadAreNotModified(final ExternalMessage actual, final ExternalMessage expected) {
        assertThat(actual.getTopicPath()).isEqualTo(expected.getTopicPath());
        assertThat(actual.getBytePayload()).isEqualTo(expected.getBytePayload());
        assertThat(actual.getTextPayload()).isEqualTo(expected.getTextPayload());
    }
}