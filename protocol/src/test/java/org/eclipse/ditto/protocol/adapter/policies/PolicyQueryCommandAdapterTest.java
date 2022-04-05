/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.adapter.policies;

import java.util.function.Predicate;

import javax.annotation.Nonnull;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.commands.query.PolicyQueryCommand;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.TestConstants;
import org.eclipse.ditto.protocol.TestConstants.Policies;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.UnknownCommandException;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapterTest;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link PolicyQueryCommandAdapter}.
 */
public final class PolicyQueryCommandAdapterTest implements ProtocolAdapterTest {

    private PolicyQueryCommandAdapter underTest;

    @Before
    public void setUp() {
        underTest = PolicyQueryCommandAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Test(expected = UnknownCommandException.class)
    public void unknownCommandToAdaptable() {
        underTest.toAdaptable(new UnknownPolicyQueryCommand(), TopicPath.Channel.NONE);
    }

    @Test
    public void retrieveThingWithFieldsFromAdaptable() {
        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("policyId");
        final RetrievePolicy expected =
                RetrievePolicy.of(TestConstants.POLICY_ID, TestConstants.DITTO_HEADERS_V_2, selectedFields);

        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.POLICY_ID).commands().retrieve().build();
        final JsonPointer path = JsonPointer.empty();
        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withFields(selectedFields)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final PolicyQueryCommand<?> actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void retrieveThingWithFieldsToAdaptable() {
        final JsonFieldSelector selectedFields = JsonFieldSelector.newInstance("policyId");
        final TopicPath topicPath = TopicPath.newBuilder(TestConstants.POLICY_ID).commands().retrieve().build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path).withFields(selectedFields).build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final RetrievePolicy retrievePolicy =
                RetrievePolicy.of(TestConstants.POLICY_ID, TestConstants.HEADERS_V_2_NO_CONTENT_TYPE, selectedFields);
        final Adaptable actual = underTest.toAdaptable(retrievePolicy, TopicPath.Channel.NONE);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    private static class UnknownPolicyQueryCommand implements PolicyQueryCommand<UnknownPolicyQueryCommand> {

        @Override
        public String getType() {
            return "policies.commands:modifyThingId";
        }

        @Nonnull
        @Override
        public String getManifest() {
            return getType();
        }

        @Override
        public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate predicate) {
            return JsonObject.newBuilder()
                    .set(JsonFields.TYPE, getType())
                    .set("thingId", getEntityId().toString())
                    .build();
        }

        @Override
        public DittoHeaders getDittoHeaders() {
            return TestConstants.DITTO_HEADERS_V_2;
        }

        @Override
        public JsonPointer getResourcePath() {
            return JsonPointer.of("/thingId");
        }

        @Override
        public PolicyId getEntityId() {
            return Policies.POLICY_ID;
        }

        @Override
        public Category getCategory() {
            return Category.MODIFY;
        }

        @Override
        public UnknownPolicyQueryCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
            return this;
        }

    }

}
