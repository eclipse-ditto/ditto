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
package org.eclipse.ditto.signals.commands.things.modify;

import static org.eclipse.ditto.signals.commands.things.assertions.ThingCommandAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.AccessControlListModelFactory;
import org.eclipse.ditto.model.things.AclNotAllowedException;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.PolicyIdNotAllowedException;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyThing}.
 */
public final class ModifyThingTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingCommand.JsonFields.TYPE, ModifyThing.TYPE)
            .set(ThingCommand.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID)
            .set(ModifyThing.JSON_THING, TestConstants.Thing.THING.toJson(FieldType.regularOrSpecial()))
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyThing.class,
                areImmutable(),
                provided(Thing.class, JsonObject.class).isAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyThing.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullThing() {
        ModifyThing.of(TestConstants.Thing.THING_ID, null, null, TestConstants.EMPTY_DITTO_HEADERS);
    }


    @Test
    public void toJsonReturnsExpected() {
        final ModifyThing underTest =
                ModifyThing.of(TestConstants.Thing.THING_ID, TestConstants.Thing.THING, null,
                        TestConstants.EMPTY_DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson(FieldType.regularOrSpecial());

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }


    @Test
    public void createInstanceFromValidJson() {
        final ModifyThing underTest = ModifyThing.fromJson(KNOWN_JSON.toString(), TestConstants.EMPTY_DITTO_HEADERS);

        assertThat(underTest).isNotNull();
        assertThat(underTest.getThing()).isEqualTo(TestConstants.Thing.THING);
    }

    @Test(expected = AclNotAllowedException.class)
    public void ensuresNoACLInV2Command() {
        final DittoHeaders v2Headers = DittoHeaders.newBuilder().schemaVersion(JsonSchemaVersion.LATEST).build();
        final Thing thingWithAcl = TestConstants.Thing.THING.toBuilder()
                .setPermissions(AuthorizationModelFactory.newAuthSubject("any"),
                        AccessControlListModelFactory.allPermissions())
                .removePolicyId()
                .build();
        ModifyThing.of(TestConstants.Thing.THING_ID, thingWithAcl, null, v2Headers);
    }

    @Test(expected = PolicyIdNotAllowedException.class)
    public void ensuresNoPolicyInV1Command() {
        final DittoHeaders v1Headers = DittoHeaders.newBuilder().schemaVersion(JsonSchemaVersion.V_1).build();
        final Thing thingWithoutAclAndPolicy = TestConstants.Thing.THING.toBuilder()
                .removeAllPermissions()
                .removePolicyId()
                .build();
        final JsonObject initialPolicy = JsonObject.newBuilder().build();
        ModifyThing.of(TestConstants.Thing.THING_ID, thingWithoutAclAndPolicy, initialPolicy, v1Headers);

    }

    @Test(expected = PolicyIdNotAllowedException.class)
    public void ensuresNoPolicyIdInV1Command() {
        final DittoHeaders v1Headers = DittoHeaders.newBuilder().schemaVersion(JsonSchemaVersion.V_1).build();
        final Thing thingWithPolicyId = TestConstants.Thing.THING.toBuilder()
                .removeAllPermissions()
                .setPolicyId("any:policyId")
                .build();
        ModifyThing.of(TestConstants.Thing.THING_ID, thingWithPolicyId, null, v1Headers);

    }

}
