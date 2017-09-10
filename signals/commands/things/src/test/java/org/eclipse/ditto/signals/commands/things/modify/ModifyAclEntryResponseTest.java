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
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ModifyAclEntryResponse}.
 */
public final class ModifyAclEntryResponseTest {

    private static final JsonObject KNOWN_JSON_CREATED = JsonFactory.newObjectBuilder()
            .set(ThingCommandResponse.JsonFields.TYPE, ModifyAclEntryResponse.TYPE)
            .set(ThingCommandResponse.JsonFields.STATUS, HttpStatusCode.CREATED.toInt())
            .set(ThingCommandResponse.JsonFields.JSON_THING_ID, TestConstants.Thing.THING_ID)
            .set(ModifyAclEntryResponse.JSON_ACL_ENTRY,
                    TestConstants.Authorization.ACL_ENTRY_OLDMAN.toJson(FieldType.regularOrSpecial()))
            .build();

    private static final JsonObject KNOWN_JSON_UPDATED = KNOWN_JSON_CREATED.toBuilder()
            .set(ThingCommandResponse.JsonFields.STATUS.getPointer(), HttpStatusCode.NO_CONTENT.toInt())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyAclEntryResponse.class,
                areImmutable(),
                provided(AuthorizationSubject.class, AclEntry.class).areAlsoImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ModifyAclEntryResponse.class)
                .withRedefinedSuperclass()
                .verify();
    }


    @Test
    public void toJsonReturnsExpected() {
        final ModifyAclEntryResponse underTestCreated =
                ModifyAclEntryResponse.created(TestConstants.Thing.THING_ID,
                        TestConstants.Authorization.ACL_ENTRY_OLDMAN, DittoHeaders.empty());
        final JsonObject actualJsonCreated = underTestCreated.toJson(FieldType.regularOrSpecial());

        assertThat(actualJsonCreated).isEqualTo(KNOWN_JSON_CREATED);

        final ModifyAclEntryResponse underTestUpdated =
                ModifyAclEntryResponse.modified(TestConstants.Thing.THING_ID,
                        TestConstants.Authorization.ACL_ENTRY_OLDMAN, DittoHeaders.empty());
        final JsonObject actualJsonUpdated = underTestUpdated.toJson(FieldType.regularOrSpecial());

        assertThat(actualJsonUpdated).isEqualTo(KNOWN_JSON_UPDATED);
    }


    @Test
    public void createInstanceFromValidJson() {
        final ModifyAclEntryResponse underTestCreated =
                ModifyAclEntryResponse.fromJson(KNOWN_JSON_CREATED, DittoHeaders.empty());

        assertThat(underTestCreated).isNotNull();
        assertThat(underTestCreated.getAclEntry()).isEqualTo(TestConstants.Authorization.ACL_ENTRY_OLDMAN);

        final ModifyAclEntryResponse underTestUpdated =
                ModifyAclEntryResponse.fromJson(KNOWN_JSON_UPDATED, DittoHeaders.empty());

        assertThat(underTestUpdated).isNotNull();
        assertThat(underTestUpdated.getAclEntry()).isEqualTo(TestConstants.Authorization.ACL_ENTRY_OLDMAN);
    }

}
