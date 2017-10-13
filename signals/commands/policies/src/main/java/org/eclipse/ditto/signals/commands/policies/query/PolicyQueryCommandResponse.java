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
package org.eclipse.ditto.signals.commands.policies.query;

import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.WithEntity;
import org.eclipse.ditto.signals.commands.policies.PolicyCommandResponse;


/**
 * Aggregates all PolicyCommandResponses which query state of a {@link org.eclipse.ditto.model.policies.Policy}.
 *
 * @param <T> the type of the implementing class.
 */
public interface PolicyQueryCommandResponse<T extends PolicyQueryCommandResponse>
        extends PolicyCommandResponse<T>, WithEntity<T> {

    @Override
    T setEntity(JsonValue entity);

    @Override
    T setDittoHeaders(DittoHeaders dittoHeaders);

}
