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
package org.eclipse.ditto.thingsearch.service.common.model;

import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.json.JsonArray;

/**
 * ResultList that has the array of sort expressions of the last result.
 *
 * @param <E> the type of the items
 */
public interface ResultList<E> extends List<E> {

    /**
     * Get values of sort expressions of the last result.
     *
     * @return the array of sort expressions of the last result
     */
    Optional<JsonArray> lastResultSortValues();

}
