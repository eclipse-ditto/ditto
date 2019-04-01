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
package org.eclipse.ditto.signals.commands.common;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.signals.base.AbstractJsonParsableRegistry;
import org.eclipse.ditto.signals.base.JsonParsable;

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

        return new CommonCommandRegistry(parseStrategies);
    }

    @Override
    protected String resolveType(final JsonObject jsonObject) {
        return jsonObject.getValueOrThrow(CommonCommand.JsonFields.TYPE);
    }

}
