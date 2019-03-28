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
import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.atteo.classindex.ClassIndex;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableCommandResponse;
import org.eclipse.ditto.signals.base.AbstractJsonParsableRegistry;
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.base.JsonTypeNotParsableException;

/**
 * Contains all strategies to deserialize subclasses of {@link Command} from a combination of
 * {@link JsonObject} and {@link DittoHeaders}.
 */
@Immutable
public final class GlobalCommandResponseRegistry extends AbstractJsonParsableRegistry<CommandResponse>
        implements CommandResponseRegistry<CommandResponse> {

    private static final GlobalCommandResponseRegistry INSTANCE =
            new GlobalCommandResponseRegistry(new JsonParsableCommandResponseRegistry());

    private GlobalCommandResponseRegistry(final JsonParsableCommandResponseRegistry jsonParsableRegistry) {
        super(jsonParsableRegistry.getParseRegistries());
    }

    /**
     * Gets an INSTANCE of GlobalCommandResponseRegistry.
     *
     * @return the INSTANCE of GlobalCommandResponseRegistry.
     */
    public static GlobalCommandResponseRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Contains all strategies to deserialize {@link CommandResponse} annotated with {@link JsonParsableCommandResponse}
     * from a combination of {@link JsonObject} and {@link DittoHeaders}.
     */
    private static final class JsonParsableCommandResponseRegistry {

        private static final Class<?> JSON_OBJECT_PARAMETER = JsonObject.class;
        private static final Class<?> DITTO_HEADERS_PARAMETER = DittoHeaders.class;

        private final Map<String, JsonParsable<CommandResponse>> parseRegistries = new HashMap<>();

        private JsonParsableCommandResponseRegistry() {
            final Iterable<Class<?>> jsonParsableCommandResponses =
                    ClassIndex.getAnnotated(JsonParsableCommandResponse.class);
            jsonParsableCommandResponses.forEach(parsableCommandResponse -> {
                final JsonParsableCommandResponse fromJsonAnnotation =
                        parsableCommandResponse.getAnnotation(JsonParsableCommandResponse.class);
                try {
                    final String methodName = fromJsonAnnotation.method();
                    final String type = fromJsonAnnotation.type();
                    final Method method = parsableCommandResponse
                            .getMethod(methodName, JSON_OBJECT_PARAMETER, DITTO_HEADERS_PARAMETER);

                    appendMethodToParseStrategies(type, method);
                } catch (final NoSuchMethodException e) {
                    final String message = String.format("Could not create deserializing strategy for '%s'.",
                            parsableCommandResponse.getName());
                    throw new Error(message, e);
                }
            });
        }

        private void appendMethodToParseStrategies(final String type, final Method method) {
            parseRegistries.put(type, (jsonObject, dittoHeaders) -> {
                try {
                    return (CommandResponse) method.invoke(null, jsonObject, dittoHeaders);
                } catch (final IllegalAccessException | InvocationTargetException e) {
                    throw JsonTypeNotParsableException.newBuilder(type, getClass().getSimpleName())
                            .dittoHeaders(dittoHeaders).build();
                }
            });
        }

        private Map<String, JsonParsable<CommandResponse>> getParseRegistries() {
            return new HashMap<>(parseRegistries);
        }
    }


    @Override
    protected String resolveType(final JsonObject jsonObject) {
        return jsonObject.getValueOrThrow(CommandResponse.JsonFields.TYPE);
    }
}
