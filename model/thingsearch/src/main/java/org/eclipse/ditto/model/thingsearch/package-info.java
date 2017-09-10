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

/**
 * Contains the Search framework. Entry point to this package is {@link org.eclipse.ditto.model.thingsearch.SearchModelFactory}.
 * This class provides methods for creating a fully fledged {@link org.eclipse.ditto.model.thingsearch.SearchQuery} as well as
 * the result of a search: {@link org.eclipse.ditto.model.thingsearch.SearchResult}.
 * <p>
 * A SearchResult has an array of result values and a value indicating the offset of the next page of search results or
 * that the current page is the last one.
 *
 * <h3>Object creation</h3>
 * {@link org.eclipse.ditto.model.thingsearch.SearchModelFactory} is the main entry point for obtaining objects of this
 * package's interfaces to work with.
 */
@org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault
package org.eclipse.ditto.model.thingsearch;
