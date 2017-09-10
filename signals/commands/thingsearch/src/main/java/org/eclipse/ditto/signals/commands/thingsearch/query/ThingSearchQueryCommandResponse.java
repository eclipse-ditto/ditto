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
package org.eclipse.ditto.signals.commands.thingsearch.query;


import org.eclipse.ditto.signals.commands.base.WithEntity;
import org.eclipse.ditto.signals.commands.thingsearch.ThingSearchCommandResponse;

/**
 * Aggregates all SearchCommandResponses which query the Search Service.
 *
 * @param <T> the type of the implementing class.
 */
public interface ThingSearchQueryCommandResponse<T extends ThingSearchQueryCommandResponse>
        extends ThingSearchCommandResponse<T>,
        WithEntity<T> {
}

