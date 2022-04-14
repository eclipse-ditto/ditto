/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.placeholders;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.protocol.TopicPath;

/**
 * Placeholder implementation that replaces:
 * <ul>
 * <li>{@code topic:full} -> {@code {namespace}/{entityName}/{group}/{channel}/{criterion}/{action-subject}}</li>
 * <li>{@code topic:namespace}</li>
 * <li>{@code topic:entityName}</li>
 * <li>{@code topic:group}</li>
 * <li>{@code topic:channel}</li>
 * <li>{@code topic:criterion}</li>
 * <li>{@code topic:action}</li>
 * <li>{@code topic:subject}</li>
 * <li>{@code topic:action-subject}</li>
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
    private static final String ENTITY_NAME_PLACEHOLDER = "entityName";
    private static final String GROUP_PLACEHOLDER = "group";
    private static final String CHANNEL_PLACEHOLDER = "channel";
    private static final String CRITERION_PLACEHOLDER = "criterion";
    private static final String ACTION_PLACEHOLDER = "action";
    private static final String SUBJECT_PLACEHOLDER = "subject";
    private static final String ACTION_OR_SUBJECT_PLACEHOLDER = "action-subject";

    private static final List<String> SUPPORTED = Collections.unmodifiableList(
            Arrays.asList(FULL_PLACEHOLDER, NAMESPACE_PLACEHOLDER, ENTITY_NAME_PLACEHOLDER,
                    GROUP_PLACEHOLDER, CHANNEL_PLACEHOLDER, CRITERION_PLACEHOLDER, ACTION_PLACEHOLDER,
                    SUBJECT_PLACEHOLDER, ACTION_OR_SUBJECT_PLACEHOLDER));

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
    public List<String> resolveValues(final TopicPath topicPath, final String placeholder) {
        ConditionChecker.argumentNotEmpty(placeholder, "placeholder");
        switch (placeholder) {
            case NAMESPACE_PLACEHOLDER:
                return Collections.singletonList(topicPath.getNamespace());
            case ENTITY_NAME_PLACEHOLDER:
                return Collections.singletonList(topicPath.getEntityName());
            case GROUP_PLACEHOLDER:
                return Collections.singletonList(topicPath.getGroup().getName());
            case CHANNEL_PLACEHOLDER:
                return Collections.singletonList(topicPath.getChannel().getName());
            case CRITERION_PLACEHOLDER:
                return Collections.singletonList(topicPath.getCriterion().getName());
            case ACTION_PLACEHOLDER:
                return topicPath.getAction()
                        .map(TopicPath.Action::getName)
                        .map(Collections::singletonList)
                        .orElseGet(Collections::emptyList);
            case SUBJECT_PLACEHOLDER:
                return topicPath.getSubject()
                        .map(Collections::singletonList)
                        .orElseGet(Collections::emptyList);
            case ACTION_OR_SUBJECT_PLACEHOLDER:
                // treat action-subject as synonyms:
                return Optional.ofNullable(topicPath.getSubject()
                                .orElseGet(() -> topicPath.getAction().map(TopicPath.Action::getName).orElse(null)))
                        .map(Collections::singletonList)
                        .orElseGet(Collections::emptyList);
            case FULL_PLACEHOLDER:
                return Collections.singletonList(topicPath.getPath());
            default:
                return Collections.emptyList();
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[]";
    }
}
