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
package org.eclipse.ditto.signals.commands.things.query;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.WithEntity;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;

/**
 * Aggregates all ThingCommandResponses which query the state of a {@link org.eclipse.ditto.model.things.Thing}.
 *
 * @param <T> the type of the implementing class.
 */
public interface ThingQueryCommandResponse<T extends ThingQueryCommandResponse>
        extends ThingCommandResponse<T>, WithEntity<T> {

    @Override
    T setEntity(JsonValue entity);

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

}
