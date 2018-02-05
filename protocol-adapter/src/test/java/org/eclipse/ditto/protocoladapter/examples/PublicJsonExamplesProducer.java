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
package org.eclipse.ditto.protocoladapter.examples;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.ProtocolFactory;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.PrettyPrintEmptyElementsWriter;
import com.eclipsesource.json.WriterConfig;

/**
 * Produces Ditto Protocol JSON example snippets wrapped in Markdown syntax:
 <pre>{@code   ## <title>
```json
   { ... json ... }
```
   }
</pre>
 *
 */
public final class PublicJsonExamplesProducer extends JsonExamplesProducer {

    private static final DittoProtocolAdapter PROTOCOL_ADAPTER = DittoProtocolAdapter.newInstance();
    private static final String TO_ADAPTABLE = "toAdaptable";
    private static final WriterConfig CUSTOM_PRETTY_PRINT = PrettyPrintEmptyElementsWriter.indentWithSpaces(2);

    private final String h2Begin;
    private final String h2End;
    private final String codeJsonBegin;
    private final String codeEnd;
    private final String newLine;
    private final String fileExtension;

    private final List<Method> toAdaptableMethods;

    private PublicJsonExamplesProducer(final String markdownType) {
        if (markdownType.equals("markdown")) {
            h2Begin = "## ";
            h2End = "\n";
            codeJsonBegin = "```json";
            codeEnd = "```";
            newLine = "\n";
            fileExtension = ".md";
        } else {
            throw new IllegalArgumentException("Unknown markdownType: " + markdownType);
        }
        toAdaptableMethods = Arrays.stream(PROTOCOL_ADAPTER.getClass().getMethods())
                .filter(m -> TO_ADAPTABLE.equals(m.getName())).collect(Collectors.toList());
    }

    public static void main(final String... args) throws IOException {
        if (args.length != 1) {
            System.err.println("Exactly 1 argument required: the target folder in which to generate the JSON files");
            System.exit(-1);
        }
        final String[] writePath = args[0].split("/");
        run(args, new PublicJsonExamplesProducer(writePath[writePath.length - 1]));
    }

    private Optional<Method> findMatchingToAdaptableMethod(final Class parameterClass) {
        return toAdaptableMethods.stream()
                .filter(m -> m.getParameters().length == 1)
                .filter(m -> m.getParameters()[0].getType().isAssignableFrom(parameterClass))
                .findFirst();
    }

    private static Jsonifiable.WithPredicate<JsonObject, JsonField> wrapExceptionInThingErrorResponse(
            final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable) {

        if (jsonifiable instanceof DittoRuntimeException) {
            return ThingErrorResponse.of((DittoRuntimeException) jsonifiable);
        } else {
            return jsonifiable;
        }
    }

    private Optional<Adaptable> toAdaptable(final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable) {
        final Jsonifiable.WithPredicate<JsonObject, JsonField> source = wrapExceptionInThingErrorResponse(jsonifiable);
        return findMatchingToAdaptableMethod(source.getClass()).flatMap(m -> invokeToAdaptable(m, source));
    }

    private static Optional<Adaptable> invokeToAdaptable(final Method toAdaptable,
            final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable) {

        try {
            return Optional.ofNullable((Adaptable) toAdaptable.invoke(PROTOCOL_ADAPTER, jsonifiable));
        } catch (IllegalAccessException | InvocationTargetException e) {
            System.err.println("error in invokeToAdaptable: " + e.getCause().getClass().getSimpleName() + ": " +
                    e.getCause().getMessage());
            return Optional.empty();
        }
    }

    private void writeJson(final Path path, final String title, final Optional<Adaptable> adaptable,
            final Class theClass) {

        if (adaptable.isPresent()) {
            final String jsonString =
                    adaptable.map(adaptable1 -> ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable1)
                            .toJsonString()).get();

            try {
                final String markdown = wrapCodeSnippet(title, jsonString);
                Files.write(path, markdown.getBytes());
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            System.out.println("Adaptable not found for class: " + theClass);
        }
    }

    private String wrapCodeSnippet(final String title, final String jsonString) {
        final StringBuilder markdown = new StringBuilder();
        // ===== <title> =====
        markdown.append(h2Begin).append(title).append(h2End).append(newLine);
        // <code javascript>
        markdown.append(codeJsonBegin).append(newLine);
        // { ... }
        markdown.append(Json.parse(jsonString).toString(CUSTOM_PRETTY_PRINT)).append(newLine);
        // </code>
        markdown.append(codeEnd).append(newLine);
        return markdown.toString();
    }

    private static String resolveTitle(final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable) {
        if (jsonifiable instanceof DittoRuntimeException) {
            // use the error code for exception
            return ((DittoRuntimeException) jsonifiable).getErrorCode();
        } else {
            // by default use class name
            return jsonifiable.getClass().getSimpleName();
        }
    }

    private Path resolveFileName(final Path path, final Jsonifiable.WithPredicate<JsonObject, JsonField>
            jsonifiable) {
        if (jsonifiable instanceof DittoRuntimeException) {
            // use the error code for exception
            final String filename =
                    ((DittoRuntimeException) jsonifiable).getErrorCode().replace(':', '_').replace('.', '_');
            return path.resolveSibling(filename + fileExtension);
        } else {
            // by default just lowercase and replace .json with .txt
            return path.resolveSibling(path.getFileName().toString().toLowerCase().replace(".json", fileExtension));
        }
    }

    @Override
    protected void writeJson(final Path path, final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable) {
        final Path dowikPath = resolveFileName(path, jsonifiable);
        final String dowikTitle = resolveTitle(jsonifiable);
        writeJson(dowikPath, dowikTitle, toAdaptable(jsonifiable), jsonifiable.getClass());
    }

    @Override
    protected void writeJson(final Path path, final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable,
            final JsonSchemaVersion schemaVersion) throws IOException {

        final Path dowikPath = resolveFileName(path, jsonifiable);
        final String dowikTitle = resolveTitle(jsonifiable);
        writeJson(dowikPath, dowikTitle, toAdaptable(jsonifiable), jsonifiable.getClass());
    }

}
