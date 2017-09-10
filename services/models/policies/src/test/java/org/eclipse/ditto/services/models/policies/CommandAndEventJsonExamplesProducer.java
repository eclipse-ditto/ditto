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
package org.eclipse.ditto.services.models.policies;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyResponse;


public final class CommandAndEventJsonExamplesProducer {

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

        final SudoRetrievePolicy sudoRetrievePolicy =
                SudoRetrievePolicy.of(TestConstants.Policy.POLICY_ID, TestConstants.EMPTY_HEADERS);
        writeJson(sudoCommandsDir.resolve(Paths.get("sudoRetrievePolicy.json")), sudoRetrievePolicy);

        final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                SudoRetrievePolicyResponse.of(TestConstants.Policy.POLICY_ID, TestConstants.Policy.POLICY,
                        TestConstants.EMPTY_HEADERS);
        writeJson(sudoCommandsDir.resolve(Paths.get("sudoRetrievePolicyResponse.json")), sudoRetrievePolicyResponse);
    }

    private static void writeJson(final Path path, final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable)
            throws IOException {
        final String jsonString = jsonifiable.toJsonString(FieldType.regularOrSpecial());
        System.out.println("Writing file: " + path.toAbsolutePath());
        Files.write(path, jsonString.getBytes());
    }

}
