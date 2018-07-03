/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 *
 */
package org.eclipse.ditto.services.things.persistence.actors;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.signals.events.things.AclEntryCreated;
import org.eclipse.ditto.signals.events.things.AclEntryDeleted;
import org.eclipse.ditto.signals.events.things.AclEntryModified;
import org.eclipse.ditto.signals.events.things.AclModified;
import org.eclipse.ditto.signals.events.things.AttributeCreated;
import org.eclipse.ditto.signals.events.things.AttributeDeleted;
import org.eclipse.ditto.signals.events.things.AttributeModified;
import org.eclipse.ditto.signals.events.things.AttributesCreated;
import org.eclipse.ditto.signals.events.things.AttributesDeleted;
import org.eclipse.ditto.signals.events.things.AttributesModified;
import org.eclipse.ditto.signals.events.things.FeatureCreated;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionCreated;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionDeleted;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionModified;
import org.eclipse.ditto.signals.events.things.FeatureDeleted;
import org.eclipse.ditto.signals.events.things.FeatureModified;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesDeleted;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesModified;
import org.eclipse.ditto.signals.events.things.FeaturePropertyCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertyDeleted;
import org.eclipse.ditto.signals.events.things.FeaturePropertyModified;
import org.eclipse.ditto.signals.events.things.FeaturesCreated;
import org.eclipse.ditto.signals.events.things.FeaturesDeleted;
import org.eclipse.ditto.signals.events.things.FeaturesModified;
import org.eclipse.ditto.signals.events.things.PolicyIdCreated;
import org.eclipse.ditto.signals.events.things.PolicyIdModified;
import org.eclipse.ditto.signals.events.things.ThingCreated;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.eclipse.ditto.signals.events.things.ThingModified;

public class ThingEventHandlers implements HandleThingEvent {

    private static final Map<Class<?>, HandleThingEvent<?>> map = new HashMap<>();

    static {
        // # Thing Creation
        match(ThingCreated.class,
                (thingCreated, thing, revisionNumber) -> thingCreated.getThing().toBuilder()
                        .setLifecycle(ThingLifecycle.ACTIVE)
                        .setRevision(revisionNumber)
                        .setModified(thingCreated.getTimestamp().orElse(null))
                        .build());

        // # Thing Modification
        match(ThingModified.class, (tm, thing, revisionNumber) -> {
            // we need to use the current thing as base otherwise we would loose its state
            final ThingBuilder.FromCopy copyBuilder = thing.toBuilder().setLifecycle(ThingLifecycle.ACTIVE)
                    .setRevision(revisionNumber)
                    .setModified(tm.getTimestamp().orElse(null));

            mergeThingModifications(tm.getThing(), copyBuilder);

            return copyBuilder.build();
        });

        // # Thing Deletion
        match(ThingDeleted.class, (td, thing, revisionNumber) -> {
            if (thing != null) {
                return thing.toBuilder()
                        .setLifecycle(ThingLifecycle.DELETED)
                        .setRevision(revisionNumber)
                        .setModified(td.getTimestamp().orElse(null))
                        .build();
            } else {
                // log.warning("Thing was null when 'ThingDeleted' event should have been applied on recovery.");
                return null;
            }
        });

        // # ACL Modification
        match(AclModified.class, (tam, thing, revisionNumber) -> thing.toBuilder()
                .removeAllPermissions()
                .setPermissions(tam.getAccessControlList())
                .setRevision(revisionNumber)
                .setModified(tam.getTimestamp().orElse(null))
                .build());

        // # ACL Entry Creation
        match(AclEntryCreated.class, (aec, thing, revisionNumber) -> thing.toBuilder()
                .setPermissions(aec.getAclEntry())
                .setRevision(revisionNumber)
                .setModified(aec.getTimestamp().orElse(null))
                .build());

        // # ACL Entry Modification
        match(AclEntryModified.class, (taem, thing, revisionNumber) -> thing.toBuilder()
                .setPermissions(taem.getAclEntry())
                .setRevision(revisionNumber)
                .setModified(taem.getTimestamp().orElse(null))
                .build());

        // # ACL Entry Deletion
        match(AclEntryDeleted.class, (aed, thing, revisionNumber) -> thing.toBuilder()
                .removePermissionsOf(aed.getAuthorizationSubject())
                .setRevision(revisionNumber)
                .setModified(aed.getTimestamp().orElse(null))
                .build());

        // # Attributes Creation
        match(AttributesCreated.class, (ac, thing, revisionNumber) -> thing.toBuilder()
                .setAttributes(ac.getCreatedAttributes())
                .setRevision(revisionNumber)
                .setModified(ac.getTimestamp().orElse(null))
                .build());

        // # Attributes Modification
        match(AttributesModified.class, (tasm, thing, revisionNumber) -> thing.toBuilder()
                .setAttributes(tasm.getModifiedAttributes())
                .setRevision(revisionNumber)
                .setModified(tasm.getTimestamp().orElse(null))
                .build());

        // # Attribute Modification
        match(AttributeModified.class, (tam, thing, revisionNumber) -> thing.toBuilder()
                .setAttribute(tam.getAttributePointer(), tam.getAttributeValue())
                .setRevision(revisionNumber)
                .setModified(tam.getTimestamp().orElse(null))
                .build());

        // # Attribute Creation
        match(AttributeCreated.class, (ac, thing, revisionNumber) -> thing.toBuilder()
                .setAttribute(ac.getAttributePointer(), ac.getAttributeValue())
                .setRevision(revisionNumber)
                .setModified(ac.getTimestamp().orElse(null))
                .build());

        // # Attributes Deletion
        match(AttributesDeleted.class, (tasd, thing, revisionNumber) -> thing.toBuilder()
                .removeAllAttributes()
                .setRevision(revisionNumber)
                .setModified(tasd.getTimestamp().orElse(null))
                .build());

        // # Attribute Deletion
        match(AttributeDeleted.class, (tad, thing, revisionNumber) -> thing.toBuilder()
                .removeAttribute(tad.getAttributePointer())
                .setRevision(revisionNumber)
                .setModified(tad.getTimestamp().orElse(null))
                .build());

        // # Features Modification
        match(FeaturesModified.class, (fm, thing, revisionNumber) -> thing.toBuilder()
                .removeAllFeatures()
                .setFeatures(fm.getFeatures())
                .setRevision(revisionNumber)
                .setModified(fm.getTimestamp().orElse(null))
                .build());

        // # Features Creation
        match(FeaturesCreated.class, (fc, thing, revisionNumber) -> thing.toBuilder()
                .setFeatures(fc.getFeatures())
                .setRevision(revisionNumber)
                .setModified(fc.getTimestamp().orElse(null))
                .build());

        // # Features Deletion
        match(FeaturesDeleted.class, (fd, thing, revisionNumber) -> thing.toBuilder()
                .removeAllFeatures()
                .setRevision(revisionNumber)
                .setModified(fd.getTimestamp().orElse(null))
                .build());

        // # Feature Creation
        match(FeatureCreated.class, (fc, thing, revisionNumber) -> thing.toBuilder()
                .setFeature(fc.getFeature())
                .setRevision(revisionNumber)
                .setModified(fc.getTimestamp().orElse(null))
                .build());

        // # Feature Modification
        match(FeatureModified.class, (fm, thing, revisionNumber) -> thing.toBuilder()
                .setFeature(fm.getFeature())
                .setRevision(revisionNumber)
                .setModified(fm.getTimestamp().orElse(null))
                .build());

        // # Feature Deletion
        match(FeatureDeleted.class, (fd, thing, revisionNumber) -> thing.toBuilder()
                .removeFeature(fd.getFeatureId())
                .setRevision(revisionNumber)
                .setModified(fd.getTimestamp().orElse(null))
                .build());

        // # Feature Definition Creation
        match(FeatureDefinitionCreated.class, (fdc, thing, revisionNumber) -> thing.toBuilder()
                .setFeatureDefinition(fdc.getFeatureId(), fdc.getDefinition())
                .setRevision(revisionNumber)
                .setModified(fdc.getTimestamp().orElse(null))
                .build());

        // # Feature Definition Modification
        match(FeatureDefinitionModified.class, (fdm, thing, revisionNumber) -> thing.toBuilder()
                .setFeatureDefinition(fdm.getFeatureId(), fdm.getDefinition())
                .setRevision(revisionNumber)
                .setModified(fdm.getTimestamp().orElse(null))
                .build());

        // # Feature Definition Deletion
        match(FeatureDefinitionDeleted.class, (fdd, thing, revisionNumber) -> thing.toBuilder()
                .removeFeatureDefinition(fdd.getFeatureId())
                .setRevision(revisionNumber)
                .setModified(fdd.getTimestamp().orElse(null))
                .build());

        // # Feature Properties Creation
        match(FeaturePropertiesCreated.class, (fpc, thing, revisionNumber) -> thing.toBuilder()
                .setFeatureProperties(fpc.getFeatureId(), fpc.getProperties())
                .setRevision(revisionNumber)
                .setModified(fpc.getTimestamp().orElse(null))
                .build());

        // # Feature Properties Modification
        match(FeaturePropertiesModified.class, (fpm, thing, revisionNumber) -> thing.toBuilder()
                .setFeatureProperties(fpm.getFeatureId(), fpm.getProperties())
                .setRevision(revisionNumber)
                .setModified(fpm.getTimestamp().orElse(null))
                .build());

        // # Feature Properties Deletion
        match(FeaturePropertiesDeleted.class, (fpd, thing, revisionNumber) -> thing.toBuilder()
                .removeFeatureProperties(fpd.getFeatureId())
                .setRevision(revisionNumber)
                .setModified(fpd.getTimestamp().orElse(null))
                .build());

        // # Feature Property Creation
        match(FeaturePropertyCreated.class, (fpc, thing, revisionNumber) -> thing.toBuilder()
                .setFeatureProperty(fpc.getFeatureId(), fpc.getPropertyPointer(), fpc.getPropertyValue())
                .setRevision(revisionNumber)
                .setModified(fpc.getTimestamp().orElse(null))
                .build());

        // # Feature Property Modification
        match(FeaturePropertyModified.class, (fpm, thing, revisionNumber) -> thing.toBuilder()
                .setFeatureProperty(fpm.getFeatureId(), fpm.getPropertyPointer(), fpm.getPropertyValue())
                .setRevision(revisionNumber)
                .setModified(fpm.getTimestamp().orElse(null))
                .build());

        // # Feature Property Deletion
        match(FeaturePropertyDeleted.class, (fpd, thing, revisionNumber) -> thing.toBuilder()
                .removeFeatureProperty(fpd.getFeatureId(), fpd.getPropertyPointer())
                .setRevision(revisionNumber)
                .setModified(fpd.getTimestamp().orElse(null))
                .build());

        // # Policy ID Creation
        match(PolicyIdCreated.class, (pic, thing, revisionNumber) -> {
            final ThingBuilder.FromCopy thingBuilder = thing.toBuilder();
            thingBuilder.setPolicyId(pic.getPolicyId());
            return thingBuilder.setRevision(revisionNumber)
                    .setModified(pic.getTimestamp().orElse(null))
                    .build();
        });

        // # Policy ID Modification
        match(PolicyIdModified.class, (pim, thing, revisionNumber) -> {
            final ThingBuilder.FromCopy thingBuilder = thing.toBuilder();
            thingBuilder.setPolicyId(pim.getPolicyId());
            return thingBuilder.setRevision(revisionNumber)
                    .setModified(pim.getTimestamp().orElse(null))
                    .build();
        });

    }

    private static <T> void match(final Class<T> cls, final HandleThingEvent<T> apply) {
        map.put(cls, apply);
    }

    @Override
    public Thing handle(final Object event, final Thing thing, final long revisionNumber) {
        final HandleThingEvent handler = map.get(event.getClass());
        if (handler != null) {
            return handler.handle(event, thing, revisionNumber);
        } else {
            return null;
        }
    }

    /**
     * Merges the modifications from {@code thingWithModifications} to {@code builder}. Merge is implemented very
     * simple: All first level fields of {@code thingWithModifications} overwrite the first level fields of {@code
     * builder}. If a field does not exist in {@code thingWithModifications}, a maybe existing field in {@code
     * builder} remains unchanged.
     *
     * @param thingWithModifications the thing containing the modifications.
     * @param builder the builder to be modified.
     */
    private static void mergeThingModifications(final Thing thingWithModifications,
            final ThingBuilder.FromCopy builder) {
        thingWithModifications.getPolicyId().ifPresent(builder::setPolicyId);
        thingWithModifications.getAccessControlList().ifPresent(builder::setPermissions);
        thingWithModifications.getAttributes().ifPresent(builder::setAttributes);
        thingWithModifications.getFeatures().ifPresent(builder::setFeatures);
    }
}
