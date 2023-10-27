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
package org.eclipse.ditto.things.model.signals.commands.modify;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.WithOptionalEntity;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandResponse;

/**
 * Aggregates all ThingCommandResponses which modify the state of a {@link org.eclipse.ditto.things.model.Thing}.
 *
 * @param <T> the type of the implementing class.
 */
public interface ThingModifyCommandResponse<T extends ThingModifyCommandResponse<T>> extends ThingCommandResponse<T>,
        WithOptionalEntity<T> {

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);
}
