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
package org.eclipse.ditto.signals.commands.batch;

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandRegistry;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandRegistry;

/**
 * A {@link org.eclipse.ditto.signals.commands.base.CommandRegistry} aware of all {@link BatchCommand}s.
 */
@Immutable
public final class BatchCommandRegistry extends AbstractCommandRegistry<BatchCommand> {

    private BatchCommandRegistry(final Map<String, JsonParsable<BatchCommand>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@link BatchCommandRegistry}.
     *
     * @param batchStepCommandRegistry the registry with supported commands for batch steps.
     * @return the command registry.
     */
    public static BatchCommandRegistry newInstance(final CommandRegistry<? extends Command> batchStepCommandRegistry) {
        requireNonNull(batchStepCommandRegistry);

        final Map<String, JsonParsable<BatchCommand>> parseStrategies = new HashMap<>();

        parseStrategies.put(ExecuteBatch.TYPE,
                (jo, headers) -> ExecuteBatch.fromJson(jo, headers, batchStepCommandRegistry));

        return new BatchCommandRegistry(parseStrategies);
    }

    @Override
    protected String getTypePrefix() {
        return BatchCommand.TYPE_PREFIX;
    }

}
