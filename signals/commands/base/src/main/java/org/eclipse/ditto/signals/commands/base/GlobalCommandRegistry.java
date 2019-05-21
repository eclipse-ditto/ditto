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

import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableCommand;
import org.eclipse.ditto.signals.base.AbstractAnnotationBasedJsonParsableFactory;
import org.eclipse.ditto.signals.base.AbstractGlobalJsonParsableRegistry;

/**
 * Contains all strategies to deserialize subclasses of {@link Command} from a combination of
 * {@link JsonObject} and {@link DittoHeaders}.
 */
@Immutable
public final class GlobalCommandRegistry
        extends AbstractGlobalJsonParsableRegistry<Command, JsonParsableCommand>
        implements CommandRegistry<Command> {

    private static final GlobalCommandRegistry INSTANCE = new GlobalCommandRegistry();

    private GlobalCommandRegistry() {
        super(Command.class, JsonParsableCommand.class, new CommandParsingStrategyFactory());
    }

    /**
     * Gets an instance of GlobalCommandRegistry.
     *
     * @return the instance.
     */
    public static GlobalCommandRegistry getInstance() {
        return INSTANCE;
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
        return jsonObject.getValue(Command.JsonFields.ID);
    }

    /**
     * Contains all strategies to deserialize {@link Command} annotated with {@link JsonParsableCommand}
     * from a combination of {@link JsonObject} and {@link DittoHeaders}.
     */
    private static final class CommandParsingStrategyFactory
            extends AbstractAnnotationBasedJsonParsableFactory<Command, JsonParsableCommand> {

        private CommandParsingStrategyFactory() {}

        @Override
        protected String getV1FallbackKeyFor(final JsonParsableCommand annotation) {
            return annotation.name();
        }

        @Override
        protected String getKeyFor(final JsonParsableCommand annotation) {
            return annotation.typePrefix() + annotation.name();
        }

        @Override
        protected String getMethodNameFor(final JsonParsableCommand annotation) {
            return annotation.method();
        }

    }

}
