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
package org.eclipse.ditto.model.things;

import static org.eclipse.ditto.model.things.assertions.DittoThingsAssertions.assertThat;

import java.time.Instant;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.junit.Test;


public final class ImmutableThingV1V2MigrationTest {

    @Test
    public void createThingV1andMigrateToV2() {
        final AccessControlList acl = ThingsModelFactory.newAclBuilder() //
                .set(ThingsModelFactory.newAclEntry(AuthorizationSubject.newInstance("some-user-id"), //
                        ThingsModelFactory.allPermissions())) //
                .set(ThingsModelFactory.newAclEntry(AuthorizationSubject.newInstance("some-group-id"), //
                        ThingsModelFactory.newPermissions(Permission.READ))).build();
        final Attributes attributes = ThingsModelFactory.newAttributesBuilder() //
                .set("foo", 1) //
                .set("complex/bar", false) //
                .build();
        final Feature feature = ThingsModelFactory.newFeatureBuilder() //
                .properties(ThingsModelFactory.newFeaturePropertiesBuilder() //
                        .set("bum", "lux") //
                        .build() //
                ) //
                .withId("myFeature1") //
                .build();
        final Features features = ThingsModelFactory.newFeatures(feature);
        final Thing thingV1 = ImmutableThing
                .of("com.example:myThing", acl, attributes, features, ThingLifecycle.ACTIVE,
                        ThingRevision.newInstance(1),
                        Instant.now());

        System.out.println("V1:\n" + thingV1.toJsonString(JsonSchemaVersion.V_1));
        System.out.println("V2:\n" + thingV1.toJsonString());
        System.out.println("V2 with special:\n" + thingV1.toJsonString(FieldType.regularOrSpecial()));
    }

    @Test
    public void createThingV2andMigrateToV1() {
        final String thingId = "com.example:myThing";

        final String endUser = "EndUser";
        final String support = "Support";

        final String im3UserSid = "uuid-of-im-user";
        final String im3GroupSid = "uuid-of-im-group";

        final Attributes attributes = ThingsModelFactory.newAttributesBuilder() //
                .set("foo", 1) //
                .set("complex/bar", false) //
                .build();
        final Feature feature = ThingsModelFactory.newFeatureBuilder() //
                .properties(ThingsModelFactory.newFeaturePropertiesBuilder() //
                        .set("bum", "lux") //
                        .build() //
                ) //
                .withId("myFeature1") //
                .build();
        final Features features = ThingsModelFactory.newFeatures(feature);
        final Thing thingV2 = ImmutableThing
                .of(thingId, thingId, attributes, features, ThingLifecycle.ACTIVE, ThingRevision.newInstance(1),
                        Instant.now());

        System.out.println("V1:\n" + thingV2.toJsonString(JsonSchemaVersion.V_1));
        System.out.println("V2:\n" + thingV2.toJsonString());

        System.out.println("V2 with special:\n" + thingV2.toJsonString(FieldType.regularOrSpecial()));
    }

    @Test
    public void createThingV2FromJson() {
        final String thingId = "com.example:myThing";

        final String endUser = "EndUser";
        final String support = "Support";

        final String im3UserSid = "uuid-of-im-user";
        final String im3GroupSid = "uuid-of-im-group";

        final Attributes attributes = ThingsModelFactory.newAttributesBuilder() //
                .set("foo", 1) //
                .set("complex/bar", false) //
                .build();
        final Feature feature = ThingsModelFactory.newFeatureBuilder() //
                .properties(ThingsModelFactory.newFeaturePropertiesBuilder() //
                        .set("bum", "lux") //
                        .build() //
                ) //
                .withId("myFeature1") //
                .build();
        final Features features = ThingsModelFactory.newFeatures(feature);
        final Thing thingV2 = ImmutableThing
                .of(thingId, thingId, attributes, features, ThingLifecycle.ACTIVE, ThingRevision.newInstance(1),
                        Instant.now());

        final String thingJson = thingV2.toJsonString(FieldType.regularOrSpecial());
        final Thing thingFromJson = ThingsModelFactory.newThing(thingJson);

        System.out.println(thingV2.toJsonString(FieldType.regularOrSpecial()));
        System.out.println(thingFromJson.toJsonString(FieldType.regularOrSpecial()));

        assertThat(thingFromJson).isEqualTo(thingV2);
    }
}
