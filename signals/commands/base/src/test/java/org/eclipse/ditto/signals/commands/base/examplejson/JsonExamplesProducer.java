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
package org.eclipse.ditto.signals.commands.base.examplejson;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelectorInvalidException;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.json.JsonPointerInvalidException;
import org.eclipse.ditto.model.base.exceptions.DittoJsonException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationFailedException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationProviderUnavailableException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayBadGatewayException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayInternalErrorException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayMethodNotAllowedException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayServiceTimeoutException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayServiceTooManyRequestsException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayServiceUnavailableException;


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

        final GatewayBadGatewayException gatewayBadGatewayException = GatewayBadGatewayException.newBuilder().build();
        writeJson(exceptionsDir.resolve(Paths.get("gatewayBadGatewayException.json")), gatewayBadGatewayException);

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
