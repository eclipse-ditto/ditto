/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.pubsub.extractors;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;

/**
 * Extract read-subjects of messages as topics.
 *
 * @param <T> type of messages.
 */
public final class ReadSubjectExtractor<T extends WithDittoHeaders> implements PubSubTopicExtractor<T> {

    private ReadSubjectExtractor() {}

    /**
     * Create an extractor of read-subjects as topics.
     *
     * @param <T> type of messages.
     * @return a read-subject extractor.
     */
    public static <T extends WithDittoHeaders> ReadSubjectExtractor<T> of() {
        return new ReadSubjectExtractor<>();
    }

    /**
     * Determines the pub/sub topics based on the provided {@code namespaces} and {@code authorizationSubjects}.
     *
     * @param namespaces the namespaces for which pub/sub topics should be determined for - may be empty if all
     * namespaces are relevant and no scoping to namespaces should be done.
     * @param authorizationSubjects the authorization subjects to subscribe for.
     * @return a set of Ditto pub/sub topics with e.g. to subscribe for events in the cluster.
     */
    public static Set<String> determineTopicsFor(
            final Collection<String> namespaces,
            final Collection<AuthorizationSubject> authorizationSubjects
    ) {

        final Set<String> authorizationSubjectIds = authorizationSubjects.stream()
                .map(AuthorizationSubject::getId)
                .collect(Collectors.toSet());
        if (namespaces.isEmpty()) {
            return authorizationSubjectIds;
        } else {
            return namespaces.stream().flatMap(
                    namespace -> combineNamespaceWithAuthSubjects(namespace, authorizationSubjectIds)
            ).collect(Collectors.toSet());
        }
    }

    @Override
    public Collection<String> getTopics(final T event) {
        final DittoHeaders dittoHeaders = event.getDittoHeaders();
        final Set<AuthorizationSubject> readGrantedSubjects = dittoHeaders.getReadGrantedSubjects();

        final Set<String> topicsWithoutNamespace = readGrantedSubjects.stream()
                .map(AuthorizationSubject::getId)
                .collect(Collectors.toSet());

        if (event instanceof WithEntityId withEntityId &&
                withEntityId.getEntityId() instanceof NamespacedEntityId namespacedEntityId) {
            final String namespace = namespacedEntityId.getNamespace();
            return Stream.concat(
                    combineNamespaceWithAuthSubjects(namespace, topicsWithoutNamespace),
                    topicsWithoutNamespace.stream()
            ).collect(Collectors.toSet());
        } else {
            return topicsWithoutNamespace;
        }
    }

    private static Stream<String> combineNamespaceWithAuthSubjects(final String namespace,
            final Set<String> authorizationSubjectIds) {
        return authorizationSubjectIds.stream().map(subject -> namespace + "#" + subject);
    }

}
