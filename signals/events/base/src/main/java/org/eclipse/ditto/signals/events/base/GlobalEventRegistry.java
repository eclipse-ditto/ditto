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
package org.eclipse.ditto.signals.events.base;

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
import org.eclipse.ditto.model.base.json.JsonParsableEvent;
import org.eclipse.ditto.signals.base.AbstractJsonParsableRegistry;
import org.eclipse.ditto.signals.base.DeserializationStrategyNotFoundError;
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.base.JsonTypeNotParsableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains all strategies to deserialize subclasses of {@link Event} from a combination of
 * {@link JsonObject} and {@link DittoHeaders}.
 */
@Immutable
public final class GlobalEventRegistry extends AbstractJsonParsableRegistry<Event> implements EventRegistry<Event> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalEventRegistry.class);

    private static final GlobalEventRegistry INSTANCE = new GlobalEventRegistry(new JsonParsableEventRegistry());

    private final Map<String, String> nameToTypePrefixMap;

    private GlobalEventRegistry(final JsonParsableEventRegistry jsonParsableEventRegistry) {
        super(jsonParsableEventRegistry.getParseRegistries());
        nameToTypePrefixMap =
                Collections.unmodifiableMap(new HashMap<>(jsonParsableEventRegistry.getNameToTypePrefixMap()));
    }

    /**
     * Gets an instance of GlobalEventRegistry.
     *
     * @return the instance of GlobalEventRegistry.
     */
    public static GlobalEventRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Creates a new instance of {@link CustomizedGlobalEventRegistry} containing the given parse strategies.
     * Should entries already exist they will be replaced by the new entries.
     *
     * @param parseStrategies The new strategies.
     * @return A Registry containing the merged strategies.
     */
    public CustomizedGlobalEventRegistry customize(final Map<String, JsonParsable<Event>> parseStrategies) {
        return new CustomizedGlobalEventRegistry(this, parseStrategies);
    }

    @Override
    protected String resolveType(final JsonObject jsonObject) {
        /*
         * If type was not present (was included in V2) take "event" instead and transform to V2 format.
         * Fail if "event" also is not present.
         */
        return jsonObject.getValue(Event.JsonFields.TYPE)
                .orElseGet(() -> extractTypeV1(jsonObject)
                        .orElseThrow(() -> new JsonMissingFieldException(Event.JsonFields.TYPE)));
    }

    @SuppressWarnings({"squid:CallToDeprecatedMethod", "deprecation"})
    private Optional<String> extractTypeV1(final JsonObject jsonObject) {
        return jsonObject.getValue(Event.JsonFields.ID).map(event -> nameToTypePrefixMap.get(event) + event);
    }

    /**
     * Contains all strategies to deserialize {@link Event} annotated with {@link JsonParsableEvent}
     * from a combination of {@link JsonObject} and {@link DittoHeaders}.
     */
    private static final class JsonParsableEventRegistry {

        private static final Class<?> JSON_OBJECT_PARAMETER = JsonObject.class;
        private static final Class<?> DITTO_HEADERS_PARAMETER = DittoHeaders.class;

        private final Map<String, JsonParsable<Event>> parseRegistries = new HashMap<>();
        private final Map<String, String> nameToTypePrefixMap = new HashMap<>();

        private JsonParsableEventRegistry() {
            final Iterable<Class<?>> jsonParsableEvents = ClassIndex.getAnnotated(JsonParsableEvent.class);
            jsonParsableEvents.forEach(parsableEvent -> {
                final JsonParsableEvent fromJsonAnnotation = parsableEvent.getAnnotation(JsonParsableEvent.class);
                try {
                    final String methodName = fromJsonAnnotation.method();
                    final String typePrefix = fromJsonAnnotation.typePrefix();
                    final String name = fromJsonAnnotation.name();
                    final Method method =
                            parsableEvent.getMethod(methodName, JSON_OBJECT_PARAMETER, DITTO_HEADERS_PARAMETER);

                    appendMethodToParseStrategies(typePrefix, name, method);
                } catch (final NoSuchMethodException e) {
                    throw new DeserializationStrategyNotFoundError(parsableEvent, e);
                }
            });
        }

        private void appendMethodToParseStrategies(final String typePrefix, final String name, final Method method) {
            final String type = typePrefix + name;
            final JsonParsable<Event> deserializationMethod = (jsonObject, dittoHeaders) -> {
                try {
                    return (Event) method.invoke(null, jsonObject, dittoHeaders);
                } catch (final IllegalAccessException | InvocationTargetException e) {
                    LOGGER.error("Exception occurred during parsing of json.", e);
                    throw JsonTypeNotParsableException.newBuilder(type, getClass().getSimpleName())
                            .dittoHeaders(dittoHeaders)
                            .cause(e)
                            .build();
                }
            };
            nameToTypePrefixMap.put(name, typePrefix);
            parseRegistries.put(type, deserializationMethod);
        }

        private Map<String, JsonParsable<Event>> getParseRegistries() {
            return new HashMap<>(parseRegistries);
        }

        private Map<String, String> getNameToTypePrefixMap() {
            return new HashMap<>(nameToTypePrefixMap);
        }

    }

}
