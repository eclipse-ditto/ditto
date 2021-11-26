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
package org.eclipse.ditto.policies.model;

import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;

/**
 * A collection of {@link Resource}s contained in a single {@link PolicyEntry}.
 */
public interface Resources extends Iterable<Resource>, Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns a new {@code Resources} containing the given resources.
     *
     * @param resources the {@link Resource}s to be contained in the new Resources.
     * @return the new {@code Resources}.
     * @throws NullPointerException if {@code resources} is {@code null}.
     */
    static Resources newInstance(final Iterable<Resource> resources) {
        return PoliciesModelFactory.newResources(resources);
    }

    /**
     * Returns a new {@code Resources} containing the given resource.
     *
     * @param resource the {@link Resource} to be contained in the new Resources.
     * @param furtherResources further {@link Resource}s to be contained in the new Resources.
     * @return the new {@code Resources}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static Resources newInstance(final Resource resource, final Resource... furtherResources) {
        return PoliciesModelFactory.newResources(resource, furtherResources);
    }

    /**
     * Returns the supported JSON schema version of this resources. <em>Resources support only
     * {@link JsonSchemaVersion#V_2}.</em>
     *
     * @return the supported schema version.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * Returns the Resource with the given {@code resourceType} and {@code resourcePath} or an empty optional.
     *
     * @param resourceType the type of the Resource to be retrieved.
     * @param resourcePath the path of the Resource to be retrieved.
     * @return the Resource or an empty optional.
     * @throws NullPointerException if {@code resourcePath} is {@code null}.
     * @throws IllegalArgumentException if {@code resourceType} is empty.
     */
    default Optional<Resource> getResource(final String resourceType, final CharSequence resourcePath) {
        return getResource(ResourceKey.newInstance(resourceType, resourcePath));
    }

    /**
     * Returns the Resource with the given {@code resourcePath} or an empty optional.
     *
     * @param resourceKey the ResourceKey of the Resource to be retrieved.
     * @return the Resource with the given path or an empty optional.
     * @throws NullPointerException if {@code path} is {@code null}.
     */
    Optional<Resource> getResource(ResourceKey resourceKey);

    /**
     * Sets the given Resource to a copy of this Resources. A previous Resource with the same identifier will be
     * overwritten.
     *
     * @param resource the Resource to be set.
     * @return a copy of this Resources with {@code resource} set.
     * @throws NullPointerException if {@code resource} is {@code null}.
     */
    Resources setResource(Resource resource);

    /**
     * Removes the Resource with the given {@code resourceType} and {@code resourcePath} from a copy of this Resources.
     *
     * @param resourceType the type of the Resource to be removed.
     * @param resourcePath the path of the Resource to be removed.
     * @return a copy of this Resources with {@code resource} removed.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code resourceType} is empty.
     */
    default Resources removeResource(final String resourceType, final CharSequence resourcePath) {
        return removeResource(ResourceKey.newInstance(resourceType, resourcePath));
    }

    /**
     * Removes the Resource with the given {@code resourcePath} from a copy of this Resources.
     *
     * @param resourceKey the ResourceKey of the Resource to be removed.
     * @return a copy of this Resources with {@code resource} removed.
     * @throws NullPointerException if {@code resourceKey} is {@code null}.
     */
    Resources removeResource(ResourceKey resourceKey);

    /**
     * Returns the size of this Resources, i. e. the number of contained values.
     *
     * @return the size.
     */
    int getSize();

    /**
     * Indicates whether this Resources is empty.
     *
     * @return {@code true} if this Resources does not contain any values, {@code false} else.
     */
    boolean isEmpty();

    /**
     * Returns a sequential {@code Stream} with the values of this Resources as its source.
     *
     * @return a sequential stream of the Resources of this container.
     */
    Stream<Resource> stream();

    /**
     * Returns all non-hidden marked fields of this Resources.
     *
     * @return a JSON object representation of this Resources including only non-hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.regularOrSpecial()).get(fieldSelector);
    }

}
