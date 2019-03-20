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
package org.eclipse.ditto.signals.events.base;

import java.util.Map;

import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.signals.base.AbstractJsonParsableRegistry;
import org.eclipse.ditto.signals.base.JsonParsable;

public abstract class AbstractEventRegistry<T extends Event> extends AbstractJsonParsableRegistry<T>
        implements EventRegistry<T>{

    protected AbstractEventRegistry(final Map<String, JsonParsable<T>> parseStrategies) {
        super(parseStrategies);
    }

    @Override
    protected String resolveType(final JsonObject jsonObject) {
        return jsonObject.getValue(Event.JsonFields.TYPE)
                .orElseThrow(() -> JsonMissingFieldException.newBuilder()
                        .fieldName(Event.JsonFields.TYPE.getPointer().toString())
                        .build());
    }
}
