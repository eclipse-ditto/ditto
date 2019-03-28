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

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.base.AbstractCommandResponseRegistry;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.base.CommandResponseRegistry;

/**
 * A {@link CommandResponseRegistry} for all command responses which should be
 * known to Batches.
 */
@Immutable
public final class BatchCommandResponseRegistry extends AbstractCommandResponseRegistry<CommandResponse> {

    private BatchCommandResponseRegistry(final Map<String, JsonParsable<CommandResponse>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * @return the commandResponse registry.
     */
    public static BatchCommandResponseRegistry newInstance() {
        final Map<String, JsonParsable<CommandResponse>> parseStrategies = new HashMap<>();

        parseStrategies.put(ExecuteBatchResponse.TYPE, ExecuteBatchResponse::fromJson);

        return new BatchCommandResponseRegistry(parseStrategies);
    }

}
