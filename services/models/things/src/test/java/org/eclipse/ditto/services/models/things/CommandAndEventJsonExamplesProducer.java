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
package org.eclipse.ditto.services.models.things;

import static org.eclipse.ditto.model.base.auth.AuthorizationModelFactory.newAuthSubject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseOptions;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThings;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingsResponse;

/* */
public final class CommandAndEventJsonExamplesProducer {

    private static final JsonParseOptions JSON_PARSE_OPTIONS =
            JsonFactory.newParseOptionsBuilder().withoutUrlDecoding().build();

    public static void main(final String... args) throws IOException {
        if (args.length != 1) {
            System.err.println("Exactly 1 argument required: the target folder in which to generate the JSON files");
            System.exit(-1);
        }
        produce(Paths.get(args[0]));
    }

    private static void produce(final Path rootPath) throws IOException {
        produceSudoCommands(rootPath.resolve("commands"));
    }

    private static void produceSudoCommands(final Path rootPath) throws IOException {
        final Path sudoCommandsDir = rootPath.resolve(Paths.get("sudo"));
        Files.createDirectories(sudoCommandsDir);

        final JsonFieldSelector sudoFieldSelector =
                JsonFactory.newFieldSelector("thingId,_revision,__lifecycle", JSON_PARSE_OPTIONS);

        final String namespace = "org.eclipse.ditto";
        final ThingId thingId1 = ThingId.of(namespace, "the_thingId_1");
        final ThingId thingId2 = ThingId.of(namespace, "the_thingId_2");
        final ThingId thingId3 = ThingId.of(namespace, "the_thingId_3");


        final SudoRetrieveThing sudoRetrieveThing =
                SudoRetrieveThing.of(thingId1, TestConstants.EMPTY_HEADERS);
        writeJson(sudoCommandsDir.resolve(Paths.get("sudoRetrieveThing.json")), sudoRetrieveThing);

        final SudoRetrieveThing sudoRetrieveThingWithFieldSelector =
                SudoRetrieveThing.of(thingId1, sudoFieldSelector, TestConstants.EMPTY_HEADERS);
        writeJson(sudoCommandsDir.resolve(Paths.get("sudoRetrieveThing-withFieldSelector.json")),
                sudoRetrieveThingWithFieldSelector);

        final SudoRetrieveThings sudoRetrieveThings = SudoRetrieveThings
                .of(Arrays.asList(thingId1, thingId2,
                        thingId3),
                        TestConstants.EMPTY_HEADERS);
        writeJson(sudoCommandsDir.resolve(Paths.get("sudoRetrieveThings.json")), sudoRetrieveThings);

        final SudoRetrieveThings sudoRetrieveThingsWithFieldSelector = SudoRetrieveThings
                .of(Arrays.asList(thingId1, thingId2,
                        thingId3),
                        sudoFieldSelector, TestConstants.EMPTY_HEADERS);
        writeJson(sudoCommandsDir.resolve(Paths.get("sudoRetrieveThings-withFieldSelector.json")),
                sudoRetrieveThingsWithFieldSelector);

        final Thing exampleThingJson = createExampleThing(thingId1);
        final Thing exampleThing2Json = createExampleThing(thingId2);

        final JsonObject exampleThingJsonRestrictedByPredicate = exampleThingJson.toJson(FieldType.notHidden());
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                SudoRetrieveThingResponse.of(exampleThingJsonRestrictedByPredicate, TestConstants.EMPTY_HEADERS);
        writeJson(sudoCommandsDir.resolve(Paths.get("sudoRetrieveThingResponse.json")), sudoRetrieveThingResponse);

        final JsonObject exampleThingJsonRestrictedByFieldSelector = exampleThingJson.toJson(sudoFieldSelector);
        final SudoRetrieveThingResponse sudoRetrieveThingResponseWithFieldSelector =
                SudoRetrieveThingResponse.of(exampleThingJsonRestrictedByFieldSelector, TestConstants.EMPTY_HEADERS);
        writeJson(sudoCommandsDir.resolve(Paths.get("sudoRetrieveThingResponse-withFieldSelector.json")),
                sudoRetrieveThingResponseWithFieldSelector);

        final SudoRetrieveThingsResponse sudoRetrieveThingsResponse = SudoRetrieveThingsResponse
                .of(Arrays.asList(exampleThingJson, exampleThing2Json), FieldType.notHidden(),
                        TestConstants.EMPTY_HEADERS);
        writeJson(sudoCommandsDir.resolve(Paths.get("sudoRetrieveThingsResponse.json")), sudoRetrieveThingsResponse);

        final SudoRetrieveThingsResponse sudoRetrieveThingsResponseWithFieldSelector =
                SudoRetrieveThingsResponse.of(Arrays.asList(exampleThingJson, exampleThing2Json), //
                        sudoFieldSelector, FieldType.regularOrSpecial(), TestConstants.EMPTY_HEADERS);
        writeJson(sudoCommandsDir.resolve(Paths.get("sudoRetrieveThingsResponse-withFieldSelector.json")),
                sudoRetrieveThingsResponseWithFieldSelector);
    }

    private static Thing createExampleThing(final ThingId thingId) {
        final AuthorizationSubject authorizationSubject = newAuthSubject("the_acl_subject");
        final AuthorizationSubject anotherAuthorizationSubject = newAuthSubject("another_acl_subject");

        return ThingsModelFactory.newThingBuilder() //
                .setLifecycle(ThingLifecycle.ACTIVE) //
                .setRevision(1) //
                .setPermissions(authorizationSubject, org.eclipse.ditto.model.things.Permission.READ,
                        org.eclipse.ditto.model.things.Permission.WRITE,
                        org.eclipse.ditto.model.things.Permission.ADMINISTRATE) //
                .setPermissions(anotherAuthorizationSubject, org.eclipse.ditto.model.things.Permission.READ,
                        org.eclipse.ditto.model.things.Permission.WRITE) //
                .setId(thingId) //
                .build();
    }

    private static void writeJson(final Path path, final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable)
            throws IOException {
        final String jsonString = jsonifiable.toJsonString(FieldType.regularOrSpecial());
        System.out.println("Writing file: " + path.toAbsolutePath());
        Files.write(path, jsonString.getBytes());
    }

}
