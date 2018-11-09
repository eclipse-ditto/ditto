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
package org.eclipse.ditto.services.models.connectivity.placeholder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.protocoladapter.TopicPath;

/**
 * Placeholder implementation that replaces:
 * <ul>
 * <li>{@code topic:full} -> {@code {namespace}/{entityId}/{group}/{channel}/{criterion}/{action|subject}}</li>
 * <li>{@code topic:namespace}</li>
 * <li>{@code topic:entityId}</li>
 * <li>{@code topic:group}</li>
 * <li>{@code topic:channel}</li>
 * <li>{@code topic:criterion}</li>
 * <li>{@code topic:action}</li>
 * <li>{@code topic:subject}</li>
 * <li>{@code topic:action|subject}</li>
 * </ul>
 * The input value is a TopicPath.
 */
@Immutable
final class ImmutableTopicPathPlaceholder implements TopicPathPlaceholder {

    /**
     * Singleton instance of the ImmutableTopicPathPlaceholder.
     */
    static final ImmutableTopicPathPlaceholder INSTANCE = new ImmutableTopicPathPlaceholder();

    private static final String FULL_PLACEHOLDER = "full";
    private static final String NAMESPACE_PLACEHOLDER = "namespace";
    private static final String ENTITYID_PLACEHOLDER = "entityId";
    private static final String GROUP_PLACEHOLDER = "group";
    private static final String CHANNEL_PLACEHOLDER = "channel";
    private static final String CRITERION_PLACEHOLDER = "criterion";
    private static final String ACTION_PLACEHOLDER = "action";
    private static final String SUBJECT_PLACEHOLDER = "subject";
    private static final String ACTION_OR_SUBJECT_PLACEHOLDER = "action|subject";

    private static final List<String> SUPPORTED = Collections.unmodifiableList(
            Arrays.asList(FULL_PLACEHOLDER, NAMESPACE_PLACEHOLDER, ENTITYID_PLACEHOLDER, GROUP_PLACEHOLDER,
                    CHANNEL_PLACEHOLDER, CRITERION_PLACEHOLDER, ACTION_PLACEHOLDER, SUBJECT_PLACEHOLDER,
                    ACTION_OR_SUBJECT_PLACEHOLDER));

    private ImmutableTopicPathPlaceholder() {
    }

    @Override
    public String getPrefix() {
        return "topic";
    }

    @Override
    public List<String> getSupportedNames() {
        return SUPPORTED;
    }

    @Override
    public boolean supports(final String name) {
        return SUPPORTED.contains(name);
    }

    @Override
    public Optional<String> apply(final TopicPath topicPath, final String placeholder) {
        ConditionChecker.argumentNotEmpty(placeholder, "placeholder");
        switch (placeholder) {
            case NAMESPACE_PLACEHOLDER:
                return Optional.of(topicPath.getNamespace());
            case ENTITYID_PLACEHOLDER:
                return Optional.of(topicPath.getId());
            case GROUP_PLACEHOLDER:
                return Optional.of(topicPath.getGroup().getName());
            case CHANNEL_PLACEHOLDER:
                return Optional.of(topicPath.getChannel().getName());
            case CRITERION_PLACEHOLDER:
                return Optional.of(topicPath.getCriterion().getName());
            case ACTION_PLACEHOLDER:
                return topicPath.getAction().map(TopicPath.Action::getName);
            case SUBJECT_PLACEHOLDER:
                return topicPath.getSubject();
            case ACTION_OR_SUBJECT_PLACEHOLDER:
                // treat action|subject as synonyms:
                return Optional.ofNullable(
                        topicPath.getSubject()
                                .orElseGet(() ->
                                        topicPath.getAction().map(TopicPath.Action::getName).orElse(null)
                                )
                );
            case FULL_PLACEHOLDER:
                return Optional.of(topicPath.getPath());
            default:
                return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[]";
    }
}
