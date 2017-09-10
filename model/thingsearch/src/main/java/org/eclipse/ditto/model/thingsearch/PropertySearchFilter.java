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
package org.eclipse.ditto.model.thingsearch;

import java.util.Collection;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * A property search filter defines what property exactly is to be searched. For example if the goal is to search for a
 * threshold which is greater than some value, the following code creates an according search query:
 *
 * <pre>
 *    final SearchProperty thresholdProperty = SearchModelFactory.property("attributes/threshold");
 *    final PropertySearchFilter propertySearchFilter = thresholdProperty.gt(42.23);
 *
 *    final SearchQuery searchQuery = SearchModelFactory.newSearchQuery(propertySearchFilter);
 * </pre>
 *
 * To define a more fine grained search, property search filters can be concatenated with the help
 * {@link LogicalSearchFilter} which are available through the {@link SearchModelFactory}.
 */
@Immutable
public interface PropertySearchFilter extends SearchFilter {

    /**
     * Returns the path of the property to be searched for.
     *
     * @return the path of the searched property.
     */
    JsonPointer getPath();

    /**
     * Returns an unmodifiable collection containing the values which confine the search result as the searched property
     * has to comply to them in a way the type of this filter determines. For example, if a Thing with a particular ID
     * is searched, the values would contain the searched Thing ID while the type would be {@link
     * Type#EQ}.
     *
     * @return the values this property has to match to be part of the search result.
     * @see #getType()
     */
    Collection<JsonValue> getValues();

}
