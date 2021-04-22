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

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapterTest;
import org.eclipse.ditto.protocol.TestConstants;
import org.eclipse.ditto.protocol.TestConstants.Policies;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.protocol.UnknownCommandException;
import org.eclipse.ditto.policies.model.signals.commands.modify.PolicyModifyCommand;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link PolicyModifyCommandAdapter}.
 */
public final class PolicyModifyCommandAdapterTest implements ProtocolAdapterTest {

    private PolicyModifyCommandAdapter underTest;

    @Before
    public void setUp() {
        underTest = PolicyModifyCommandAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Test(expected = UnknownCommandException.class)
    public void unknownCommandToAdaptable() {
        underTest.toAdaptable(new UnknownPolicyModifyCommand(), TopicPath.Channel.NONE);
    }

    private static class UnknownPolicyModifyCommand implements PolicyModifyCommand<UnknownPolicyModifyCommand> {

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
        public UnknownPolicyModifyCommand setDittoHeaders(final DittoHeaders dittoHeaders) {
            return this;
        }

    }

}
