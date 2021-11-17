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
package org.eclipse.ditto.base.model.signals.commands;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.json.JsonParsableCommand;
import org.eclipse.ditto.base.model.signals.AbstractAnnotationBasedJsonParsableFactory;
import org.eclipse.ditto.base.model.signals.AbstractGlobalJsonParsableRegistry;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;

/**
 * Contains all strategies to deserialize subclasses of {@link Command} from a combination of
 * {@link org.eclipse.ditto.json.JsonObject} and {@link org.eclipse.ditto.base.model.headers.DittoHeaders}.
 */
@Immutable
public final class GlobalCommandRegistry
        extends AbstractGlobalJsonParsableRegistry<Command<?>, JsonParsableCommand>
        implements CommandRegistry<Command<?>> {

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
                .orElseThrow(() -> new JsonMissingFieldException(Command.JsonFields.TYPE));
    }

    /**
     * Contains all strategies to deserialize {@link Command} annotated with
     * {@link org.eclipse.ditto.base.model.json.JsonParsableCommand}
     * from a combination of {@link org.eclipse.ditto.json.JsonObject} and
     * {@link org.eclipse.ditto.base.model.headers.DittoHeaders}.
     */
    private static final class CommandParsingStrategyFactory
            extends AbstractAnnotationBasedJsonParsableFactory<Command<?>, JsonParsableCommand> {

        private CommandParsingStrategyFactory() {}

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
