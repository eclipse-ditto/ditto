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

import org.eclipse.ditto.base.model.json.JsonParsableCommandResponse;
import org.eclipse.ditto.base.model.signals.AbstractAnnotationBasedJsonParsableFactory;
import org.eclipse.ditto.base.model.signals.AbstractGlobalJsonParsableRegistry;
import org.eclipse.ditto.json.JsonObject;

/**
 * Contains all strategies to deserialize subclasses of {@link Command} from a combination of
 * {@link org.eclipse.ditto.json.JsonObject} and {@link org.eclipse.ditto.base.model.headers.DittoHeaders}.
 */
@Immutable
public final class GlobalCommandResponseRegistry
        extends AbstractGlobalJsonParsableRegistry<CommandResponse<?>, JsonParsableCommandResponse>
        implements CommandResponseRegistry<CommandResponse<?>> {

    private static final GlobalCommandResponseRegistry INSTANCE = new GlobalCommandResponseRegistry();

    private GlobalCommandResponseRegistry() {
        super(CommandResponse.class, JsonParsableCommandResponse.class, new CommandResponseParsingStrategyFactory());
    }

    /**
     * Gets an INSTANCE of GlobalCommandResponseRegistry.
     *
     * @return the INSTANCE of GlobalCommandResponseRegistry.
     */
    public static GlobalCommandResponseRegistry getInstance() {
        return INSTANCE;
    }

    @Override
    protected String resolveType(final JsonObject jsonObject) {
        return jsonObject.getValueOrThrow(CommandResponse.JsonFields.TYPE);
    }

    /**
     * Contains all strategies to deserialize {@link CommandResponse} annotated with
     * {@link org.eclipse.ditto.base.model.json.JsonParsableCommandResponse}
     * from a combination of {@link org.eclipse.ditto.json.JsonObject} and
     * {@link org.eclipse.ditto.base.model.headers.DittoHeaders}.
     */
    private static final class CommandResponseParsingStrategyFactory
            extends AbstractAnnotationBasedJsonParsableFactory<CommandResponse<?>, JsonParsableCommandResponse> {

        private CommandResponseParsingStrategyFactory() {}

        @Override
        protected String getKeyFor(final JsonParsableCommandResponse annotation) {
            return annotation.type();
        }

        @Override
        protected String getMethodNameFor(final JsonParsableCommandResponse annotation) {
            return annotation.method();
        }
    }

}
