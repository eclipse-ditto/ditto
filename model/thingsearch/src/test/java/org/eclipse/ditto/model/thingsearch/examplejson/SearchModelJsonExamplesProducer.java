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
package org.eclipse.ditto.model.thingsearch.examplejson;

import static org.eclipse.ditto.model.base.auth.AuthorizationModelFactory.newAuthSubject;
import static org.eclipse.ditto.model.things.ThingsModelFactory.newAclEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.Permission;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.model.thingsearch.SearchModelFactory;
import org.eclipse.ditto.model.thingsearch.SearchResult;


public final class SearchModelJsonExamplesProducer {

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
        final AuthorizationContext authContext = AuthorizationModelFactory.newAuthContext(
                DittoAuthorizationContextType.UNSPECIFIED, authorizationSubjects);

        final Path authorizationDir = rootPath.resolve(Paths.get("authorization"));
        Files.createDirectories(authorizationDir);
        writeJson(authorizationDir.resolve(Paths.get("authorizationContext.json")), authContext);

        produceSearchModel(rootPath);
    }


    private static void produceSearchModel(final Path rootPath) throws IOException {
        final Path modelDir = rootPath.resolve(Paths.get("model"));
        Files.createDirectories(modelDir);
        final String namespace = "org.eclipse.ditto.example";
        final ThingId thingId1 = ThingId.of(namespace, "thing1");
        final ThingId thingId2 = ThingId.of(namespace, "thing2");

        final AuthorizationSubject authorizationSubject = newAuthSubject("the_acl_subject");
        final AccessControlList accessControlList = ThingsModelFactory.newAclBuilder() //
                .set(newAclEntry(authorizationSubject, Permission.READ, Permission.WRITE, Permission.ADMINISTRATE)) //
                .build();
        final Thing thing1 = ThingsModelFactory.newThingBuilder() //
                .setId(thingId1) //
                .setPermissions(accessControlList) //
                .build();
        final Thing thing2 = ThingsModelFactory.newThingBuilder() //
                .setId(thingId2) //
                .setPermissions(accessControlList) //
                .build();

        final JsonArray items = JsonFactory.newArrayBuilder(Arrays.asList(thing1.toJson(), thing2.toJson())).build();
        final SearchResult searchResult = SearchModelFactory.newSearchResult(items, 2);

        writeJson(modelDir.resolve(Paths.get("search-result.json")), searchResult);
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
