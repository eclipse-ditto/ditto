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
package org.eclipse.ditto.model.things.examplejson;

import static org.eclipse.ditto.json.JsonFactory.newPointer;
import static org.eclipse.ditto.json.JsonFactory.newValue;
import static org.eclipse.ditto.model.base.auth.AuthorizationModelFactory.newAuthSubject;
import static org.eclipse.ditto.model.things.ThingsModelFactory.newAclEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Permission;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.model.things.ThingsModelFactory;


public final class ThingModelJsonExamplesProducer {

    public static void main(final String... args) throws IOException {
        if (args.length != 1) {
            System.err.println("Exactly 1 argument required: the target folder in which to generate the JSON files");
            System.exit(-1);
        }
        produce(Paths.get(args[0]));
    }

    private static void produce(final Path rootPath) throws IOException {
        final List<AuthorizationSubject> authorizationSubjects = new ArrayList<>();
        authorizationSubjects.add(newAuthSubject("the_firstSubject"));
        authorizationSubjects.add(newAuthSubject("the_anotherSubject"));
        final AuthorizationContext authContext = AuthorizationModelFactory.newAuthContext(authorizationSubjects);

        final Path authorizationDir = rootPath.resolve(Paths.get("authorization"));
        Files.createDirectories(authorizationDir);
        writeJson(authorizationDir.resolve(Paths.get("authorizationContext.json")), authContext);

        produceThingModel(rootPath);
    }


    private static void produceThingModel(final Path rootPath) throws IOException {
        final Path modelDir = rootPath.resolve(Paths.get("model"));
        Files.createDirectories(modelDir);

        final String thingId = "org.eclipse.ditto.example:the_thingId";
        final AuthorizationSubject authorizationSubject1 = newAuthSubject("the_acl_subject");

        final Thing thing = ThingsModelFactory.newThingBuilder() //
                .setLifecycle(ThingLifecycle.ACTIVE) //
                .setRevision(1) //
                .setModified(Instant.now()) //
                .setPermissions(authorizationSubject1, Permission.READ, Permission.WRITE, Permission.ADMINISTRATE) //
                .setId(thingId) //
                .build();
        writeJson(modelDir.resolve(Paths.get("thing.json")), thing);

        final Attributes thingAttributes = ThingsModelFactory.newAttributesBuilder() //
                .set(newPointer("location/latitude"), newValue(44.673856)) //
                .set(newPointer("location/longitude"), newValue(8.261719)) //
                .set(newPointer("maker"), newValue("Bosch")) //
                .build();

        final AuthorizationSubject authorizationSubject = newAuthSubject("the_acl_subject");
        final AuthorizationSubject anotherAuthorizationSubject = newAuthSubject("another_acl_subject");
        final AccessControlList accessControlList = ThingsModelFactory.newAclBuilder() //
                .set(newAclEntry(authorizationSubject, Permission.READ, Permission.WRITE, Permission.ADMINISTRATE)) //
                .set(newAclEntry(anotherAuthorizationSubject, Permission.READ, Permission.WRITE)) //
                .build();

        final Thing thingFilled = ThingsModelFactory.newThingBuilder() //
                .setAttributes(thingAttributes) //
                .setLifecycle(ThingLifecycle.ACTIVE) //
                .setRevision(1) //
                .setModified(Instant.now()) //
                .setPermissions(accessControlList) //
                .setId(thingId) //
                .build();
        writeJson(modelDir.resolve(Paths.get("thing-filled.json")), thingFilled);

        final FeatureProperties featureProperties1 = ThingsModelFactory.newFeaturePropertiesBuilder() //
                .set("status", createAttributeExampleJsonObject()) //
                .set("configuration", JsonFactory.newObject("{\"rpm\": 500, \"active\": true}")) //
                .build();

        final Feature feature = ThingsModelFactory.newFeature("myFeature", featureProperties1);
        writeJson(modelDir.resolve(Paths.get("feature.json")), feature);

        final FeatureProperties featureProperties2 = ThingsModelFactory.newFeaturePropertiesBuilder() //
                .set("bumlux", "aString") //
                .set("myCategory", JsonFactory.newObject("{\"thinking\": \"aloud\", \"bold\": false}")) //
                .build();

        final Feature feature2 = ThingsModelFactory.newFeature("Flux_capacitor", featureProperties2);

        final Thing thingFilledAndFeatures = ThingsModelFactory.newThingBuilder() //
                .setAttributes(thingAttributes) //
                .setFeatures(ThingsModelFactory.newFeatures(feature, feature2)) //
                .setLifecycle(ThingLifecycle.ACTIVE) //
                .setRevision(42) //
                .setModified(Instant.now()) //
                .setPermissions(accessControlList) //
                .setId(thingId) //
                .build();
        writeJson(modelDir.resolve(Paths.get("thing-filled-features.json")), thingFilledAndFeatures);
        writeString(modelDir.resolve(Paths.get("thing-filled-features-special.json")),
                thingFilledAndFeatures.toJsonString(FieldType.regularOrSpecial()));
    }

    private static Attributes createAttributeExampleJsonObject() {
        return ThingsModelFactory.newAttributes("{\"some\": \"attr\", \"foo\": true}");
    }

    private static void writeJson(final Path path, final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable)
            throws IOException {
        final String jsonString = jsonifiable.toJsonString();
        writeString(path, jsonString);
    }

    private static void writeString(final Path path, final String jsonString) throws IOException {
        System.out.println("Writing file: " + path.toAbsolutePath());
        Files.write(path, jsonString.getBytes());
    }
}
