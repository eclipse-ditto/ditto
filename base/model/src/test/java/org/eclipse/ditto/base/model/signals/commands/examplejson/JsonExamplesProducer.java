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
package org.eclipse.ditto.base.model.signals.commands.examplejson;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayAuthenticationFailedException;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayAuthenticationProviderUnavailableException;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayServiceTooManyRequestsException;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelectorInvalidException;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointerInvalidException;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayMethodNotAllowedException;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayServiceTimeoutException;
import org.eclipse.ditto.base.model.signals.commands.exceptions.GatewayServiceUnavailableException;


public class JsonExamplesProducer {

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.empty();

    public static void main(final String... args) throws IOException {
        run(args, new JsonExamplesProducer());
    }

    private static void run(final String[] args, final JsonExamplesProducer producer) throws
            IOException {
        if (args.length != 1) {
            System.err.println("Exactly 1 argument required: the target folder in which to generate the JSON files");
            System.exit(-1);
        }
        producer.produce(Paths.get(args[0]));
    }

    private void produce(final Path rootPath) throws IOException {
        produceJsonExceptions(rootPath.resolve("json"));
        produceGatewayExceptions(rootPath.resolve("gateway"));
    }


    private void produceGatewayExceptions(final Path rootPath) throws IOException {
        final Path exceptionsDir = rootPath.resolve(Paths.get("errors"));
        Files.createDirectories(exceptionsDir);

        final GatewayAuthenticationFailedException gatewayAuthenticationFailedException =
                GatewayAuthenticationFailedException.newBuilder("devops authentication failed!").build();
        writeJson(exceptionsDir.resolve(Paths.get("gatewayAuthenticationFailedException.json")),
                gatewayAuthenticationFailedException);

        final GatewayAuthenticationProviderUnavailableException gatewayAuthenticationProviderUnavailableException =
                GatewayAuthenticationProviderUnavailableException.newBuilder().build();
        writeJson(exceptionsDir.resolve(Paths.get("gatewayAuthenticationProviderUnavailableException.json")),
                gatewayAuthenticationProviderUnavailableException);

        final GatewayInternalErrorException gatewayInternalErrorException =
                GatewayInternalErrorException.newBuilder().build();
        writeJson(exceptionsDir.resolve(Paths.get("gatewayInternalErrorException.json")),
                gatewayInternalErrorException);

        final GatewayMethodNotAllowedException gatewayMethodNotAllowedException =
                GatewayMethodNotAllowedException.newBuilder("POST").build();
        writeJson(exceptionsDir.resolve(Paths.get("gatewayMethodNotAllowedException.json")),
                gatewayMethodNotAllowedException);

        final GatewayServiceTimeoutException gatewayServiceTimeoutException =
                GatewayServiceTimeoutException.newBuilder().build();
        writeJson(exceptionsDir.resolve(Paths.get("gatewayServiceTimeoutException.json")),
                gatewayServiceTimeoutException);

        final GatewayServiceUnavailableException gatewayServiceUnavailableException =
                GatewayServiceUnavailableException.newBuilder().build();
        writeJson(exceptionsDir.resolve(Paths.get("gatewayServiceUnavailableException.json")),
                gatewayServiceUnavailableException);

        final GatewayServiceTooManyRequestsException gatewayServiceTooManyRequestsException =
                GatewayServiceTooManyRequestsException.newBuilder().build();
        writeJson(exceptionsDir.resolve(Paths.get("gatewayServiceTooManyRequestsException.json")),
                gatewayServiceTooManyRequestsException);
    }

    private void produceJsonExceptions(final Path rootPath) throws IOException {
        final Path exceptionsDir = rootPath.resolve(Paths.get("errors"));
        Files.createDirectories(exceptionsDir);

        final DittoJsonException jsonFieldSelectorInvalidException =
                new DittoJsonException(JsonFieldSelectorInvalidException.newBuilder().fieldSelector("foo(bar").build(),
                        DITTO_HEADERS);
        writeJson(exceptionsDir.resolve(Paths.get("jsonFieldSelectorInvalidException.json")),
                jsonFieldSelectorInvalidException);

        final DittoJsonException jsonPointerInvalidException = new DittoJsonException(
                JsonPointerInvalidException.newBuilder().jsonPointer("").build(), DITTO_HEADERS);
        writeJson(exceptionsDir.resolve(Paths.get("jsonPointerInvalidException.json")), jsonPointerInvalidException);

        final DittoJsonException jsonMissingFieldException = new DittoJsonException(
                JsonMissingFieldException.newBuilder().fieldName("attributes").build(), DITTO_HEADERS);
        writeJson(exceptionsDir.resolve(Paths.get("jsonMissingFieldException.json")), jsonMissingFieldException);

        final DittoJsonException jsonParseException = new DittoJsonException(
                JsonParseException.newBuilder().message("Could not read 'foo'").build(), DITTO_HEADERS);
        writeJson(exceptionsDir.resolve(Paths.get("jsonParseException.json")), jsonParseException);
    }

    private void writeJson(final Path path, final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable)
            throws IOException {
        writeJson(path, jsonifiable, JsonSchemaVersion.LATEST);
    }

    private void writeJson(final Path path, final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable,
            final JsonSchemaVersion schemaVersion) throws IOException {
        final String jsonString = jsonifiable.toJsonString(schemaVersion);
        System.out.println("Writing file: " + path.toAbsolutePath());
        Files.write(path, jsonString.getBytes());
    }
}
