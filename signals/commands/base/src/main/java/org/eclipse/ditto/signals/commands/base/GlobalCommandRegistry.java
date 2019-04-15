/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.signals.commands.base;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.atteo.classindex.ClassIndex;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableCommand;
import org.eclipse.ditto.signals.base.AbstractJsonParsableRegistry;
import org.eclipse.ditto.signals.base.DeserializationStrategyNotFoundError;
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.base.JsonTypeNotParsableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains all strategies to deserialize subclasses of {@link Command} from a combination of
 * {@link JsonObject} and {@link DittoHeaders}.
 */
@Immutable
public final class GlobalCommandRegistry extends AbstractJsonParsableRegistry<Command>
        implements CommandRegistry<Command> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalCommandRegistry.class);

    private static final GlobalCommandRegistry INSTANCE = new GlobalCommandRegistry(new JsonParsableCommandRegistry());

    private final Map<String, String> nameToTypePrefixMap;

    private GlobalCommandRegistry(final JsonParsableCommandRegistry jsonParsableCommandRegistry) {
        super(jsonParsableCommandRegistry.getParseRegistries());
        nameToTypePrefixMap =
                Collections.unmodifiableMap(new HashMap<>(jsonParsableCommandRegistry.getNameToTypePrefixMap()));
    }

    /**
     * Gets an instance of GlobalCommandRegistry.
     *
     * @return the instance.
     */
    public static GlobalCommandRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Contains all strategies to deserialize {@link Command} annotated with {@link JsonParsableCommand}
     * from a combination of {@link JsonObject} and {@link DittoHeaders}.
     */
    private static final class JsonParsableCommandRegistry {

        private static final Class<?> JSON_OBJECT_PARAMETER = JsonObject.class;
        private static final Class<?> DITTO_HEADERS_PARAMETER = DittoHeaders.class;

        private final Map<String, JsonParsable<Command>> parseRegistries = new HashMap<>();
        private final Map<String, String> nameToTypePrefixMap = new HashMap<>();

        private JsonParsableCommandRegistry() {
            final Iterable<Class<?>> jsonParsableCommands = ClassIndex.getAnnotated(JsonParsableCommand.class);
            jsonParsableCommands.forEach(parsableCommand -> {
                final JsonParsableCommand fromJsonAnnotation = parsableCommand.getAnnotation(JsonParsableCommand.class);
                try {
                    final String methodName = fromJsonAnnotation.method();
                    final String typePrefix = fromJsonAnnotation.typePrefix();
                    final String name = fromJsonAnnotation.name();
                    final Method method =
                            parsableCommand.getMethod(methodName, JSON_OBJECT_PARAMETER, DITTO_HEADERS_PARAMETER);

                    appendMethodToParseStrategies(typePrefix, name, method);
                } catch (final NoSuchMethodException e) {
                    throw new DeserializationStrategyNotFoundError(parsableCommand, e);
                }
            });
        }

        private void appendMethodToParseStrategies(final String typePrefix, final String name, final Method method) {
            final String type = typePrefix + name;
            nameToTypePrefixMap.put(name, typePrefix);
            parseRegistries.put(type, (jsonObject, dittoHeaders) -> {
                try {
                    return (Command) method.invoke(null, jsonObject, dittoHeaders);
                } catch (final IllegalAccessException | InvocationTargetException e) {
                    LOGGER.error("Exception occurred during parsing of json.", e);
                    throw JsonTypeNotParsableException.newBuilder(type, getClass().getSimpleName())
                            .dittoHeaders(dittoHeaders)
                            .cause(e)
                            .build();
                }
            });
        }

        private Map<String, JsonParsable<Command>> getParseRegistries() {
            return new HashMap<>(parseRegistries);
        }

        private Map<String, String> getNameToTypePrefixMap() {
            return new HashMap<>(nameToTypePrefixMap);
        }

    }

    @Override
    protected String resolveType(final JsonObject jsonObject) {
        /*
         * If type was not present (was included in V2) take "event" instead and transform to V2 format.
         * Fail if "event" also is not present.
         */
        return jsonObject.getValue(Command.JsonFields.TYPE)
                .orElseGet(() -> extractTypeV1(jsonObject)
                        .orElseThrow(() -> new JsonMissingFieldException(Command.JsonFields.TYPE)));
    }

    @SuppressWarnings({"squid:CallToDeprecatedMethod"})
    private Optional<String> extractTypeV1(final JsonObject jsonObject) {
        return jsonObject.getValue(Command.JsonFields.ID).map(event -> nameToTypePrefixMap.get(event) + event);
    }

}
