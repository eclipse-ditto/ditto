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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * An immutable implementation of {@link Resources}.
 */
@Immutable
final class ImmutableResources implements Resources {

    private final Map<ResourceKey, Resource> resources;

    private ImmutableResources(final Map<ResourceKey, Resource> theResources) {
        checkNotNull(theResources, "Resources");
        resources = Collections.unmodifiableMap(new LinkedHashMap<>(theResources));
    }

    /**
     * Returns a new instance of {@code Resources} with the given resources.
     *
     * @param resources the {@link Resource}s of the new Resources.
     * @return the new {@code Resources}.
     * @throws NullPointerException if {@code resources} is {@code null}.
     */
    public static ImmutableResources of(final Iterable<Resource> resources) {
        checkNotNull(resources, "resources");

        final Map<ResourceKey, Resource> resourcesMap = new LinkedHashMap<>();
        resources.forEach(resource -> {
            final Resource existingResource = resourcesMap.put(resource.getResourceKey(), resource);
            if (null != existingResource) {
                final String msgTemplate = "There is more than one Resource with the path <{0}>!";
                throw new IllegalArgumentException(MessageFormat.format(msgTemplate, resource.getPath()));
            }
        });

        return new ImmutableResources(resourcesMap);
    }

    /**
     * Creates a new {@code Resources} from the specified JSON object.
     *
     * @param jsonObject the JSON object of which a new Resources instance is to be created.
     * @return the {@code Resources} which was created from the given JSON object.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if the passed in {@code jsonObject} was not in the expected
     * 'Resources' format.
     */
    public static Resources fromJson(final JsonObject jsonObject) {
        final List<Resource> theResources = jsonObject.stream()
                .filter(field -> !Objects.equals(field.getKey(), JsonSchemaVersion.getJsonKey()))
                .map(field -> ImmutableResource.of(ResourceKey.newInstance(field.getKeyName()), field.getValue()))
                .collect(Collectors.toList());

        return of(theResources);
    }

    @Override
    public Optional<Resource> getResource(final ResourceKey resourceKey) {
        checkNotNull(resourceKey, "ResourceKey of the resource to retrieve");
        return Optional.ofNullable(resources.get(resourceKey));
    }

    @Override
    public Resources setResource(final Resource resource) {
        checkNotNull(resource, "resource to set");

        Resources result = this;

        final Resource existingResource = resources.get(resource.getResourceKey());
        if (!Objects.equals(existingResource, resource)) {
            result = createNewResourcesWithNewResource(resource);
        }

        return result;
    }

    private Resources createNewResourcesWithNewResource(final Resource newResource) {
        final Map<ResourceKey, Resource> resourcesCopy = copyResources();
        resourcesCopy.put(newResource.getResourceKey(), newResource);
        return new ImmutableResources(resourcesCopy);
    }

    private Map<ResourceKey, Resource> copyResources() {
        return new LinkedHashMap<>(resources);
    }

    @Override
    public Resources removeResource(final ResourceKey resourceKey) {
        checkNotNull(resourceKey, "ResourceKey of the resource to remove");

        if (!resources.containsKey(resourceKey)) {
            return this;
        }

        final Map<ResourceKey, Resource> resourcesCopy = copyResources();
        resourcesCopy.remove(resourceKey);

        return new ImmutableResources(resourcesCopy);
    }

    @Override
    public int getSize() {
        return resources.size();
    }

    @Override
    public boolean isEmpty() {
        return resources.isEmpty();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        return JsonFactory.newObjectBuilder()
                .setAll(resourcesToJson(schemaVersion, thePredicate), predicate)
                .build();
    }

    private JsonObject resourcesToJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();

        resources.values().forEach(resource -> {
            final JsonKey key = JsonKey.of(resource.getFullQualifiedPath());
            final JsonValue value = resource.toJson(schemaVersion, thePredicate);
            final JsonFieldDefinition<JsonObject> fieldDefinition =
                    JsonFactory.newJsonObjectFieldDefinition(key, FieldType.REGULAR, JsonSchemaVersion.V_2);
            final JsonField field = JsonFactory.newField(key, value, fieldDefinition);

            jsonObjectBuilder.set(field, predicate);
        });

        return jsonObjectBuilder.build();
    }

    @Override
    public Iterator<Resource> iterator() {
        return new LinkedHashSet<>(resources.values()).iterator();
    }

    @Override
    public Stream<Resource> stream() {
        return resources.values().stream();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableResources resources1 = (ImmutableResources) o;
        return Objects.equals(resources, resources1.resources);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resources);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [resources=" + resources + "]";
    }

}
