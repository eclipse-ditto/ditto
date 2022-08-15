/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.enforcement;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.placeholders.PlaceholderReferenceNotSupportedException;

/**
 * Responsible to extract and hold information about a referenced entity from a placeholder String.
 */
@Immutable
final class ReferencePlaceholder {

    private static final String placeholderBeginning = "\\{\\{\\s?";
    private static final String placeholderEnding = "\\s?}}";
    private static final String referenceKeyword = "ref";
    private static final String everythingExceptFrontSlashesAndSpaces = "[^/\\s]";
    private static final String everythingExceptSpaces = "[^\\s]";
    private static final String entityTypeGroup = "(" + everythingExceptFrontSlashesAndSpaces + "+)";
    private static final String entityIdGroup = "(" + everythingExceptFrontSlashesAndSpaces + "+)";
    private static final String fieldSelectorGroup = "(" + everythingExceptSpaces + "+)";
    private static final Pattern referencePlaceholderPattern = Pattern.compile(
            placeholderBeginning + referenceKeyword + ":" + entityTypeGroup + "/" + entityIdGroup + "/" +
                    fieldSelectorGroup + placeholderEnding);

    private final ReferencedEntityType referencedEntityType;
    private final EntityId referencedEntityId;
    private final JsonPointer referencedField;

    private ReferencePlaceholder(final ReferencedEntityType referencedEntityType, final EntityId referencedEntityId,
            final JsonPointer referencedField) {
        this.referencedEntityType = referencedEntityType;
        this.referencedEntityId = referencedEntityId;
        this.referencedField = referencedField;
    }

    /**
     * Matches the given input against {@link #referencePlaceholderPattern}. If the input does not match the returned
     * Optional will be empty. If it does match the Optional will contain an instance of {@code ReferencePlaceholder}.
     *
     * @param input The placeholder input that should be matched against {@link #referencePlaceholderPattern}.
     * @return An Optional of {@code ReferencePlaceholder}. Optional is empty if the given input does not match
     * {@link #referencePlaceholderPattern}.
     */
    public static Optional<ReferencePlaceholder> fromCharSequence(@Nullable final CharSequence input) {

        if (input == null) {
            return Optional.empty();
        }

        final Matcher matcher = referencePlaceholderPattern.matcher(input);
        if (matcher.find()) {
            final ReferencedEntityType referencedEntityType = ReferencedEntityType.fromString(matcher.group(1));
            final EntityId entityId = referencedEntityType.toEntityId(matcher.group(2));
            final JsonPointer referencedField = JsonPointer.of(matcher.group(3));
            return Optional.of(new ReferencePlaceholder(referencedEntityType, entityId, referencedField));
        }

        return Optional.empty();
    }


    ReferencedEntityType getReferencedEntityType() {
        return referencedEntityType;
    }

    EntityId getReferencedEntityId() {
        return referencedEntityId;
    }

    JsonPointer getReferencedField() {
        return referencedField;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ReferencePlaceholder that = (ReferencePlaceholder) o;
        return referencedEntityType == that.referencedEntityType &&
                Objects.equals(referencedEntityId, that.referencedEntityId) &&
                Objects.equals(referencedField, that.referencedField);
    }

    @Override
    public int hashCode() {
        return Objects.hash(referencedEntityType, referencedEntityId, referencedField);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "referencedEntityType=" + referencedEntityType +
                ", referencedEntityId=" + referencedEntityId +
                ", referencedField=" + referencedField +
                "]";
    }

    public enum ReferencedEntityType {

        THINGS(id -> NamespacedEntityId.of(EntityType.of("thing"), id));

        private final Function<? super CharSequence, ? extends EntityId> entityIdFactory;

        <I extends EntityId> ReferencedEntityType(final Function<? super CharSequence, I> entityIdFactory) {
            this.entityIdFactory = entityIdFactory;
        }

        public static ReferencedEntityType fromString(final String referencedEntityTypeString) {
            if ("things".equalsIgnoreCase(referencedEntityTypeString)) {
                return THINGS;
            } else {
                final Set<CharSequence> supportedEntityTypes =
                        Arrays.stream(ReferencedEntityType.values()).map(Enum::name).collect(Collectors.toSet());

                throw PlaceholderReferenceNotSupportedException
                        .fromUnsupportedEntityType(referencedEntityTypeString, supportedEntityTypes)
                        .build();
            }
        }

        private EntityId toEntityId(final CharSequence entityId) {
            return entityIdFactory.apply(entityId);
        }

    }

}
