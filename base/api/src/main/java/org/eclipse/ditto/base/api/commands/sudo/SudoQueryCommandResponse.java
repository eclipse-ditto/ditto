/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.api.commands.sudo;

import org.eclipse.ditto.base.model.signals.commands.WithEntity;
import org.eclipse.ditto.json.JsonValue;

/**
 * Aggregates all sudo "query" command responses in Ditto.
 *
 * @param <T> the type of the implementing class.
 */
public interface SudoQueryCommandResponse<T extends SudoQueryCommandResponse<T>> extends SudoCommandResponse<T>,
        WithEntity<T> {

    @Override
    T setEntity(JsonValue entity);

}
