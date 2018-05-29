/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.events.batch;

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.base.CommandRegistry;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseRegistry;
import org.eclipse.ditto.signals.events.base.AbstractEventRegistry;
import org.eclipse.ditto.signals.events.base.EventRegistry;

/**
 * An {@link EventRegistry} aware of all {@link BatchEvent}s.
 */
@Immutable
public final class BatchEventRegistry extends AbstractEventRegistry<BatchEvent> {

    private BatchEventRegistry(final Map<String, JsonParsable<BatchEvent>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@link BatchEventRegistry}.
     *
     * @param batchStepCommandRegistry the registry with supported commands for batch steps.
     * @param batchStepCommandResponseRegistry the registry with supported command responses for batch steps.
     * @return the event registry.
     */
    public static BatchEventRegistry newInstance(final CommandRegistry<? extends Command> batchStepCommandRegistry,
            final CommandResponseRegistry<? extends CommandResponse> batchStepCommandResponseRegistry) {
        requireNonNull(batchStepCommandRegistry);
        requireNonNull(batchStepCommandResponseRegistry);

        final Map<String, JsonParsable<BatchEvent>> parseStrategies = new HashMap<>();

        parseStrategies.put(BatchExecutionFinished.TYPE,
                (jo, headers) -> BatchExecutionFinished.fromJson(jo, headers, batchStepCommandResponseRegistry));
        parseStrategies.put(BatchExecutionStarted.TYPE,
                (jo, headers) -> BatchExecutionStarted.fromJson(jo, headers, batchStepCommandRegistry));
        parseStrategies.put(BatchCommandExecuted.TYPE,
                (jo, headers) -> BatchCommandExecuted.fromJson(jo, headers, batchStepCommandResponseRegistry));

        return new BatchEventRegistry(parseStrategies);
    }

}
