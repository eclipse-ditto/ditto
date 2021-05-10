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
package org.eclipse.ditto.base.model.signals.events;

import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.JsonParsableEvent;
import org.eclipse.ditto.base.model.signals.AbstractAnnotationBasedJsonParsableFactory;
import org.eclipse.ditto.base.model.signals.AbstractGlobalJsonParsableRegistry;
import org.eclipse.ditto.base.model.signals.JsonParsable;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;

/**
 * Contains all strategies to deserialize subclasses of {@link Event} from a combination of
 * {@link org.eclipse.ditto.json.JsonObject} and {@link org.eclipse.ditto.base.model.headers.DittoHeaders}.
 *
 * @param <T> the event's type.
 */
@Immutable
public final class GlobalEventRegistry<T extends Event<?>>
        extends AbstractGlobalJsonParsableRegistry<T, JsonParsableEvent> implements EventRegistry<T> {

    private static final GlobalEventRegistry<?> INSTANCE = new GlobalEventRegistry<>();

    private GlobalEventRegistry() {
        super(Event.class, JsonParsableEvent.class, new EventParsingStrategyFactory<>());
    }

    /**
     * Gets an instance of GlobalEventRegistry.
     *
     * @param <T> the event's type.
     * @return the instance of GlobalEventRegistry.
     */
    public static <T extends Event<?>> GlobalEventRegistry<T> getInstance() {
        return (GlobalEventRegistry<T>) INSTANCE;
    }

    /**
     * Creates a new instance of {@link CustomizedGlobalEventRegistry} containing the given parse strategies.
     * Should entries already exist they will be replaced by the new entries.
     *
     * @param parseStrategies The new strategies.
     * @return A Registry containing the merged strategies.
     */
    public CustomizedGlobalEventRegistry<T> customize(final Map<String, JsonParsable<T>> parseStrategies) {
        return new CustomizedGlobalEventRegistry<>(this, parseStrategies);
    }

    @Override
    protected String resolveType(final JsonObject jsonObject) {
        return jsonObject.getValue(Event.JsonFields.TYPE)
                .orElseThrow(() -> new JsonMissingFieldException(Event.JsonFields.TYPE));
    }

    /**
     * Contains all strategies to deserialize {@link Event} annotated with {@link org.eclipse.ditto.base.model.json.JsonParsableEvent}
     * from a combination of {@link org.eclipse.ditto.json.JsonObject} and {@link org.eclipse.ditto.base.model.headers.DittoHeaders}.
     */
    private static final class EventParsingStrategyFactory<T extends Event<?>> extends
            AbstractAnnotationBasedJsonParsableFactory<T, JsonParsableEvent> {

        private EventParsingStrategyFactory() {}

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
