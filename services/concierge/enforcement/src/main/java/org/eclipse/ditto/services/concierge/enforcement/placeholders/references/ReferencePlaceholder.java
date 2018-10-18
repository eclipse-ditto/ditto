/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.concierge.enforcement.placeholders.references;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayPlaceholderReferenceNotSupportedException;

/**
 * Responsible to extract and hold information about a referenced entity from a placeholder String.
 */
@Immutable
public final class ReferencePlaceholder {

    private static String placeholderBeginning = "\\{\\{\\s?";
    private static String placeholderEnding = "\\s?}}";
    private static String referenceKeyword = "ref";
    private static String everythingExceptFrontSlashesAndSpaces = "[^/\\s]";
    private static String entityTypeGroup = "(" + everythingExceptFrontSlashesAndSpaces + "+)";
    private static String entityIdGroup = "(" + everythingExceptFrontSlashesAndSpaces + "+)";
    private static String fieldSelectorGroup = "(" + everythingExceptFrontSlashesAndSpaces + "+)";
    private static Pattern referencePlaceholderPattern = Pattern.compile(
            placeholderBeginning + referenceKeyword + ":" + entityTypeGroup + "/" + entityIdGroup + "/" +
                    fieldSelectorGroup + placeholderEnding);

    private final ReferencedEntityType referencedEntityType;
    private final String referencedEntityId;
    private final JsonFieldSelector referencedFieldSelector;

    private ReferencePlaceholder(final ReferencedEntityType referencedEntityType, final String referencedEntityId,
            final JsonFieldSelector referencedFieldSelector) {
        this.referencedEntityType = referencedEntityType;
        this.referencedEntityId = referencedEntityId;
        this.referencedFieldSelector = referencedFieldSelector;
    }

    /**
     * Matches the given input against {@link #referencePlaceholderPattern}. If the input does not match the returned
     * Optional will be empty. If it does match the Optional will contain an instance of {@link ReferencePlaceholder}.
     *
     * @param input The placeholder input that should be matched against {@link #referencePlaceholderPattern}.
     * @return An Optional of {@link ReferencePlaceholder}. Optional is empty if the given input does not match
     * {@link #referencePlaceholderPattern}.
     */
    public static Optional<ReferencePlaceholder> fromCharSequence(@Nullable final CharSequence input) {

        if (input == null) {
            return Optional.empty();
        }

        final Matcher matcher = referencePlaceholderPattern.matcher(input);
        if (matcher.find()) {
            return Optional.of(
                    new ReferencePlaceholder(ReferencedEntityType.fromString(matcher.group(1)), matcher.group(2),
                            JsonFieldSelector.newInstance(matcher.group(3))));
        }

        return Optional.empty();
    }


    ReferencedEntityType getReferencedEntityType() {
        return referencedEntityType;
    }

    String getReferencedEntityId() {
        return referencedEntityId;
    }

    JsonFieldSelector getReferencedFieldSelector() {
        return referencedFieldSelector;
    }

    public enum ReferencedEntityType {

        THINGS;

        public static ReferencedEntityType fromString(final String referencedEntityTypeString) {
            if ("things".equalsIgnoreCase(referencedEntityTypeString)) {
                return THINGS;
            } else {
                final Set<CharSequence> supportedEntityTypes =
                        Arrays.stream(ReferencedEntityType.values()).map(Enum::name).collect(Collectors.toSet());

                throw GatewayPlaceholderReferenceNotSupportedException
                        .fromUnsupportedEntityType(referencedEntityTypeString, supportedEntityTypes)
                        .build();
            }
        }
    }
}
