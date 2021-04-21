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
package org.eclipse.ditto.protocol.examples;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;
import org.eclipse.ditto.policies.model.PolicyException;
import org.eclipse.ditto.things.model.ThingException;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.signals.base.WithResource;
import org.eclipse.ditto.policies.model.signals.commands.PolicyErrorResponse;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThing;
import org.eclipse.ditto.things.model.signals.commands.modify.MergeThingResponse;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.PrettyPrintEmptyElementsWriter;
import com.eclipsesource.json.WriterConfig;

/**
 * Produces Ditto Protocol JSON example snippets wrapped in Markdown syntax:
 * <pre>{@code   ## <title>
 * ```json
 * { ... json ... }
 * ```
 * }
 * </pre>
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr") // this is a command line tool, System.out is ok
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

    /**
     * Generated examples that can be used for e.g. the ditto-documentation. The producer will therefore create
     * .md-files for the different examples. Internally it will use {@link JsonExamplesProducer} for creating the
     * example json.
     *
     * @param args expected one argument: the path where the examples should be stored. The last part of the path should
     * represent the type of the produced example (only type "markdown" is supported). Example:
     * "generated-examples/markdown"
     */
    public static void main(final String... args) throws IOException {
        if (args.length != 1) {
            System.err.println("Exactly 1 argument required: <target-folder>/<file-type(only \"markdown\" supported)>");
            System.exit(-1);
        }
        final String[] writePath = args[0].split("/");
        run(args, new PublicJsonExamplesProducer(writePath[writePath.length - 1]));
    }

    private Optional<Method> findMatchingToAdaptableMethod(final Class<?> parameterClass) {
        return toAdaptableMethods.stream()
                .filter(m -> m.getParameters().length == 1)
                .filter(m -> m.getParameters()[0].getType().isAssignableFrom(parameterClass))
                .findFirst();
    }

    private static Jsonifiable.WithPredicate<JsonObject, JsonField> wrapExceptionInErrorResponse(
            final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable) {

        if (jsonifiable instanceof DittoRuntimeException) {
            if (jsonifiable instanceof ThingException) {
                return ThingErrorResponse.of((DittoRuntimeException) jsonifiable);
            } else if (jsonifiable instanceof PolicyException) {
                return PolicyErrorResponse.of((DittoRuntimeException) jsonifiable);
            }

            System.out.println(jsonifiable.getClass().getName() + " is neither ThingException nor PolicyException.");
            return ThingErrorResponse.of((DittoRuntimeException) jsonifiable);
        } else {
            return jsonifiable;
        }
    }

    private Optional<Adaptable> toAdaptable(final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable) {
        final Jsonifiable.WithPredicate<JsonObject, JsonField> source = wrapExceptionInErrorResponse(jsonifiable);
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
            final Class<?> theClass, final List<DittoHeaderDefinition> excludedHeaders) {
        if (adaptable.isPresent()) {
            final String jsonString = adaptable.map(adaptable1 -> {
                        final JsonObject adaptableJson = ProtocolFactory.wrapAsJsonifiableAdaptable(adaptable1).toJson();
                        final JsonObject nonRegularFieldsFromValueRemoved = removeNonRegularFieldsFromValue(adaptableJson);
                        final JsonObject headersExcluded = excludeHeaders(nonRegularFieldsFromValueRemoved, excludedHeaders);
                        return headersExcluded.toString();
                    }
            ).get();

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

    private JsonObject removeNonRegularFieldsFromValue(final JsonObject initialObject) {
        return initialObject.getValue(Payload.JsonFields.VALUE)
                .filter(v -> !v.isNull())
                .filter(JsonValue::isObject)
                .map(JsonValue::asObject)
                .map(value -> value.stream()
                        // can't test for FieldType.REGULAR since some adaptables will already have forgotten the
                        // field types for fields that should be shown. Thus we are safer this way.
                        .filter(field -> !(field.isMarkedAs(FieldType.SPECIAL) || field.isMarkedAs(FieldType.HIDDEN)))
                        .collect(JsonCollectors.fieldsToObject()))
                .map(valueWithOnlyRegularFields -> initialObject.toBuilder()
                        .set(Payload.JsonFields.VALUE, valueWithOnlyRegularFields)
                        .build())
                .orElse(initialObject);
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
        } else if (jsonifiable instanceof MergeThing && !((MergeThing) jsonifiable).getValue().isNull()) {
            return "Merge thing command at " + ((WithResource) jsonifiable).getResourcePath();
        } else if (jsonifiable instanceof MergeThing && ((MergeThing) jsonifiable).getValue().isNull()) {
            return "Merge thing command deleting " + ((WithResource) jsonifiable).getResourcePath();
        } else if (jsonifiable instanceof MergeThingResponse) {
            return "Merge thing command response at " + ((WithResource) jsonifiable).getResourcePath();
        } else if (jsonifiable instanceof ThingMerged) {
            return "Thing merged event at " + ((WithResource) jsonifiable).getResourcePath();
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
    protected void writeJson(final Path path, final Jsonifiable.WithPredicate<JsonObject, JsonField> jsonifiable,
            final JsonSchemaVersion schemaVersion, final List<DittoHeaderDefinition> excludedHeaders) {

        final Path dowikPath = resolveFileName(path, jsonifiable);
        final String dowikTitle = resolveTitle(jsonifiable);
        writeJson(dowikPath, dowikTitle, toAdaptable(jsonifiable), jsonifiable.getClass(), excludedHeaders);
    }

}
