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
package org.eclipse.ditto.protocoladapter.policies;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.Payload;
import org.eclipse.ditto.protocoladapter.ProtocolAdapterTest;
import org.eclipse.ditto.protocoladapter.TestConstants;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.signals.base.ErrorRegistry;
import org.eclipse.ditto.signals.base.GlobalErrorRegistry;
import org.eclipse.ditto.signals.commands.policies.PolicyErrorResponse;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link PolicyErrorResponseAdapter}.
 */
public final class PolicyErrorResponseAdapterTest implements ProtocolAdapterTest {

    private PolicyErrorResponseAdapter underTest;
    private DittoRuntimeException dittoRuntimeException;

    @Before
    public void setUp() {
        final ErrorRegistry<DittoRuntimeException> errorRegistry = GlobalErrorRegistry.getInstance();
        underTest = PolicyErrorResponseAdapter.of(DittoProtocolAdapter.getHeaderTranslator(), errorRegistry);
        dittoRuntimeException = DittoRuntimeException
                .newBuilder("error.code", HttpStatusCode.UNAUTHORIZED)
                .message("the message")
                .description("the description")
                .build();
    }

    @Test
    public void testFromAdaptable() {

        final PolicyErrorResponse expected =
                PolicyErrorResponse.of(TestConstants.Policies.POLICY_ID, dittoRuntimeException);

        final TopicPath topicPath =
                TopicPath.newBuilder(TestConstants.Policies.POLICY_ID).policies().none().errors().build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(dittoRuntimeException.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final PolicyErrorResponse actual = underTest.fromAdaptable(adaptable);
        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void testToAdaptable() {
        final PolicyErrorResponse policyErrorResponse =
                PolicyErrorResponse.of(TestConstants.Policies.POLICY_ID, dittoRuntimeException);

        final TopicPath topicPath =
                TopicPath.newBuilder(TestConstants.Policies.POLICY_ID).policies().none().errors().build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(dittoRuntimeException.toJson(FieldType.regularOrSpecial()))
                        .withStatus(HttpStatusCode.UNAUTHORIZED)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final Adaptable actual = underTest.toAdaptable(policyErrorResponse, TopicPath.Channel.NONE);
        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }
}
