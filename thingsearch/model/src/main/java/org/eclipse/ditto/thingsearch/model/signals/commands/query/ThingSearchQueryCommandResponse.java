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
package org.eclipse.ditto.thingsearch.model.signals.commands.query;


import org.eclipse.ditto.base.model.signals.commands.WithEntity;
import org.eclipse.ditto.thingsearch.model.signals.commands.ThingSearchCommandResponse;

/**
 * Aggregates all SearchCommandResponses which query the Search Service.
 *
 * @param <T> the type of the implementing class.
 */
public interface ThingSearchQueryCommandResponse<T extends ThingSearchQueryCommandResponse<T>>
        extends ThingSearchCommandResponse<T>, WithEntity<T> {
}

