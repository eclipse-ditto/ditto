/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.signals.commands.common;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.signals.base.AbstractJsonParsableRegistry;
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.commands.common.purge.PurgeEntities;

/**
 * A {@link org.eclipse.ditto.signals.base.JsonParsableRegistry} aware of all
 * {@link org.eclipse.ditto.signals.commands.common.CommonCommand}s.
 */
@Immutable
public final class CommonCommandRegistry extends AbstractJsonParsableRegistry<CommonCommand> {

    private CommonCommandRegistry(final Map<String, JsonParsable<CommonCommand>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns an instance of {@code CommonCommandRegistry}.
     *
     * @return the instance.
     */
    public static CommonCommandRegistry getInstance() {
        final Map<String, JsonParsable<CommonCommand>> parseStrategies = new HashMap<>();
        parseStrategies.put(Shutdown.TYPE, Shutdown::fromJson);
        parseStrategies.put(PurgeEntities.TYPE, PurgeEntities::fromJson);

        return new CommonCommandRegistry(parseStrategies);
    }

    @Override
    protected String resolveType(final JsonObject jsonObject) {
        return jsonObject.getValueOrThrow(CommonCommand.JsonFields.TYPE);
    }

}
