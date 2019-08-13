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
package org.eclipse.ditto.signals.commands.things.modify;

import static org.eclipse.ditto.signals.commands.things.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyPolicyIdResponse}.
 */
public class ModifyPolicyIdResponseTest {

    private static final String KNOWN_POLICY_ID = "namepsace:policyId";

    private static final JsonObject KNOWN_JSON_CREATED = JsonFactory.newObjectBuilder()
            .set(ThingCommandResponse.JsonFields.TYPE, ModifyPolicyIdResponse.TYPE)
            .set(ThingCommandResponse.JsonFields.STATUS, HttpStatusCode.CREATED.toInt())
            .set(ThingCommandResponse.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .set(ModifyPolicyIdResponse.JSON_POLICY_ID, KNOWN_POLICY_ID)
            .build();

    private static final JsonObject KNOWN_JSON_UPDATED = JsonFactory.newObjectBuilder()
            .set(ThingCommandResponse.JsonFields.TYPE, ModifyPolicyIdResponse.TYPE)
            .set(ThingCommandResponse.JsonFields.STATUS, HttpStatusCode.NO_CONTENT.toInt())
            .set(ThingCommandResponse.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyPolicyIdResponse.class, areImmutable(), provided(ThingId.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyPolicyIdResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test
    public void toJsonReturnsExpected() {
        final ModifyPolicyIdResponse underTestCreated = ModifyPolicyIdResponse
                .created(TestConstants.Thing.THING_ID, KNOWN_POLICY_ID, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJsonCreated = underTestCreated.toJson(FieldType.regularOrSpecial());

        assertThat(actualJsonCreated).isEqualTo(KNOWN_JSON_CREATED);

        final ModifyPolicyIdResponse underTestUpdated =
                ModifyPolicyIdResponse.modified(TestConstants.Thing.THING_ID, TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJsonUpdated = underTestUpdated.toJson(FieldType.regularOrSpecial());

        assertThat(actualJsonUpdated).isEqualTo(KNOWN_JSON_UPDATED);
    }


    @Test
    public void createInstanceFromValidJson() {
        final ModifyPolicyIdResponse underTestCreated =
                ModifyPolicyIdResponse.fromJson(KNOWN_JSON_CREATED, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTestCreated).isNotNull();
        assertThat(underTestCreated.getPolicyId()).hasValue(KNOWN_POLICY_ID);

        final ModifyPolicyIdResponse underTestUpdated =
                ModifyPolicyIdResponse.fromJson(KNOWN_JSON_UPDATED, TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTestUpdated).isNotNull();
        assertThat(underTestUpdated.getPolicyId()).isEmpty();
    }

}
