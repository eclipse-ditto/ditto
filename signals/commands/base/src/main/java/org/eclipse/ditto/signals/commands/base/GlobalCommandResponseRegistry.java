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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonParsableCommandResponse;
import org.eclipse.ditto.signals.base.AbstractAnnotationBasedJsonParsableBuilder;
import org.eclipse.ditto.signals.base.AbstractGlobalJsonParsableRegistry;

/**
 * Contains all strategies to deserialize subclasses of {@link Command} from a combination of
 * {@link JsonObject} and {@link DittoHeaders}.
 */
@Immutable
public final class GlobalCommandResponseRegistry
        extends AbstractGlobalJsonParsableRegistry<CommandResponse, JsonParsableCommandResponse>
        implements CommandResponseRegistry<CommandResponse> {

    private static final GlobalCommandResponseRegistry INSTANCE = new GlobalCommandResponseRegistry();

    private GlobalCommandResponseRegistry() {
        super(CommandResponse.class, JsonParsableCommandResponse.class, new CommandResponseParsingStrategyBuilder());
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
     * Contains all strategies to deserialize {@link CommandResponse} annotated with {@link JsonParsableCommandResponse}
     * from a combination of {@link JsonObject} and {@link DittoHeaders}.
     */
    private static final class CommandResponseParsingStrategyBuilder
            extends AbstractAnnotationBasedJsonParsableBuilder<CommandResponse, JsonParsableCommandResponse> {

        private CommandResponseParsingStrategyBuilder() {}

        @Override
        protected String getV1FallbackKeyFor(final JsonParsableCommandResponse annotation) {
            return annotation.type();
        }

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
