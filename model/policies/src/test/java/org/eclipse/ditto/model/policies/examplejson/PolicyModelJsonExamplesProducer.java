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
package org.eclipse.ditto.model.policies.examplejson;

import static org.eclipse.ditto.model.base.auth.AuthorizationModelFactory.newAuthSubject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.model.policies.PoliciesModelFactory;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.model.policies.SubjectType;
import org.eclipse.ditto.model.policies.TestConstants;

public final class PolicyModelJsonExamplesProducer {

    public static void main(final String... args) throws IOException {
        if (args.length != 1) {
            System.err.println("Exactly 1 argument required: the target folder in which to generate the JSON files");
            System.exit(-1);
        }
        produce(Paths.get(args[0]));
    }

    private static void produce(final Path rootPath) throws IOException {
        final Collection<AuthorizationSubject> authorizationSubjects = new ArrayList<>();
        authorizationSubjects.add(newAuthSubject("the_firstSubject"));
        authorizationSubjects.add(newAuthSubject("the_anotherSubject"));
        final AuthorizationContext authContext = AuthorizationModelFactory.newAuthContext(authorizationSubjects);

        final Path authorizationDir = rootPath.resolve(Paths.get("authorization"));
        Files.createDirectories(authorizationDir);
        writeJson(authorizationDir.resolve(Paths.get("authorizationContext.json")), authContext);

        producePolicyModel(rootPath);
    }

    private static void producePolicyModel(final Path rootPath) throws IOException {
        final Path modelDir = rootPath.resolve(Paths.get("model"));
        Files.createDirectories(modelDir);

        final String policyId = "org.eclipse.ditto.example:the_thingId";

        final Policy policy = PoliciesModelFactory.newPolicyBuilder(policyId)
                .forLabel("EndUser")
                .setSubject(SubjectIssuer.GOOGLE, UUID.randomUUID().toString(),
                        SubjectType.newInstance("yourTypeDescription"))
                .setGrantedPermissions("thing", "/",
                        TestConstants.Policy.PERMISSION_READ,
                        TestConstants.Policy.PERMISSION_WRITE)
                .setRevokedPermissions("thing", "/attributes",
                        TestConstants.Policy.PERMISSION_WRITE)
                .forLabel("Support")
                .setRevokedPermissions("thing", "/features",
                        TestConstants.Policy.PERMISSION_READ,
                        TestConstants.Policy.PERMISSION_WRITE)
                .build();
        writeJson(modelDir.resolve(Paths.get("policy.json")), policy);
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
