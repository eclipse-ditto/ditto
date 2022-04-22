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
package org.eclipse.ditto.policies.enforcement;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.policies.enforcement.config.CreationRestrictionConfig;
import org.eclipse.ditto.policies.enforcement.config.EntityCreationConfig;

/**
 * Default implementation for {@link CreationRestrictionEnforcer} based on the configuration from {@link EntityCreationConfig}.
 */
@Immutable
public class DefaultCreationRestrictionEnforcer implements CreationRestrictionEnforcer {

    private static final ThreadSafeDittoLogger log = DittoLoggerFactory.getThreadSafeLogger(DefaultCreationRestrictionEnforcer.class);

    private final EntityCreationConfig config;

    private DefaultCreationRestrictionEnforcer(final EntityCreationConfig config) {
        this.config = config;
    }

    /**
     * Create a new implementation from the provided configuration.
     *
     * @param config the configuration to use.
     * @return The new instance.
     */
    public static DefaultCreationRestrictionEnforcer of(final EntityCreationConfig config) {
        return new DefaultCreationRestrictionEnforcer(config);
    }

    @Override
    public boolean canCreate(final Context context) {
        return matchesList(this.config.getGrant(), context)
                && !matchesList(this.config.getRevoke(), context);
    }

    private boolean matchesList(final List<CreationRestrictionConfig> list, final Context context) {
        return list.stream().anyMatch(entry -> matches(entry, context));
    }

    private static boolean matches(final CreationRestrictionConfig entry, final Context context) {

        return matchesResourceType(entry.getResourceTypes(), context)
                && matchesAuthSubjectPattern(entry.getAuthSubject(), context)
                && matchesNamespacePattern(entry.getNamespace(), context);

    }

    private static boolean matchesResourceType(final Set<String> resourceTypes, final Context context) {
        if (resourceTypes.isEmpty()) {
            // no restriction -> pass
            log.withCorrelationId(context.getHeaders()).debug("No resource type restriction: pass");
            return true;
        }

        if (resourceTypes.contains(context.getResourceType())) {
            return true;
        }

        log.withCorrelationId(context.getHeaders()).debug("No resource type match: reject");
        // no match, but non-empty list -> reject
        return false;
    }

    private static boolean matchesAuthSubjectPattern(final List<Pattern> authSubjectPatterns, final Context context) {
        if (authSubjectPatterns.isEmpty()) {
            // no restrictions -> pass
            log.withCorrelationId(context.getHeaders()).debug("No auth subject restriction: pass");
            return true;
        }

        for (final var subject : context.getAuthorizationContext().getAuthorizationSubjectIds()) {
            for (final var authSubjectPattern : authSubjectPatterns) {
                if (authSubjectPattern.matcher(subject).matches()) {
                    log.withCorrelationId(context.getHeaders()).debug("Matched auth subject {}: pass", subject);
                    // entry found -> pass
                    return true;
                }
            }
        }

        log.withCorrelationId(context.getHeaders()).debug("No auth subject match: reject");
        // no match, but non-empty list -> reject
        return false;
    }

    private static boolean matchesNamespacePattern(final List<Pattern> namespacePatterns, final Context context) {

        if (namespacePatterns.isEmpty()) {
            log.withCorrelationId(context.getHeaders()).debug("No namespace restriction: pass");
            // no restrictions -> pass
            return true;
        }

        var namespace = context.getNamespace();
        for (final var namespacePattern : namespacePatterns) {
            if (namespacePattern.matcher(namespace).matches()) {
                log.withCorrelationId(context.getHeaders()).debug("Namespace '{}' matched {}: pass", namespace, namespacePattern);
                return true;
            }
        }

        log.withCorrelationId(context.getHeaders()).debug("No namespace match: reject");
        // no match, but non-empty list -> reject
        return false;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "config=" + config +
                ']';
    }
}
