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

import java.util.Map;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableEvent;
import org.eclipse.ditto.signals.base.AbstractAnnotationBasedJsonParsableFactory;
import org.eclipse.ditto.signals.base.AbstractGlobalJsonParsableRegistry;
import org.eclipse.ditto.signals.base.JsonParsable;

/**
 * Contains all strategies to deserialize subclasses of {@link Event} from a combination of
 * {@link JsonObject} and {@link DittoHeaders}.
 */
@Immutable
public final class GlobalEventRegistry
        extends AbstractGlobalJsonParsableRegistry<Event, JsonParsableEvent>
        implements EventRegistry<Event> {

    private static final GlobalEventRegistry INSTANCE = new GlobalEventRegistry();

    private GlobalEventRegistry() {
        super(Event.class, JsonParsableEvent.class, new EventParsingStrategyFactory());
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

    @SuppressWarnings({"squid:CallToDeprecatedMethod",})
    @Deprecated
    private Optional<String> extractTypeV1(final JsonObject jsonObject) {
        return jsonObject.getValue(Event.JsonFields.ID);
    }

    /**
     * Contains all strategies to deserialize {@link Event} annotated with {@link JsonParsableEvent}
     * from a combination of {@link JsonObject} and {@link DittoHeaders}.
     */
    private static final class EventParsingStrategyFactory extends
            AbstractAnnotationBasedJsonParsableFactory<Event, JsonParsableEvent> {

        private EventParsingStrategyFactory() {}

        @Override
        @Deprecated
        protected String getV1FallbackKeyFor(final JsonParsableEvent annotation) {
            return annotation.name();
        }

        @Override
        protected String getKeyFor(final JsonParsableEvent annotation) {
            return annotation.typePrefix() + annotation.name();
        }

        @Override
        protected String getMethodNameFor(final JsonParsableEvent annotation) {
            return annotation.method();
        }

    }

}
