/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.messages.model.MessageHeaderDefinition;
import org.eclipse.ditto.protocol.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.commands.CommandNotSupportedException;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpResponse;
import akka.util.ByteString;

/**
 * Unit test for {@link DittoRuntimeExceptionToHttpResponse}.
 */
public final class DittoRuntimeExceptionToHttpResponseTest {

    private static HeaderTranslator headerTranslator;

    @Rule
    public final TestName testName = new TestName();

    @BeforeClass
    public static void setUpClass() {
        headerTranslator = HeaderTranslator.of(DittoHeaderDefinition.values(), MessageHeaderDefinition.values());
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DittoRuntimeExceptionToHttpResponse.class,
                areImmutable(),
                provided(HeaderTranslator.class).isAlsoImmutable());
    }

    @Test
    public void tryToGetInstanceWithNullHeaderTranslator() {
        assertThatNullPointerException()
                .isThrownBy(() -> DittoRuntimeExceptionToHttpResponse.getInstance(null))
                .withMessage("The headerTranslator must not be null!")
                .withNoCause();
    }

    @Test
    public void tryToConvertNullException() {
        final DittoRuntimeExceptionToHttpResponse underTest =
                DittoRuntimeExceptionToHttpResponse.getInstance(headerTranslator);

        assertThatNullPointerException()
                .isThrownBy(() -> underTest.apply(null))
                .withMessage("The exception must not be null!")
                .withNoCause();
    }

    @Test
    public void conversionReturnsExpected() {
        final byte invalidApiVersion = -1;
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(testName.getMethodName())
                .build();
        final CommandNotSupportedException exception = CommandNotSupportedException.newBuilder(invalidApiVersion)
                .dittoHeaders(dittoHeaders)
                .build();
        final Map<String, String> externalHeaders = headerTranslator.toExternalHeaders(dittoHeaders);
        final List<HttpHeader> httpHeaders = new ArrayList<>(externalHeaders.size());
        externalHeaders.forEach((key, value) -> httpHeaders.add(HttpHeader.parse(key, value)));
        final HttpResponse expected = HttpResponse.create()
                .withStatus(exception.getHttpStatus().getCode())
                .withHeaders(httpHeaders)
                .withEntity(ContentTypes.APPLICATION_JSON, ByteString.fromString(exception.toJsonString()));

        final DittoRuntimeExceptionToHttpResponse underTest =
                DittoRuntimeExceptionToHttpResponse.getInstance(headerTranslator);

        final HttpResponse actual = underTest.apply(exception);

        assertThat(actual).isEqualTo(expected);
    }

}
