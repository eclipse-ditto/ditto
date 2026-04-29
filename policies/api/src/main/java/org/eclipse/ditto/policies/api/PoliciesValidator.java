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
package org.eclipse.ditto.policies.api;

import static java.util.Objects.requireNonNull;

import java.text.MessageFormat;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.common.Validator;
import org.eclipse.ditto.policies.model.PoliciesResourceType;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyEntry;
import org.eclipse.ditto.policies.model.PolicyImporter;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.eclipse.ditto.policies.model.Subject;
import org.eclipse.ditto.policies.model.Subjects;

/**
 * Instances of this class can be used to validate a specified {@link org.eclipse.ditto.policies.model.Policy} with
 * minimum required permissions.
 */
public final class PoliciesValidator implements Validator {

    private static final ResourceKey ROOT_RESOURCE = PoliciesResourceType.policyResource("/");

    private static final String NO_AUTH_SUBJECT_PATTERN =
            "It must contain at least one permanent Subject with permission(s) <{0}> on resource <{1}>!";

    private final Iterable<PolicyEntry> policyEntries;
    private final boolean containsPolicyImport;

    private boolean validationResult;
    @Nullable private String reason;

    private PoliciesValidator(final Iterable<PolicyEntry> policyEntries, final boolean containsPolicyImport) {
        this.policyEntries = policyEntries;
        this.containsPolicyImport = containsPolicyImport;
        validationResult = true;
        reason = null;
    }

    /**
     * Creates a new {@code PoliciesValidator} instance.
     * <p>
     * If {@code policyEntries} is a {@link Policy} whose entries contain local references, the
     * local-reference-resolved view is validated rather than the raw entries — otherwise a policy
     * that splits the WRITE-on-policy:/ subject across two entries linked by mutual local
     * references would be incorrectly rejected. Cross-policy import refs are not resolved here
     * (no policy loader available); a policy with imports continues to short-circuit as
     * {@code valid=true} below.
     *
     * @param policyEntries the policyEntries to be validated.
     * @return a new {@code PoliciesValidator} object.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static PoliciesValidator newInstance(final Iterable<PolicyEntry> policyEntries) {
        requireNonNull(policyEntries, "The policyEntries to validate must not be null!");

        final boolean containsPolicyImport;
        final Iterable<PolicyEntry> entriesToValidate;
        if (policyEntries instanceof Policy policy) {
            containsPolicyImport = !policy.getPolicyImports().isEmpty();
            entriesToValidate = resolveLocalReferencesIfAny(policy);
        } else {
            containsPolicyImport = false;
            entriesToValidate = policyEntries;
        }

        return new PoliciesValidator(entriesToValidate, containsPolicyImport);
    }

    private static Iterable<PolicyEntry> resolveLocalReferencesIfAny(final Policy policy) {
        final boolean anyEntryHasReferences = StreamSupport.stream(policy.spliterator(), false)
                .anyMatch(entry -> !entry.getReferences().isEmpty());
        if (!anyEntryHasReferences) {
            return policy;
        }
        // Synchronous local-reference resolution — does not load imported policies. Import-ref
        // targets that aren't in the policy's own entry set will be silently skipped and
        // contribute nothing to the resolved view; that's the correct semantics here, since the
        // validator only short-circuits when imports are present anyway.
        return PolicyImporter.resolveReferences(policy, policy.getEntriesSet());
    }

    @Override
    public boolean isValid() {
        if (containsPolicyImport) {
            return true;
        } else {
            // Disregard expiring subjects when testing for permissions granted because those are deleted after some time.
            final Set<Subject> withPermissionGranted = StreamSupport.stream(policyEntries.spliterator(), false)
                    .filter(this::hasPermissionGranted)
                    .map(PolicyEntry::getSubjects)
                    .flatMap(Subjects::stream)
                    .filter(subject -> subject.getExpiry().isEmpty())
                    .collect(Collectors.toSet());

            final Set<Subject> withPermissionRevoked = StreamSupport.stream(policyEntries.spliterator(), false)
                    .filter(this::hasPermissionRevoked)
                    .map(PolicyEntry::getSubjects)
                    .flatMap(Subjects::stream)
                    .collect(Collectors.toSet());

            withPermissionGranted.removeAll(withPermissionRevoked);

            validationResult = !withPermissionGranted.isEmpty();

            if (!validationResult) {
                reason = MessageFormat.format(NO_AUTH_SUBJECT_PATTERN, Permission.MIN_REQUIRED_POLICY_PERMISSIONS,
                        ROOT_RESOURCE);
            }

            return validationResult;
        }
    }

    private boolean hasPermissionGranted(final PolicyEntry policyEntry) {
        return policyEntry.getResources().stream()
                .anyMatch(resource -> {
                    final boolean isRootResource = ROOT_RESOURCE.equals(resource.getResourceKey());
                    final boolean containsGrantedPermissions = resource.getEffectedPermissions()
                            .getGrantedPermissions()
                            .contains(Permission.MIN_REQUIRED_POLICY_PERMISSIONS);

                    return isRootResource && containsGrantedPermissions;
                });
    }

    private boolean hasPermissionRevoked(final PolicyEntry policyEntry) {
        return policyEntry.getResources().stream()
                .anyMatch(resource -> {
                    final boolean isRootResource = ROOT_RESOURCE.equals(resource.getResourceKey());
                    final boolean containsRevokedPermissions = resource.getEffectedPermissions()
                            .getRevokedPermissions()
                            .contains(Permission.MIN_REQUIRED_POLICY_PERMISSIONS);

                    return isRootResource && containsRevokedPermissions;
                });
    }

    @Override
    public Optional<String> getReason() {
        return Optional.ofNullable(reason);
    }
}
