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

package org.eclipse.ditto.gateway.service.endpoints.routes.whoami;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * Default implementation of {@link UserInformation}.
 * @since 1.2.0
 */
@Immutable
public final class DefaultUserInformation implements UserInformation {

    @Nullable
    private final String defaultSubject;
    private final List<String> subjects;

    private DefaultUserInformation(@Nullable final String defaultSubject,
            final List<String> subjects) {
        this.defaultSubject = defaultSubject;
        this.subjects = Collections.unmodifiableList(new ArrayList<>(checkNotNull(subjects, "subjects")));
    }

    /**
     * Creates a new instance of this class by extracting the required information from {@code context}.
     *
     * @param context the context containing information about the user.
     * @return an instance of this class containing the user information extracted from {@code context}.
     * @throws NullPointerException if {@code context} is {@code null}
     */
    public static DefaultUserInformation fromAuthorizationContext(final AuthorizationContext context) {
        checkNotNull(context, "context");

        @Nullable final String defaultSubject =
                context.getFirstAuthorizationSubject().map(AuthorizationSubject::getId).orElse(null);
        final List<String> subjects = context.getAuthorizationSubjects()
                .stream()
                .map(AuthorizationSubject::getId)
                .toList();

        return new DefaultUserInformation(defaultSubject, subjects);
    }

    /**
     * Creates a new instance of this class from the specified JSON object.
     *
     * @param jsonObject a JSON object providing the data of {@link UserInformation}.
     * @return a new instance with the data extracted from {@code jsonObject}
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} contained a value at a defined location
     * with an unexpected type.
     * @throws org.eclipse.ditto.json.JsonMissingFieldException if this {@code jsonObject} did not contain a value at
     * all at a defined location.
     */
    public static DefaultUserInformation fromJson(final JsonObject jsonObject) {
        checkNotNull(jsonObject, "jsonObject");

        @Nullable final String defaultSubject = jsonObject.getValue(JsonFields.DEFAULT_SUBJECT).orElse(null);
        final List<String> subjects = jsonObject.getValueOrThrow(JsonFields.SUBJECTS)
                .stream()
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .collect(Collectors.toList());
        return new DefaultUserInformation(defaultSubject, subjects);
    }

    @Override
    public Optional<String> getDefaultSubject() {
        return Optional.ofNullable(defaultSubject);
    }

    @Override
    public List<String> getSubjects() {
        return subjects;
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> predicate) {
        final JsonObjectBuilder builder = JsonFactory.newObjectBuilder();
        builder.set(JsonFields.DEFAULT_SUBJECT, defaultSubject);
        builder.set(JsonFields.SUBJECTS, subjects.stream().map(JsonValue::of).collect(JsonCollectors.valuesToArray()));
        return builder.build();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultUserInformation that = (DefaultUserInformation) o;
        return Objects.equals(defaultSubject, that.defaultSubject) &&
                Objects.equals(subjects, that.subjects);
    }

    @Override
    public int hashCode() {
        return Objects.hash(defaultSubject, subjects);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "defaultSubject=" + defaultSubject +
                ", subjects=" + subjects +
                "]";
    }

}
