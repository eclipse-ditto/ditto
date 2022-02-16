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

/**
 * Contains the Search framework. Entry point to this package is {@link org.eclipse.ditto.thingsearch.model.SearchModelFactory}.
 * This class provides methods for creating a fully fledged {@link org.eclipse.ditto.thingsearch.model.SearchQuery} as well as
 * the result of a search: {@link org.eclipse.ditto.thingsearch.model.SearchResult}.
 * <p>
 * A SearchResult has an array of result values and a value indicating the offset of the next page of search results or
 * that the current page is the last one.
 *
 * <h2>Object creation</h2>
 * {@link org.eclipse.ditto.thingsearch.model.SearchModelFactory} is the main entry point for obtaining objects of this
 * package's interfaces to work with.
 */
@org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault
package org.eclipse.ditto.thingsearch.model;
