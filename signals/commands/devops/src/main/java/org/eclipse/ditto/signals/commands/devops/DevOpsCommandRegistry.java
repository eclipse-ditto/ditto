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
package org.eclipse.ditto.signals.commands.devops;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.signals.base.AbstractJsonParsableRegistry;
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.base.JsonParsableRegistry;
import org.eclipse.ditto.signals.commands.devops.namespace.BlockNamespace;
import org.eclipse.ditto.signals.commands.devops.namespace.PurgeNamespace;
import org.eclipse.ditto.signals.commands.devops.namespace.QueryNamespaceEmptiness;
import org.eclipse.ditto.signals.commands.devops.namespace.ShutdownNamespace;

/**
 * A {@link JsonParsableRegistry} aware of all {@link DevOpsCommand}s.
 */
@Immutable
public final class DevOpsCommandRegistry extends AbstractJsonParsableRegistry<DevOpsCommand>
        implements JsonParsableRegistry<DevOpsCommand> {

    private DevOpsCommandRegistry(final Map<String, JsonParsable<DevOpsCommand>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code DevOpsCommandRegistry}.
     *
     * @return the registry.
     */
    public static DevOpsCommandRegistry newInstance() {
        final Map<String, JsonParsable<DevOpsCommand>> parseStrategies = new HashMap<>();

        parseStrategies.put(ChangeLogLevel.TYPE, ChangeLogLevel::fromJson);
        parseStrategies.put(RetrieveLoggerConfig.TYPE, RetrieveLoggerConfig::fromJson);
        parseStrategies.put(RetrieveStatistics.TYPE, RetrieveStatistics::fromJson);
        parseStrategies.put(RetrieveStatisticsDetails.TYPE, RetrieveStatistics::fromJson);
        parseStrategies.put(ExecutePiggybackCommand.TYPE, ExecutePiggybackCommand::fromJson);

        // namespace commands
        parseStrategies.put(BlockNamespace.TYPE, BlockNamespace::fromJson);
        parseStrategies.put(PurgeNamespace.TYPE, PurgeNamespace::fromJson);
        parseStrategies.put(QueryNamespaceEmptiness.TYPE, QueryNamespaceEmptiness::fromJson);
        parseStrategies.put(ShutdownNamespace.TYPE, ShutdownNamespace::fromJson);

        return new DevOpsCommandRegistry(parseStrategies);
    }

    @Override
    protected String resolveType(final JsonObject jsonObject) {
        return jsonObject.getValueOrThrow(DevOpsCommand.JsonFields.TYPE);
    }

}
