/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.write.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.policies.model.PoliciesResourceType.THING;

import java.util.List;
import java.util.Set;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.api.Permission;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.PolicyImport;
import org.eclipse.ditto.policies.model.SubjectType;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.api.UpdateReason;
import org.eclipse.ditto.thingsearch.persistence.api.mapping.SearchIndexDocumentFactory;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingWriteModel;
import org.junit.Test;

/**
 * Pins the metadata computation of {@link EnforcedThingMapper#toWriteModel}: the thing policy tag (which
 * {@link Metadata} always counts as referenced), the retention of old referenced policies that are still imported,
 * and the carry-over of the old metadata.
 */
public final class EnforcedThingMapperTest {

    private static final ThingId THING_ID = ThingId.of("hello:world");
    private static final long THING_REVISION = 1024L;
    private static final PolicyId POLICY_ID = PolicyId.of("hello:policy");
    private static final PolicyId IMPORTED_POLICY_ID = PolicyId.of("hello:imported");
    private static final long POLICY_REVISION = 56L;
    private static final PolicyTag THING_POLICY_TAG = PolicyTag.of(POLICY_ID, POLICY_REVISION);

    private static final JsonObject THING_JSON = JsonFactory.newObject("""
            {
              "thingId": "hello:world",
              "_namespace": "hello",
              "_revision": 1024,
              "policyId": "hello:policy",
              "attributes": { "hello": "world" }
            }""");

    @Test
    public void toWriteModelBuildsMetadataAndNeutralDocument() {
        final Policy policy = policy(false);

        final ThingWriteModel result =
                EnforcedThingMapper.toWriteModel(THING_JSON, policy, Set.of(), POLICY_REVISION, null, -1);

        final Metadata expectedMetadata = Metadata.of(THING_ID, THING_REVISION,
                THING_POLICY_TAG, null, Set.of(), null, List.of(), List.of(), List.of(),
                List.of(UpdateReason.UNKNOWN));
        assertThat(result.getMetadata()).isEqualTo(expectedMetadata);
        assertThat(result.getMetadata().getThingPolicyTag()).contains(THING_POLICY_TAG);
        assertThat(result.getMetadata().getAllReferencedPolicyTags()).containsExactly(THING_POLICY_TAG);
        assertThat(result.getMetadata().getUpdateReasons()).containsExactly(UpdateReason.UNKNOWN);
        assertThat(result.getDocument())
                .isEqualTo(SearchIndexDocumentFactory.create(THING_JSON, policy, expectedMetadata, -1));
    }

    @Test
    public void retainsOldReferencedPolicyStillImported() {
        final ThingWriteModel result = EnforcedThingMapper.toWriteModel(THING_JSON, policy(true), Set.of(),
                POLICY_REVISION, oldMetadata(List.of(UpdateReason.UNKNOWN)), -1);

        assertThat(result.getMetadata().getAllReferencedPolicyTags())
                .containsExactlyInAnyOrder(THING_POLICY_TAG, PolicyTag.of(IMPORTED_POLICY_ID, 1L));
    }

    @Test
    public void dropsOldReferencedPolicyNoLongerImported() {
        final ThingWriteModel result = EnforcedThingMapper.toWriteModel(THING_JSON, policy(false), Set.of(),
                POLICY_REVISION, oldMetadata(List.of(UpdateReason.UNKNOWN)), -1);

        assertThat(result.getMetadata().getAllReferencedPolicyTags()).containsExactly(THING_POLICY_TAG);
    }

    @Test
    public void prefersNewlyReferencedPolicyOverOldTag() {
        final ThingWriteModel result = EnforcedThingMapper.toWriteModel(THING_JSON, policy(true),
                Set.of(PolicyTag.of(IMPORTED_POLICY_ID, 2L)), POLICY_REVISION,
                oldMetadata(List.of(UpdateReason.UNKNOWN)), -1);

        assertThat(result.getMetadata().getAllReferencedPolicyTags())
                .containsExactlyInAnyOrder(THING_POLICY_TAG, PolicyTag.of(IMPORTED_POLICY_ID, 2L));
    }

    @Test
    public void carriesOverOldMetadataUpdateReasons() {
        final ThingWriteModel result = EnforcedThingMapper.toWriteModel(THING_JSON, policy(true), Set.of(),
                POLICY_REVISION, oldMetadata(List.of(UpdateReason.POLICY_UPDATE)), -1);

        assertThat(result.getMetadata().getUpdateReasons()).containsExactly(UpdateReason.POLICY_UPDATE);
    }

    private static Policy policy(final boolean importsP2) {
        final var builder = Policy.newBuilder(POLICY_ID)
                .forLabel("grant-root")
                .setSubject("g:0", SubjectType.GENERATED)
                .setGrantedPermissions(THING, "/", Permission.READ)
                .exitLabel();
        if (importsP2) {
            builder.setPolicyImport(PolicyImport.newInstance(IMPORTED_POLICY_ID, null));
        }
        return builder.build();
    }

    private static Metadata oldMetadata(final List<UpdateReason> updateReasons) {
        return Metadata.of(THING_ID, THING_REVISION - 1, PolicyTag.of(POLICY_ID, POLICY_REVISION - 1), null,
                Set.of(PolicyTag.of(IMPORTED_POLICY_ID, 1L)), null, List.of(), List.of(), List.of(), updateReasons);
    }

}
