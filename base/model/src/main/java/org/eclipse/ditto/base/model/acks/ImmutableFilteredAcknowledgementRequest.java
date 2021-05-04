/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.acks;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * Immutable implementation of {@link org.eclipse.ditto.base.model.acks.FilteredAcknowledgementRequest}.
 *
 * @since 1.2.0
 */
@Immutable
final class ImmutableFilteredAcknowledgementRequest implements FilteredAcknowledgementRequest {

    private final Set<AcknowledgementRequest> includes;
    @Nullable private final String filter;

    private ImmutableFilteredAcknowledgementRequest(final Set<AcknowledgementRequest> includes,
            @Nullable final String filter) {
        this.includes = Collections.unmodifiableSet(new HashSet<>(includes));
        this.filter = filter;
    }

    /**
     * Returns an instance of {@code ImmutableFilteredAcknowledgementRequest}.
     *
     * @param includes the returned acknowledgement requests.
     * @param filter the optional filter applied to the returned acknowledgement requests.
     * @return the instance.
     * @throws NullPointerException if {@code includes} is {@code null}.
     */
    static ImmutableFilteredAcknowledgementRequest getInstance(
            final Set<AcknowledgementRequest> includes, @Nullable final String filter) {
        return new ImmutableFilteredAcknowledgementRequest(checkNotNull(includes, "includes"), filter);
    }

    @Override
    public Set<AcknowledgementRequest> getIncludes() {
        return includes;
    }

    @Override
    public Optional<String> getFilter() {
        return Optional.ofNullable(filter);
    }

    /**
     * Creates a new {@code FilteredAcknowledgementRequest} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the FilteredAcknowledgementRequest to be created.
     * @return a new FilteredAcknowledgementRequest which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static FilteredAcknowledgementRequest fromJson(final JsonObject jsonObject) {
        return FilteredAcknowledgementRequest.of(
                jsonObject.getValue(JsonFields.INCLUDES).orElse(JsonArray.empty()).stream()
                        .filter(JsonValue::isString)
                        .map(JsonValue::asString)
                        .map(AcknowledgementRequest::parseAcknowledgementRequest)
                        .collect(Collectors.toSet()), jsonObject.getValue(JsonFields.FILTER).orElse(null));
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {

        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);

        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder();
        jsonObjectBuilder.set(JsonFields.INCLUDES, includes.stream()
                .map(AcknowledgementRequest::getLabel)
                .map(AcknowledgementLabel::toString)
                .map(JsonFactory::newValue)
                .collect(JsonCollectors.valuesToArray()), predicate);

        if (filter != null) {
            jsonObjectBuilder.set(JsonFields.FILTER, filter, predicate);
        }
        return jsonObjectBuilder.build();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableFilteredAcknowledgementRequest that = (ImmutableFilteredAcknowledgementRequest) o;
        return Objects.equals(includes, that.includes) &&
                Objects.equals(filter, that.filter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(includes, filter);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "includes=" + includes +
                ", filter=" + filter +
                "]";
    }
}
