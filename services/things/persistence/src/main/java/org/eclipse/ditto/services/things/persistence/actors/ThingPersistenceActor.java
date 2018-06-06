/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.things.persistence.actors;

import java.text.MessageFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.Validator;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.AclInvalidException;
import org.eclipse.ditto.model.things.AclNotAllowedException;
import org.eclipse.ditto.model.things.AclValidator;
import org.eclipse.ditto.model.things.Attributes;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.Permission;
import org.eclipse.ditto.model.things.PolicyIdMissingException;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.models.things.ThingsMessagingConstants;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.services.things.persistence.snapshotting.DittoThingSnapshotter;
import org.eclipse.ditto.services.things.persistence.snapshotting.ThingSnapshotter;
import org.eclipse.ditto.services.things.starter.util.ConfigKeys;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.base.WithThingId;
import org.eclipse.ditto.signals.base.WithType;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.commands.things.exceptions.AclModificationInvalidException;
import org.eclipse.ditto.signals.commands.things.exceptions.AclNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.AttributeNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.AttributesNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureDefinitionNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertiesNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertyNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturesNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.PolicyIdNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingConflictException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAclEntry;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAclEntryResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttribute;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributeResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributes;
import org.eclipse.ditto.signals.commands.things.modify.DeleteAttributesResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeature;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperties;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturePropertiesResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturePropertyResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatures;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAcl;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclEntry;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclEntryResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAclResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttribute;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributeResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributes;
import org.eclipse.ditto.signals.commands.things.modify.ModifyAttributesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperties;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturePropertiesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturePropertyResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatures;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyPolicyId;
import org.eclipse.ditto.signals.commands.things.modify.ModifyPolicyIdResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.ThingModifyCommandResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAcl;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclEntry;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclEntryResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAclResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttribute;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributeResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributes;
import org.eclipse.ditto.signals.commands.things.query.RetrieveAttributesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeature;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDefinitionResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperties;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertiesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperty;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertyResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatures;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturesResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrievePolicyId;
import org.eclipse.ditto.signals.commands.things.query.RetrievePolicyIdResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
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
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingModified;
import org.eclipse.ditto.signals.events.things.ThingModifiedEvent;

import com.typesafe.config.Config;

import akka.ConfigurationException;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.cluster.sharding.ClusterSharding;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.FI;
import akka.japi.pf.ReceiveBuilder;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.RecoveryCompleted;
import akka.persistence.SnapshotOffer;
import scala.concurrent.duration.Duration;

/**
 * PersistentActor which "knows" the state of a single {@link Thing}.
 */
public final class ThingPersistenceActor extends AbstractPersistentActor implements ThingPersistenceActorInterface {

    /**
     * The prefix of the persistenceId for Things.
     */
    public static final String PERSISTENCE_ID_PREFIX = "thing:";

    /**
     * The ID of the journal plugin this persistence actor uses.
     */
    public static final String JOURNAL_PLUGIN_ID = "akka-contrib-mongodb-persistence-things-journal";

    /**
     * The ID of the snapshot plugin this persistence actor uses.
     */
    public static final String SNAPSHOT_PLUGIN_ID = "akka-contrib-mongodb-persistence-things-snapshots";

    private static final String UNHANDLED_MESSAGE_TEMPLATE =
            "This Thing Actor did not handle the requested Thing with ID <{0}>!";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final String thingId;
    private final ActorRef pubSubMediator;
    private final java.time.Duration activityCheckInterval;
    private final java.time.Duration activityCheckDeletedInterval;
    private final Receive handleThingEvents;
    private final ThingSnapshotter<?, ?> thingSnapshotter;
    private final long snapshotThreshold;
    private long accessCounter;
    private Cancellable activityChecker;
    private Thing thing;

    private ThingPersistenceActor(final String thingId,
            final ActorRef pubSubMediator,
            final ThingSnapshotter.Create thingSnapshotterCreate) {

        this.thingId = thingId;
        this.pubSubMediator = pubSubMediator;

        final Config config = getContext().system().settings().config();
        activityCheckInterval = config.getDuration(ConfigKeys.Thing.ACTIVITY_CHECK_INTERVAL);
        activityCheckDeletedInterval = config.getDuration(ConfigKeys.Thing.ACTIVITY_CHECK_DELETED_INTERVAL);

        // Activity checking
        final long configuredSnapshotThreshold = config.getLong(ConfigKeys.Thing.SNAPSHOT_THRESHOLD);
        if (configuredSnapshotThreshold < 0) {
            throw new ConfigurationException(String.format("Config setting '%s' must be positive, but is: %d.",
                    ConfigKeys.Thing.SNAPSHOT_THRESHOLD, configuredSnapshotThreshold));
        }
        snapshotThreshold = configuredSnapshotThreshold;

        // Snapshotting
        final java.time.Duration snapshotInterval = config.getDuration(ConfigKeys.Thing.SNAPSHOT_INTERVAL);
        final boolean snapshotDeleteOld = config.getBoolean(ConfigKeys.Thing.SNAPSHOT_DELETE_OLD);
        final boolean eventsDeleteOld = config.getBoolean(ConfigKeys.Thing.EVENTS_DELETE_OLD);
        thingSnapshotter =
                thingSnapshotterCreate.apply(this, pubSubMediator, snapshotDeleteOld, eventsDeleteOld, log,
                        snapshotInterval);

        handleThingEvents = ReceiveBuilder.create()
                // # Thing Creation
                .match(ThingCreated.class, tc -> thing = tc.getThing().toBuilder()
                        .setLifecycle(ThingLifecycle.ACTIVE)
                        .setRevision(getRevisionNumber())
                        .setModified(tc.getTimestamp().orElse(null))
                        .build())

                // # Thing Modification
                .match(ThingModified.class, tm -> {
                    // we need to use the current thing as base otherwise we would loose its state
                    final ThingBuilder.FromCopy copyBuilder = thing.toBuilder().setLifecycle(ThingLifecycle.ACTIVE)
                            .setRevision(getRevisionNumber())
                            .setModified(tm.getTimestamp().orElse(null));

                    mergeThingModifications(tm.getThing(), copyBuilder);

                    thing = copyBuilder.build();
                })

                // # Thing Deletion
                .match(ThingDeleted.class, td -> {
                    if (thing != null) {
                        thing = thing.toBuilder()
                                .setLifecycle(ThingLifecycle.DELETED)
                                .setRevision(getRevisionNumber())
                                .setModified(td.getTimestamp().orElse(null))
                                .build();
                    } else {
                        log.warning("Thing was null when 'ThingDeleted' event should have been applied on recovery.");
                    }
                })

                // # ACL Modification
                .match(AclModified.class, tam -> thing = thing.toBuilder()
                        .removeAllPermissions()
                        .setPermissions(tam.getAccessControlList())
                        .setRevision(getRevisionNumber())
                        .setModified(tam.getTimestamp().orElse(null))
                        .build())

                // # ACL Entry Creation
                .match(AclEntryCreated.class, aec -> thing = thing.toBuilder()
                        .setPermissions(aec.getAclEntry())
                        .setRevision(getRevisionNumber())
                        .setModified(aec.getTimestamp().orElse(null))
                        .build())

                // # ACL Entry Modification
                .match(AclEntryModified.class, taem -> thing = thing.toBuilder()
                        .setPermissions(taem.getAclEntry())
                        .setRevision(getRevisionNumber())
                        .setModified(taem.getTimestamp().orElse(null))
                        .build())

                // # ACL Entry Deletion
                .match(AclEntryDeleted.class, taed -> thing = thing.toBuilder()
                        .removePermissionsOf(taed.getAuthorizationSubject())
                        .setRevision(getRevisionNumber())
                        .setModified(taed.getTimestamp().orElse(null))
                        .build())

                // # Attributes Creation
                .match(AttributesCreated.class, ac -> thing = thing.toBuilder()
                        .setAttributes(ac.getCreatedAttributes())
                        .setRevision(getRevisionNumber())
                        .setModified(ac.getTimestamp().orElse(null))
                        .build())

                // # Attributes Modification
                .match(AttributesModified.class, tasm -> thing = thing.toBuilder()
                        .setAttributes(tasm.getModifiedAttributes())
                        .setRevision(getRevisionNumber())
                        .setModified(tasm.getTimestamp().orElse(null))
                        .build())

                // # Attribute Modification
                .match(AttributeModified.class, tam -> thing = thing.toBuilder()
                        .setAttribute(tam.getAttributePointer(), tam.getAttributeValue())
                        .setRevision(getRevisionNumber())
                        .setModified(tam.getTimestamp().orElse(null))
                        .build())

                // # Attribute Creation
                .match(AttributeCreated.class, ac -> thing = thing.toBuilder()
                        .setAttribute(ac.getAttributePointer(), ac.getAttributeValue())
                        .setRevision(getRevisionNumber())
                        .setModified(ac.getTimestamp().orElse(null))
                        .build())

                // # Attributes Deletion
                .match(AttributesDeleted.class, tasd -> thing = thing.toBuilder()
                        .removeAllAttributes()
                        .setRevision(getRevisionNumber())
                        .setModified(tasd.getTimestamp().orElse(null))
                        .build())

                // # Attribute Deletion
                .match(AttributeDeleted.class, tad -> thing = thing.toBuilder()
                        .removeAttribute(tad.getAttributePointer())
                        .setRevision(getRevisionNumber())
                        .setModified(tad.getTimestamp().orElse(null))
                        .build())

                // # Features Modification
                .match(FeaturesModified.class, fm -> thing = thing.toBuilder()
                        .removeAllFeatures()
                        .setFeatures(fm.getFeatures())
                        .setRevision(getRevisionNumber())
                        .setModified(fm.getTimestamp().orElse(null))
                        .build())

                // # Features Creation
                .match(FeaturesCreated.class, fc -> thing = thing.toBuilder()
                        .setFeatures(fc.getFeatures())
                        .setRevision(getRevisionNumber())
                        .setModified(fc.getTimestamp().orElse(null))
                        .build())

                // # Features Deletion
                .match(FeaturesDeleted.class, fd -> thing = thing.toBuilder()
                        .removeAllFeatures()
                        .setRevision(getRevisionNumber())
                        .setModified(fd.getTimestamp().orElse(null))
                        .build())

                // # Feature Creation
                .match(FeatureCreated.class, fc -> thing = thing.toBuilder()
                        .setFeature(fc.getFeature())
                        .setRevision(getRevisionNumber())
                        .setModified(fc.getTimestamp().orElse(null))
                        .build())

                // # Feature Modification
                .match(FeatureModified.class, fm -> thing = thing.toBuilder()
                        .setFeature(fm.getFeature())
                        .setRevision(getRevisionNumber())
                        .setModified(fm.getTimestamp().orElse(null))
                        .build())

                // # Feature Deletion
                .match(FeatureDeleted.class, fd -> thing = thing.toBuilder()
                        .removeFeature(fd.getFeatureId())
                        .setRevision(getRevisionNumber())
                        .setModified(fd.getTimestamp().orElse(null))
                        .build())

                // # Feature Definition Creation
                .match(FeatureDefinitionCreated.class, fdc -> thing = thing.toBuilder()
                        .setFeatureDefinition(fdc.getFeatureId(), fdc.getDefinition())
                        .setRevision(getRevisionNumber())
                        .setModified(fdc.getTimestamp().orElse(null))
                        .build())

                // # Feature Definition Modification
                .match(FeatureDefinitionModified.class, fdm -> thing = thing.toBuilder()
                        .setFeatureDefinition(fdm.getFeatureId(), fdm.getDefinition())
                        .setRevision(getRevisionNumber())
                        .setModified(fdm.getTimestamp().orElse(null))
                        .build())

                // # Feature Definition Deletion
                .match(FeatureDefinitionDeleted.class, fdd -> thing = thing.toBuilder()
                        .removeFeatureDefinition(fdd.getFeatureId())
                        .setRevision(getRevisionNumber())
                        .setModified(fdd.getTimestamp().orElse(null))
                        .build())

                // # Feature Properties Creation
                .match(FeaturePropertiesCreated.class, fpc -> thing = thing.toBuilder()
                        .setFeatureProperties(fpc.getFeatureId(), fpc.getProperties())
                        .setRevision(getRevisionNumber())
                        .setModified(fpc.getTimestamp().orElse(null))
                        .build())

                // # Feature Properties Modification
                .match(FeaturePropertiesModified.class, fpm -> thing = thing.toBuilder()
                        .setFeatureProperties(fpm.getFeatureId(), fpm.getProperties())
                        .setRevision(getRevisionNumber())
                        .setModified(fpm.getTimestamp().orElse(null))
                        .build())

                // # Feature Properties Deletion
                .match(FeaturePropertiesDeleted.class, fpd -> thing = thing.toBuilder()
                        .removeFeatureProperties(fpd.getFeatureId())
                        .setRevision(getRevisionNumber())
                        .setModified(fpd.getTimestamp().orElse(null))
                        .build())

                // # Feature Property Creation
                .match(FeaturePropertyCreated.class, fpc -> thing = thing.toBuilder()
                        .setFeatureProperty(fpc.getFeatureId(), fpc.getPropertyPointer(), fpc.getPropertyValue())
                        .setRevision(getRevisionNumber())
                        .setModified(fpc.getTimestamp().orElse(null))
                        .build())

                // # Feature Property Modification
                .match(FeaturePropertyModified.class, fpm -> thing = thing.toBuilder()
                        .setFeatureProperty(fpm.getFeatureId(), fpm.getPropertyPointer(), fpm.getPropertyValue())
                        .setRevision(getRevisionNumber())
                        .setModified(fpm.getTimestamp().orElse(null))
                        .build())

                // # Feature Property Deletion
                .match(FeaturePropertyDeleted.class, fpd -> thing = thing.toBuilder()
                        .removeFeatureProperty(fpd.getFeatureId(), fpd.getPropertyPointer())
                        .setRevision(getRevisionNumber())
                        .setModified(fpd.getTimestamp().orElse(null))
                        .build())

                // # Policy ID Creation
                .match(PolicyIdCreated.class, pic -> {
                    final ThingBuilder.FromCopy thingBuilder = thing.toBuilder();
                    thingBuilder.setPolicyId(pic.getPolicyId());
                    thing = thingBuilder.setRevision(getRevisionNumber())
                            .setModified(pic.getTimestamp().orElse(null))
                            .build();
                })

                // # Policy ID Modification
                .match(PolicyIdModified.class, pim -> {
                    final ThingBuilder.FromCopy thingBuilder = thing.toBuilder();
                    thingBuilder.setPolicyId(pim.getPolicyId());
                    thing = thingBuilder.setRevision(getRevisionNumber())
                            .setModified(pim.getTimestamp().orElse(null))
                            .build();
                })

                .build();
    }

    /**
     * Merges the modifications from {@code thingWithModifications} to {@code builder}. Merge is implemented very
     * simple: All first level fields of {@code thingWithModifications} overwrite the first level fields of {@code
     * builder}. If a field does not exist in {@code thingWithModifications}, a maybe existing field in {@code
     * builder} remains unchanged.
     * @param thingWithModifications the thing containing the modifications.
     * @param builder the builder to be modified.
     */
    private void mergeThingModifications(final Thing thingWithModifications, final ThingBuilder.FromCopy builder) {
        thingWithModifications.getPolicyId().ifPresent(builder::setPolicyId);
        thingWithModifications.getAccessControlList().ifPresent(builder::setPermissions);
        thingWithModifications.getAttributes().ifPresent(builder::setAttributes);
        thingWithModifications.getFeatures().ifPresent(builder::setFeatures);
    }

    /**
     * Creates Akka configuration object {@link Props} for this ThingPersistenceActor.
     *
     * @param thingId the Thing ID this Actor manages.
     * @param pubSubMediator the PubSub mediator actor.
     * @param thingSnapshotterCreate creator of {@code ThingSnapshotter} objects.
     * @return the Akka configuration Props object
     */
    public static Props props(final String thingId,
            final ActorRef pubSubMediator,
            final ThingSnapshotter.Create thingSnapshotterCreate) {

        return Props.create(ThingPersistenceActor.class, new Creator<ThingPersistenceActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ThingPersistenceActor create() {
                return new ThingPersistenceActor(thingId, pubSubMediator, thingSnapshotterCreate);
            }
        });
    }

    /**
     * Creates a default Akka configuration object {@link Props} for this ThingPersistenceActor using sudo commands
     * for external snapshot requests.
     *
     * @param thingId the Thing ID this Actor manages.
     * @param pubSubMediator the PubSub mediator actor.
     * @return the Akka configuration Props object
     */
    public static Props props(final String thingId, final ActorRef pubSubMediator) {
        return Props.create(ThingPersistenceActor.class, new Creator<ThingPersistenceActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ThingPersistenceActor create() {
                return new ThingPersistenceActor(thingId, pubSubMediator, DittoThingSnapshotter::getInstance);
            }
        });
    }

    /**
     * Retrieves the ShardRegion of "Things". ThingCommands can be sent to this region which handles dispatching them
     * in the cluster (onto the cluster node containing the shard).
     *
     * @param system the ActorSystem in which to lookup the ShardRegion.
     * @return the ActorRef to the ShardRegion.
     */
    public static ActorRef getShardRegion(final ActorSystem system) {
        return ClusterSharding.get(system).shardRegion(ThingsMessagingConstants.SHARD_REGION);
    }

    private static Instant eventTimestamp() {
        return Instant.now();
    }

    private static Thing enhanceThingWithLifecycle(final Thing thing) {
        final ThingBuilder.FromCopy thingBuilder = ThingsModelFactory.newThingBuilder(thing);
        if (!thing.getLifecycle().isPresent()) {
            thingBuilder.setLifecycle(ThingLifecycle.ACTIVE);
        }

        return thingBuilder.build();
    }

    private long getRevisionNumber() {
        return lastSequenceNr();
    }

    @Nonnull
    @Override
    public Thing getThing() {
        return thing;
    }

    @Nonnull
    @Override
    public String getThingId() {
        return thingId;
    }

    private void scheduleCheckForThingActivity(final long intervalInSeconds) {
        log.debug("Scheduling for Activity Check in <{}> seconds.", intervalInSeconds);
        // if there is a previous activity checker, cancel it
        if (activityChecker != null) {
            activityChecker.cancel();
        }
        // send a message to ourselves:
        activityChecker = getContext()
                .system()
                .scheduler()
                .scheduleOnce(Duration.apply(intervalInSeconds, TimeUnit.SECONDS), getSelf(),
                        new CheckForActivity(getRevisionNumber(), accessCounter), getContext().dispatcher(), null);
    }

    @Override
    public String persistenceId() {
        return PERSISTENCE_ID_PREFIX + thingId;
    }

    @Override
    public String journalPluginId() {
        return JOURNAL_PLUGIN_ID;
    }

    @Override
    public String snapshotPluginId() {
        return SNAPSHOT_PLUGIN_ID;
    }

    @Override
    public void postStop() {
        super.postStop();
        thingSnapshotter.postStop();
        if (activityChecker != null) {
            activityChecker.cancel();
        }
    }

    @Override
    public Receive createReceive() {
        /*
         * First no Thing for the ID exists at all. Thus the only command this Actor reacts to is CreateThing.
         * This behaviour changes as soon as a Thing was created.
         */
        return new StrategyAwareReceiveBuilder()
                .match(new CreateThingStrategy())
                .matchAny(new MatchAnyDuringInitializeStrategy())
                .setPeekConsumer(getIncomingMessagesLoggerOrNull())
                .build();
    }

    @Override
    public Receive createReceiveRecover() {
        // defines how state is updated during recovery
        return handleThingEvents.orElse(ReceiveBuilder.create()

                // # Snapshot handling
                .match(SnapshotOffer.class, ss -> {
                    log.debug("Got SnapshotOffer: {}", ss);
                    thing = thingSnapshotter.recoverThingFromSnapshotOffer(ss);
                })

                // # Recovery handling
                .match(RecoveryCompleted.class, rc -> {
                    if (thing != null) {
                        thing = enhanceThingWithLifecycle(thing);
                        log.debug("Thing <{}> was recovered.", thingId);

                        if (isThingActive()) {
                            becomeThingCreatedHandler();
                        } else {
                            // expect life cycle to be DELETED. if it's not, then act as if this thing is deleted.
                            if (!isThingDeleted()) {
                                // life cycle isn't known, act as
                                log.error("Unknown lifecycle state <{}> for Thing <{}>.", thing.getLifecycle(),
                                        thingId);
                            }
                            becomeThingDeletedHandler();
                        }

                    }
                })

                // # Handle unknown
                .matchAny(m -> log.warning("Unknown recover message: {}", m)).build());
    }

    /*
     * Now as the {@code thing} reference is not {@code null} the strategies which act on this reference can
     * be activated. In return the strategy for the CreateThing command is not needed anymore.
     */
    private void becomeThingCreatedHandler() {
        final Collection<ReceiveStrategy<?>> thingCreatedStrategies = initThingCreatedStrategies();
        final Receive receive = new StrategyAwareReceiveBuilder()
                .matchEach(thingCreatedStrategies)
                .matchAny(new MatchAnyAfterInitializeStrategy())
                .setPeekConsumer(getIncomingMessagesLoggerOrNull())
                .build();

        getContext().become(receive, true);
        getContext().getParent().tell(ThingSupervisorActor.ManualReset.INSTANCE, getSelf());

        scheduleCheckForThingActivity(activityCheckInterval.getSeconds());
        thingSnapshotter.startMaintenanceSnapshots();
    }

    private Collection<ReceiveStrategy<?>> initThingCreatedStrategies() {
        final Collection<ReceiveStrategy<?>> result = new ArrayList<>();

        // Thing level
        result.add(new ThingConflictStrategy());
        result.add(new ModifyThingStrategy());
        result.add(new RetrieveThingStrategy());
        result.add(new DeleteThingStrategy());

        // Policy ID
        result.add(new RetrievePolicyIdStrategy());
        result.add(new ModifyPolicyIdStrategy());

        // ACL
        result.add(new ModifyAclStrategy());
        result.add(new RetrieveAclStrategy());
        result.add(new ModifyAclEntryStrategy());
        result.add(new RetrieveAclEntryStrategy());
        result.add(new DeleteAclEntryStrategy());

        // Attributes
        result.add(new ModifyAttributesStrategy());
        result.add(new ModifyAttributeStrategy());
        result.add(new RetrieveAttributesStrategy());
        result.add(new RetrieveAttributeStrategy());
        result.add(new DeleteAttributesStrategy());
        result.add(new DeleteAttributeStrategy());

        // Features
        result.add(new ModifyFeaturesStrategy());
        result.add(new ModifyFeatureStrategy());
        result.add(new RetrieveFeaturesStrategy());
        result.add(new RetrieveFeatureStrategy());
        result.add(new DeleteFeaturesStrategy());
        result.add(new DeleteFeatureStrategy());

        // Feature Definition
        result.add(new ModifyFeatureDefinitionStrategy());
        result.add(new RetrieveFeatureDefinitionStrategy());
        result.add(new DeleteFeatureDefinitionStrategy());

        // Feature Properties
        result.add(new ModifyFeaturePropertiesStrategy());
        result.add(new ModifyFeaturePropertyStrategy());
        result.add(new RetrieveFeaturePropertiesStrategy());
        result.add(new RetrieveFeaturePropertyStrategy());
        result.add(new DeleteFeaturePropertiesStrategy());
        result.add(new DeleteFeaturePropertyStrategy());

        // sudo
        result.add(new SudoRetrieveThingStrategy());

        // TakeSnapshot
        result.addAll(thingSnapshotter.strategies());

        // Persistence specific
        result.add(new CheckForActivityStrategy());

        return result;
    }

    private void becomeThingDeletedHandler() {
        final Collection<ReceiveStrategy<?>> thingDeletedStrategies = initThingDeletedStrategies();
        final Receive receive = new StrategyAwareReceiveBuilder()
                .matchEach(thingDeletedStrategies)
                .matchAny(new ThingNotFoundStrategy())
                .setPeekConsumer(getIncomingMessagesLoggerOrNull())
                .build();

        getContext().become(receive, true);
        getContext().getParent().tell(ThingSupervisorActor.ManualReset.INSTANCE, getSelf());

        /* check in the next X minutes and therefore
         * - stay in-memory for a short amount of minutes after deletion
         * - get a Snapshot when removed from memory
         */
        scheduleCheckForThingActivity(activityCheckDeletedInterval.getSeconds());
        thingSnapshotter.stopMaintenanceSnapshots();
    }

    private Collection<ReceiveStrategy<?>> initThingDeletedStrategies() {
        final Collection<ReceiveStrategy<?>> result = new ArrayList<>();
        result.add(new CreateThingStrategy());

        // TakeSnapshot
        result.addAll(thingSnapshotter.strategies());

        // Persistence specific
        result.add(new CheckForActivityStrategy());

        return result;
    }

    private <A extends ThingModifiedEvent> void persistAndApplyEvent(final A event, final Consumer<A> handler) {

        final A modifiedEvent;
        if (thing != null) {
            // set version of event to the version of the thing
            final DittoHeaders newHeaders = event.getDittoHeaders().toBuilder()
                    .schemaVersion(thing.getImplementedSchemaVersion())
                    .build();
            modifiedEvent = (A) event.setDittoHeaders(newHeaders);
        } else {
            modifiedEvent = event;
        }

        if (modifiedEvent.getDittoHeaders().isDryRun()) {
            handler.accept(modifiedEvent);
        } else {
            persistEvent(modifiedEvent, persistedEvent -> {
                // after the event was persisted, apply the event on the current actor state
                applyEvent(persistedEvent);

                handler.accept(persistedEvent);
            });
        }
    }

    private <A extends ThingModifiedEvent> void persistEvent(final A event, final Consumer<A> handler) {
        LogUtil.enhanceLogWithCorrelationId(log, event.getDittoHeaders().getCorrelationId());
        log.debug("About to persist Event <{}>", event.getType());

        persist(event, persistedEvent -> {
            LogUtil.enhanceLogWithCorrelationId(log, event.getDittoHeaders().getCorrelationId());
            log.info("Successfully persisted Event <{}>", event.getType());

            /* the event has to be applied before creating the snapshot, otherwise a snapshot with new
               sequence no (e.g. 2), but old thing revision no (e.g. 1) will be created -> can lead to serious
               aftereffects.
             */
            handler.accept(persistedEvent);

            // save a snapshot if there were too many changes since the last snapshot
            if (snapshotThresholdPassed()) {
                thingSnapshotter.takeSnapshotInternal();
            }
        });
    }

    private boolean snapshotThresholdPassed() {
        if (thingSnapshotter.getLatestSnapshotSequenceNr() > 0) {
            return (getRevisionNumber() - thingSnapshotter.getLatestSnapshotSequenceNr()) > snapshotThreshold;
        } else {
            // there is no snapshot; count the sequence numbers from 0.
            return (getRevisionNumber() + 1) > snapshotThreshold;
        }
    }

    private <A extends ThingModifiedEvent> void applyEvent(final A event) {
        handleThingEvents.onMessage().apply(event);
        notifySubscribers(event);
    }

    private long nextRevision() {
        return getRevisionNumber() + 1;
    }

    /**
     * @return Whether the lifecycle of the Thing is active.
     */
    public boolean isThingActive() {
        return thing.hasLifecycle(ThingLifecycle.ACTIVE);
    }

    @Override
    public boolean isThingDeleted() {
        return null == thing || thing.hasLifecycle(ThingLifecycle.DELETED);
    }

    private void notifySubscribers(final ThingEvent event) {
        // publish the event in the cluster
        // publish via cluster pubSub (as we cannot expect that Websocket sessions interested in this event
        // are running on the same cluster node):
        pubSubMediator.tell(new DistributedPubSubMediator.Publish(ThingEvent.TYPE_PREFIX, event, true), getSelf());
    }

    private void attributesNotFound(final DittoHeaders dittoHeaders) {
        notifySender(AttributesNotAccessibleException.newBuilder(thingId).dittoHeaders(dittoHeaders).build());
    }

    private void attributeNotFound(final JsonPointer attributeKey, final DittoHeaders dittoHeaders) {
        notifySender(AttributeNotAccessibleException.newBuilder(thingId, attributeKey)
                .dittoHeaders(dittoHeaders)
                .build());
    }

    private void featureNotFound(final String featureId, final DittoHeaders dittoHeaders) {
        final FeatureNotAccessibleException featureNotAccessibleException =
                FeatureNotAccessibleException.newBuilder(thingId, featureId).dittoHeaders(dittoHeaders).build();

        notifySender(getSender(), featureNotAccessibleException);
    }

    private void featuresNotFound(final DittoHeaders dittoHeaders) {
        notifySender(getSender(), FeaturesNotAccessibleException.newBuilder(thingId)
                .dittoHeaders(dittoHeaders)
                .build());
    }

    private void notifySender(final WithDittoHeaders message) {
        notifySender(getSender(), message);
    }

    private void notifySender(final ActorRef sender, final WithDittoHeaders message) {
        accessCounter++;
        sender.tell(message, getSelf());
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private void aclInvalid(final Optional<String> message, final AuthorizationContext authContext,
            final DittoHeaders dittoHeaders) {

        log.debug("ACL could not be modified by Authorization Context <{}> due to: {}", authContext,
                message.orElse(null));
        notifySender(getSender(), AclModificationInvalidException.newBuilder(thingId)
                .description(message.orElse(null))
                .dittoHeaders(dittoHeaders)
                .build());
    }

    private void aclEntryNotFound(final AuthorizationSubject authorizationSubject, final DittoHeaders dittoHeaders) {
        notifySender(getSender(), AclNotAccessibleException.newBuilder(thingId, authorizationSubject)
                .dittoHeaders(dittoHeaders)
                .build());
    }

    private void featureDefinitionNotFound(final String featureId, final DittoHeaders dittoHeaders) {
        notifySender(getSender(), FeatureDefinitionNotAccessibleException.newBuilder(thingId, featureId)
                .dittoHeaders(dittoHeaders)
                .build());
    }

    private void featurePropertiesNotFound(final String featureId, final DittoHeaders dittoHeaders) {
        notifySender(getSender(), FeaturePropertiesNotAccessibleException.newBuilder(thingId, featureId)
                .dittoHeaders(dittoHeaders)
                .build());
    }

    private void featurePropertyNotFound(final String featureId, final JsonPointer jsonPointer,
            final DittoHeaders dittoHeaders) {

        notifySender(getSender(), FeaturePropertyNotAccessibleException.newBuilder(thingId, featureId, jsonPointer)
                .dittoHeaders(dittoHeaders)
                .build());
    }

    private Consumer<Object> getIncomingMessagesLoggerOrNull() {
        if (isLogIncomingMessages()) {
            return new LogIncomingMessagesConsumer();
        }
        return null;
    }

    /**
     * Indicates whether the logging of incoming messages is enabled by config or not.
     *
     * @return {@code true} if information about incoming messages should be logged, {@code false} else.
     */
    private boolean isLogIncomingMessages() {
        final ActorSystem actorSystem = getContext().getSystem();
        final Config config = actorSystem.settings().config();

        return config.hasPath(ConfigKeys.THINGS_LOG_INCOMING_MESSAGES) &&
                config.getBoolean(ConfigKeys.THINGS_LOG_INCOMING_MESSAGES);
    }

    /**
     * Message the ThingPersistenceActor can send to itself to check for activity of the Actor and terminate itself
     * if there was no activity since the last check.
     */
    private static final class CheckForActivity {

        private final long currentSequenceNr;
        private final long currentAccessCounter;

        /**
         * Constructs a new {@code CheckForActivity} message containing the current "lastSequenceNo" of the
         * ThingPersistenceActor.
         *
         * @param currentSequenceNr the current {@code lastSequenceNr()} of the ThingPersistenceActor.
         * @param currentAccessCounter the current {@code accessCounter} of the ThingPersistenceActor.
         */
        public CheckForActivity(final long currentSequenceNr, final long currentAccessCounter) {
            this.currentSequenceNr = currentSequenceNr;
            this.currentAccessCounter = currentAccessCounter;
        }

        /**
         * Returns the current {@code ThingsModelFactory.lastSequenceNr()} of the ThingPersistenceActor.
         *
         * @return the current {@code ThingsModelFactory.lastSequenceNr()} of the ThingPersistenceActor.
         */
        public long getCurrentSequenceNr() {
            return currentSequenceNr;
        }

        /**
         * Returns the current {@code accessCounter} of the ThingPersistenceActor.
         *
         * @return the current {@code accessCounter} of the ThingPersistenceActor.
         */
        public long getCurrentAccessCounter() {
            return currentAccessCounter;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final CheckForActivity that = (CheckForActivity) o;
            return Objects.equals(currentSequenceNr, that.currentSequenceNr) &&
                    Objects.equals(currentAccessCounter, that.currentAccessCounter);
        }

        @Override
        public int hashCode() {
            return Objects.hash(currentSequenceNr, currentAccessCounter);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" + "currentSequenceNr=" + currentSequenceNr +
                    ", currentAccessCounter=" + currentAccessCounter + "]";
        }

    }

    /**
     * This strategy handles the {@link CreateThing} command.
     */
    @NotThreadSafe
    private final class CreateThingStrategy extends AbstractReceiveStrategy<CreateThing> {

        /**
         * Constructs a new {@code CreateThingStrategy} object.
         */
        public CreateThingStrategy() {
            super(CreateThing.class, log);
        }

        @Override
        public FI.TypedPredicate<CreateThing> getPredicate() {
            return command -> Objects.equals(thingId, command.getId());
        }

        @Override
        protected void doApply(final CreateThing command) {
            final DittoHeaders commandHeaders = command.getDittoHeaders();

            // Thing not yet created - do so ..
            final Thing newThing;
            try {
                newThing =
                        handleCommandVersion(command.getImplementedSchemaVersion(), command.getThing(), commandHeaders);
            } catch (final DittoRuntimeException e) {
                notifySender(e);
                return;
            }

            // before persisting, check if the Thing is valid and reject if not:
            if (!isValidThing(command.getImplementedSchemaVersion(), newThing, commandHeaders)) {
                return;
            }

            final ThingCreated thingCreated;
            if (JsonSchemaVersion.V_1.equals(command.getImplementedSchemaVersion())) {
                thingCreated = ThingCreated.of(newThing, nextRevision(), eventTimestamp(), commandHeaders);
            }
            // default case handle as v2 and upwards:
            else {
                thingCreated =
                        ThingCreated.of(newThing.setPolicyId(newThing.getPolicyId().orElse(thingId)), nextRevision(),
                                eventTimestamp(), commandHeaders);
            }

            persistAndApplyEvent(thingCreated, event -> {
                notifySender(CreateThingResponse.of(thing, thingCreated.getDittoHeaders()));
                log.debug("Created new Thing with ID <{}>.", thingId);
                becomeThingCreatedHandler();
            });
        }

        private Thing handleCommandVersion(final JsonSchemaVersion version, final Thing thing,
                final DittoHeaders dittoHeaders) {

            if (JsonSchemaVersion.V_1.equals(version)) {
                return enhanceNewThingWithFallbackAcl(enhanceThingWithLifecycle(thing),
                        dittoHeaders.getAuthorizationContext());
            }
            // default case handle as v2 and upwards:
            else {
                //acl is not allowed to be set in v2
                if (thing.getAccessControlList().isPresent()) {
                    throw AclNotAllowedException.newBuilder(thingId).dittoHeaders(dittoHeaders).build();
                }

                // policyId is required for v2
                if (!thing.getPolicyId().isPresent()) {
                    throw PolicyIdMissingException.fromThingIdOnCreate(thingId, dittoHeaders);
                }

                return enhanceThingWithLifecycle(thing);
            }
        }

        /**
         * Retrieves the Thing with first authorization subjects as fallback for the ACL of the Thing if the passed
         * {@code newThing} has no ACL set.
         *
         * @param newThing the new Thing to take as a "base" and to check for presence of ACL inside.
         * @param authContext the AuthorizationContext to take the first AuthorizationSubject as fallback from.
         * @return the really new Thing with guaranteed ACL.
         */
        private Thing enhanceNewThingWithFallbackAcl(final Thing newThing, final AuthorizationContext authContext) {
            final ThingBuilder.FromCopy newThingBuilder = ThingsModelFactory.newThingBuilder(newThing);

            final Boolean isAclEmpty = newThing.getAccessControlList()
                    .map(AccessControlList::isEmpty)
                    .orElse(true);
            if (isAclEmpty) {
                // do the fallback and use the first authorized subject and give all permissions to it:
                final AuthorizationSubject authorizationSubject = authContext.getFirstAuthorizationSubject()
                        .orElseThrow(() -> new NullPointerException("AuthorizationContext does not contain an " +
                                "AuthorizationSubject!"));
                newThingBuilder.setPermissions(authorizationSubject, Thing.MIN_REQUIRED_PERMISSIONS);
            }

            return newThingBuilder.build();
        }

        private boolean isValidThing(final JsonSchemaVersion version, final Thing thing, final DittoHeaders headers) {
            final Optional<AccessControlList> accessControlList = thing.getAccessControlList();
            if (JsonSchemaVersion.V_1.equals(version)) {
                if (accessControlList.isPresent()) {
                    final Validator aclValidator =
                            AclValidator.newInstance(accessControlList.get(), Thing.MIN_REQUIRED_PERMISSIONS);
                    // before persisting, check if the ACL is valid and reject if not:
                    if (!aclValidator.isValid()) {
                        notifySender(getSender(), AclInvalidException.newBuilder(thing.getId().orElse(thingId))
                                .dittoHeaders(headers)
                                .build());
                        return false;
                    }
                } else {
                    return false;
                }
            }
            return true;
        }

        @Override
        public FI.UnitApply<CreateThing> getUnhandledFunction() {
            return command -> {
                throw new IllegalArgumentException(MessageFormat.format(UNHANDLED_MESSAGE_TEMPLATE, command.getId()));
            };
        }

    }

    /**
     * This strategy handles the {@link CreateThing} command for an already existing Thing.
     */
    @NotThreadSafe
    private final class ThingConflictStrategy extends AbstractReceiveStrategy<CreateThing> {

        /**
         * Constructs a new {@code ThingConflictStrategy} object.
         */
        public ThingConflictStrategy() {
            super(CreateThing.class, log);
        }

        @Override
        public FI.TypedPredicate<CreateThing> getPredicate() {
            return command -> Objects.equals(thingId, command.getId());
        }

        @Override
        protected void doApply(final CreateThing command) {
            notifySender(ThingConflictException.newBuilder(command.getId())
                    .dittoHeaders(command.getDittoHeaders())
                    .build());
        }

        @Override
        public FI.UnitApply<CreateThing> getUnhandledFunction() {
            return command -> {
                throw new IllegalArgumentException(MessageFormat.format(UNHANDLED_MESSAGE_TEMPLATE, command.getId()));
            };
        }

    }

    /**
     * This strategy handles the {@link ModifyThing} command for an already existing Thing.
     */
    @NotThreadSafe
    private final class ModifyThingStrategy extends AbstractThingCommandStrategy<ModifyThing> {

        /**
         * Constructs a new {@code ModifyThingStrategy} object.
         */
        public ModifyThingStrategy() {
            super(ModifyThing.class, log);
        }

        @Override
        protected void doApply(final ModifyThing command) {
            if (JsonSchemaVersion.V_1.equals(command.getImplementedSchemaVersion())) {
                handleModifyExistingWithV1Command(command);
            } else {
                // from V2 upwards, use this logic:
                handleModifyExistingWithV2Command(command);
            }
        }

        private void handleModifyExistingWithV1Command(final ModifyThing command) {
            if (JsonSchemaVersion.V_1.equals(thing.getImplementedSchemaVersion())) {
                handleModifyExistingV1WithV1Command(command);
            } else {
                handleModifyExistingV2WithV1Command(command);
            }
        }

        private void handleModifyExistingV1WithV1Command(final ModifyThing command) {
            // if the ACL was modified together with the Thing, an additional check is necessary
            final boolean isCommandAclEmpty = command.getThing()
                    .getAccessControlList()
                    .map(AccessControlList::isEmpty)
                    .orElse(true);
            if (!isCommandAclEmpty) {
                applyModifyCommand(command);
            } else {
                final DittoHeaders dittoHeaders = command.getDittoHeaders();
                final Optional<AccessControlList> existingAccessControlList = thing.getAccessControlList();
                if (existingAccessControlList.isPresent()) {
                    // special apply - take the ACL of the persisted thing instead of the new one in the command:
                    final Thing modifiedThingWithOldAcl = ThingsModelFactory.newThingBuilder(command.getThing())
                            .removeAllPermissions()
                            .setPermissions(existingAccessControlList.get())
                            .build();
                    final ThingModified thingModified =
                            ThingModified.of(modifiedThingWithOldAcl, nextRevision(), eventTimestamp(), dittoHeaders);
                    persistAndApplyEvent(thingModified,
                            event -> notifySender(ModifyThingResponse.modified(thingId, dittoHeaders)));
                } else {
                    log.error("Thing <{}> has no ACL entries even though it is of schema version 1. " +
                            "Persisting the event nevertheless to not block the user because of an " +
                            "unknown internal state.", thingId);
                    final ThingModified thingModified =
                            ThingModified.of(command.getThing(), nextRevision(), eventTimestamp(), dittoHeaders);
                    persistAndApplyEvent(thingModified,
                            event -> notifySender(ModifyThingResponse.modified(thingId, dittoHeaders)));
                }
            }
        }

        private void handleModifyExistingV2WithV1Command(final ModifyThing command) {
            // remove any acl information from command and add the current policy Id
            final Thing thingWithoutAcl = removeACL(copyPolicyId(thing, command.getThing()));
            final ThingModified thingModified =
                    ThingModified.of(thingWithoutAcl, nextRevision(), eventTimestamp(), command.getDittoHeaders());
            persistAndApplyEvent(thingModified,
                    event -> notifySender(ModifyThingResponse.modified(thingId, command.getDittoHeaders())));
        }

        private void handleModifyExistingWithV2Command(final ModifyThing command) {
            if (JsonSchemaVersion.V_1.equals(thing.getImplementedSchemaVersion())) {
                handleModifyExistingV1WithV2Command(command);
            } else {
                handleModifyExistingV2WithV2Command(command);
            }
        }

        /**
         * Handles a {@link org.eclipse.ditto.signals.commands.things.modify.ModifyThing} command that was sent
         * via API v2 and targets a Thing with API version V1.
         */
        private void handleModifyExistingV1WithV2Command(final ModifyThing command) {
            if (containsPolicy(command)) {
                applyModifyCommand(command);
            } else {
                notifySender(getSender(), PolicyIdMissingException.fromThingIdOnUpdate(thingId, command.getDittoHeaders()));
            }
        }

        /**
         * Handles a {@link org.eclipse.ditto.signals.commands.things.modify.ModifyThing} command that was sent
         * via API v2 and targets a Thing with API version V2.
         */
        private void handleModifyExistingV2WithV2Command(final ModifyThing command) {
            // ensure the Thing contains a policy ID
            final Thing thingWithPolicyId =
                    containsPolicyId(command) ? command.getThing() : copyPolicyId(thing, command.getThing());
            applyModifyCommand(ModifyThing.of(command.getThingId(),
                    thingWithPolicyId,
                    null,
                    command.getDittoHeaders()));
        }

        private void applyModifyCommand(final ModifyThing command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();

            // make sure that the ThingModified-Event contains all data contained in the resulting thing (this is
            // required e.g. for updating the search-index)
            final long nextRevision = nextRevision();
            final ThingBuilder.FromCopy modifiedThingBuilder = thing.toBuilder()
                    .setRevision(nextRevision)
                    .setModified(null);
            mergeThingModifications(command.getThing(), modifiedThingBuilder);
            final ThingModified thingModified = ThingModified.of(modifiedThingBuilder.build(), nextRevision,
                    eventTimestamp(), dittoHeaders);

            persistAndApplyEvent(thingModified,
                    event -> notifySender(ModifyThingResponse.modified(thingId, dittoHeaders)));
        }

        private boolean containsPolicy(final ModifyThing command) {
            return containsInitialPolicy(command) || containsPolicyId(command);
        }

        private boolean containsInitialPolicy(final ModifyThing command) {
            return command.getInitialPolicy().isPresent();
        }

        private boolean containsPolicyId(final ModifyThing command) {
            return command.getThing().getPolicyId().isPresent();
        }

        private Thing copyPolicyId(final Thing from, final Thing to) {
            return to.toBuilder()
                    .setPolicyId(from.getPolicyId().orElseGet(() -> {
                        log.error("Thing <{}> is schema version 2 and should therefore contain a policyId", thingId);
                        return null;
                    }))
                    .build();
        }

        private Thing removeACL(final Thing thing) {
            return thing.toBuilder()
                    .removeAllPermissions()
                    .build();
        }

        @Override
        public FI.UnitApply<ModifyThing> getUnhandledFunction() {
            return command -> notifySender(new ThingNotAccessibleException(thingId, command.getDittoHeaders()));
        }

    }

    /**
     * This strategy handles the {@link RetrieveThing} command.
     */
    @NotThreadSafe
    private final class RetrieveThingStrategy extends AbstractThingCommandStrategy<RetrieveThing> {

        /**
         * Constructs a new {@code RetrieveThingStrategy} object.
         */
        public RetrieveThingStrategy() {
            super(RetrieveThing.class, log);
        }

        @Override
        public FI.TypedPredicate<RetrieveThing> getPredicate() {
            return command -> Objects.equals(thingId, command.getId()) && null != thing && !isThingDeleted();
        }

        @Override
        protected void doApply(final RetrieveThing command) {
            final Optional<Long> snapshotRevisionOptional = command.getSnapshotRevision();
            if (snapshotRevisionOptional.isPresent()) {
                loadSnapshot(command, snapshotRevisionOptional.get(), getSender());
            } else {
                final JsonObject thingJson = command.getSelectedFields()
                        .map(sf -> thing.toJson(command.getImplementedSchemaVersion(), sf))
                        .orElseGet(() -> thing.toJson(command.getImplementedSchemaVersion()));

                notifySender(RetrieveThingResponse.of(thingId, thingJson, command.getDittoHeaders()));
            }
        }

        private void loadSnapshot(final RetrieveThing command, final long snapshotRevision, final ActorRef sender) {
            thingSnapshotter.loadSnapshot(snapshotRevision).thenAccept(thingOptional -> {
                if (thingOptional.isPresent()) {
                    respondWithLoadSnapshotResult(command, sender, thingOptional.get());
                } else {
                    respondWithNotAccessibleException(command, sender);
                }
            });
        }

        private void respondWithLoadSnapshotResult(final RetrieveThing command, final ActorRef sender,
                final Thing snapshotThing) {

            final JsonObject thingJson = command.getSelectedFields()
                    .map(sf -> snapshotThing.toJson(command.getImplementedSchemaVersion(), sf))
                    .orElseGet(() -> snapshotThing.toJson(command.getImplementedSchemaVersion()));

            notifySender(sender, RetrieveThingResponse.of(thingId, thingJson, command.getDittoHeaders()));
        }

        private void respondWithNotAccessibleException(final RetrieveThing command, final ActorRef sender) {
            // reset command headers so that correlationId etc. are preserved
            notifySender(sender, new ThingNotAccessibleException(command.getThingId(), command.getDittoHeaders()));
        }

        @Override
        public FI.UnitApply<RetrieveThing> getUnhandledFunction() {
            return command -> notifySender(new ThingNotAccessibleException(thingId, command.getDittoHeaders()));
        }

    }

    /**
     * This strategy handles the {@link SudoRetrieveThing} command.
     */
    @NotThreadSafe
    private final class SudoRetrieveThingStrategy extends AbstractThingCommandStrategy<SudoRetrieveThing> {

        /**
         * Constructs a new {@code SudoRetrieveThingStrategy} object.
         */
        public SudoRetrieveThingStrategy() {
            super(SudoRetrieveThing.class, log);
        }

        @Override
        public FI.TypedPredicate<SudoRetrieveThing> getPredicate() {
            return command -> Objects.equals(thingId, command.getId()) && null != thing && !isThingDeleted();
        }

        @Override
        protected void doApply(final SudoRetrieveThing command) {
            final Optional<JsonFieldSelector> selectedFields = command.getSelectedFields();
            final JsonSchemaVersion versionToUse = determineSchemaVersion(command);
            final JsonObject thingJson = selectedFields
                    .map(sf -> thing.toJson(versionToUse, sf, FieldType.regularOrSpecial()))
                    .orElseGet(() -> thing.toJson(versionToUse, FieldType.regularOrSpecial()));

            notifySender(SudoRetrieveThingResponse.of(thingJson, command.getDittoHeaders()));
        }

        private JsonSchemaVersion determineSchemaVersion(final SudoRetrieveThing command) {
            return command.useOriginalSchemaVersion()
                    ? getOriginalSchemaVersion()
                    : command.getImplementedSchemaVersion();
        }

        private JsonSchemaVersion getOriginalSchemaVersion() {
            return null != thing ? thing.getImplementedSchemaVersion() : JsonSchemaVersion.LATEST;
        }

        @Override
        public FI.UnitApply<SudoRetrieveThing> getUnhandledFunction() {
            return command -> notifySender(new ThingNotAccessibleException(thingId, command.getDittoHeaders()));
        }

    }

    /**
     * This strategy handles the {@link DeleteThing} command.
     */
    @NotThreadSafe
    private final class DeleteThingStrategy extends AbstractThingCommandStrategy<DeleteThing> {

        /**
         * Constructs a new {@code DeleteThingStrategy} object.
         */
        public DeleteThingStrategy() {
            super(DeleteThing.class, log);
        }

        @Override
        protected void doApply(final DeleteThing command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final ThingDeleted thingDeleted = ThingDeleted.of(thingId, nextRevision(), eventTimestamp(), dittoHeaders);

            persistAndApplyEvent(thingDeleted, event -> {
                notifySender(DeleteThingResponse.of(thingId, dittoHeaders));
                log.info("Deleted Thing with ID <{}>.", thingId);
                becomeThingDeletedHandler();
            });
        }

    }

    /**
     * This strategy handles the {@link RetrievePolicyId} command.
     */
    @NotThreadSafe
    private final class RetrievePolicyIdStrategy extends AbstractThingCommandStrategy<RetrievePolicyId> {

        /**
         * Constructs a new {@code RetrievePolicyIdStrategy} object.
         */
        public RetrievePolicyIdStrategy() {
            super(RetrievePolicyId.class, log);
        }

        @Override
        protected void doApply(final RetrievePolicyId command) {
            final Optional<String> optPolicyId = thing.getPolicyId();
            if (optPolicyId.isPresent()) {
                final String policyId = optPolicyId.get();
                notifySender(RetrievePolicyIdResponse.of(thingId, policyId, command.getDittoHeaders()));
            } else {
                notifySender(PolicyIdNotAccessibleException.newBuilder(thingId)
                        .dittoHeaders(command.getDittoHeaders())
                        .build());
            }
        }

    }

    /**
     * This strategy handles the {@link ModifyPolicyId} command.
     */
    @NotThreadSafe
    private final class ModifyPolicyIdStrategy extends AbstractThingCommandStrategy<ModifyPolicyId> {

        /**
         * Constructs a new {@code ModifyPolicyIdStrategy} object.
         */
        public ModifyPolicyIdStrategy() {
            super(ModifyPolicyId.class, log);
        }

        @Override
        protected void doApply(final ModifyPolicyId command) {
            final ThingModifiedEvent eventToPersist;
            final ThingModifyCommandResponse response;

            if (thing.getPolicyId().isPresent()) {
                eventToPersist = PolicyIdModified.of(thingId, command.getPolicyId(), nextRevision(), eventTimestamp(),
                        command.getDittoHeaders());
                response = ModifyPolicyIdResponse.modified(thingId, command.getDittoHeaders());
            } else {
                eventToPersist = PolicyIdCreated.of(thingId, command.getPolicyId(), nextRevision(), eventTimestamp(),
                        command.getDittoHeaders());
                response = ModifyPolicyIdResponse.created(thingId, command.getPolicyId(), command.getDittoHeaders());
            }

            persistAndApplyEvent(eventToPersist, event -> notifySender(response));
        }

    }

    /**
     * This strategy handles the {@link ModifyAcl} command.
     */
    @NotThreadSafe
    private final class ModifyAclStrategy extends AbstractThingCommandStrategy<ModifyAcl> {

        /**
         * Constructs a new {@code ModifyAclStrategy} object.
         */
        public ModifyAclStrategy() {
            super(ModifyAcl.class, log);
        }

        @Override
        protected void doApply(final ModifyAcl command) {
            final AccessControlList newAccessControlList = command.getAccessControlList();
            final Validator aclValidator = AclValidator.newInstance(newAccessControlList,
                    Thing.MIN_REQUIRED_PERMISSIONS);
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            if (aclValidator.isValid()) {
                final AclModified aclModified = AclModified.of(thingId, newAccessControlList, nextRevision(),
                        eventTimestamp(), dittoHeaders);

                persistAndApplyEvent(aclModified, event -> notifySender(
                        ModifyAclResponse.modified(thingId, newAccessControlList, command.getDittoHeaders())));
            } else {
                aclInvalid(aclValidator.getReason(), dittoHeaders.getAuthorizationContext(), dittoHeaders);
            }
        }

    }

    /**
     * This strategy handles the {@link ModifyAclEntry} command.
     */
    @NotThreadSafe
    private final class ModifyAclEntryStrategy extends AbstractThingCommandStrategy<ModifyAclEntry> {

        /**
         * Constructs a new {@code ModifyAclEntryStrategy} object.
         */
        public ModifyAclEntryStrategy() {
            super(ModifyAclEntry.class, log);
        }

        @Override
        protected void doApply(final ModifyAclEntry command) {
            final AccessControlList acl = thing.getAccessControlList().orElseGet(ThingsModelFactory::emptyAcl);
            final AclEntry modifiedAclEntry = command.getAclEntry();
            final Validator aclValidator = AclValidator.newInstance(acl.setEntry(modifiedAclEntry),
                    Thing.MIN_REQUIRED_PERMISSIONS);
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            if (aclValidator.isValid()) {
                final ThingModifiedEvent eventToPersist;
                final ModifyAclEntryResponse response;

                if (acl.contains(modifiedAclEntry.getAuthorizationSubject())) {
                    eventToPersist = AclEntryModified.of(command.getId(), modifiedAclEntry, nextRevision(),
                            eventTimestamp(), dittoHeaders);
                    response = ModifyAclEntryResponse.modified(thingId, modifiedAclEntry, dittoHeaders);
                } else {
                    eventToPersist = AclEntryCreated.of(command.getId(), modifiedAclEntry, nextRevision(),
                            eventTimestamp(), dittoHeaders);
                    response = ModifyAclEntryResponse.created(thingId, modifiedAclEntry, dittoHeaders);
                }

                persistAndApplyEvent(eventToPersist, event -> notifySender(response));
            } else {
                aclInvalid(aclValidator.getReason(), dittoHeaders.getAuthorizationContext(), dittoHeaders);
            }
        }

    }

    /**
     * This strategy handles the {@link DeleteAclEntry} command.
     */
    @NotThreadSafe
    private final class DeleteAclEntryStrategy extends AbstractThingCommandStrategy<DeleteAclEntry> {

        /**
         * Constructs a new {@code DeleteAclEntryStrategy} object.
         */
        public DeleteAclEntryStrategy() {
            super(DeleteAclEntry.class, log);
        }

        @Override
        protected void doApply(final DeleteAclEntry command) {
            final AccessControlList acl = thing.getAccessControlList().orElseGet(ThingsModelFactory::emptyAcl);
            final AuthorizationSubject authorizationSubject = command.getAuthorizationSubject();
            final DittoHeaders dittoHeaders = command.getDittoHeaders();

            if (acl.contains(authorizationSubject)) {
                final Validator aclValidator =
                        AclValidator.newInstance(acl.removeAllPermissionsOf(authorizationSubject),
                                Thing.MIN_REQUIRED_PERMISSIONS);
                if (aclValidator.isValid()) {
                    deleteAclEntry(authorizationSubject, dittoHeaders);
                } else {
                    aclInvalid(aclValidator.getReason(), dittoHeaders.getAuthorizationContext(), dittoHeaders);
                }
            } else {
                aclEntryNotFound(authorizationSubject, dittoHeaders);
            }
        }

        private void deleteAclEntry(final AuthorizationSubject authorizationSubject, final DittoHeaders dittoHeaders) {
            final AclEntryDeleted aclEntryDeleted =
                    AclEntryDeleted.of(thingId, authorizationSubject, nextRevision(), eventTimestamp(), dittoHeaders);

            persistAndApplyEvent(aclEntryDeleted,
                    event -> notifySender(DeleteAclEntryResponse.of(thingId, authorizationSubject, dittoHeaders)));
        }

    }

    /**
     * This strategy handles the {@link RetrieveAcl} command.
     */
    @NotThreadSafe
    private final class RetrieveAclStrategy extends AbstractThingCommandStrategy<RetrieveAcl> {

        /**
         * Constructs a new {@code RetrieveAclStrategy} object.
         */
        public RetrieveAclStrategy() {
            super(RetrieveAcl.class, log);
        }

        @Override
        protected void doApply(final RetrieveAcl command) {
            final AccessControlList acl = thing.getAccessControlList().orElseGet(ThingsModelFactory::emptyAcl);
            final JsonObject aclJson = acl.toJson(command.getImplementedSchemaVersion());
            notifySender(RetrieveAclResponse.of(thingId, aclJson, command.getDittoHeaders()));
        }

    }

    /**
     * This strategy handles the {@link RetrieveAclEntry} command.
     */
    @NotThreadSafe
    private final class RetrieveAclEntryStrategy extends AbstractThingCommandStrategy<RetrieveAclEntry> {

        /**
         * Constructs a new {@code RetrieveAclEntryStrategy} object.
         */
        public RetrieveAclEntryStrategy() {
            super(RetrieveAclEntry.class, log);
        }

        @Override
        protected void doApply(final RetrieveAclEntry command) {
            final AccessControlList acl = thing.getAccessControlList().orElseGet(ThingsModelFactory::emptyAcl);
            final AuthorizationSubject authorizationSubject = command.getAuthorizationSubject();
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            if (acl.contains(authorizationSubject)) {
                final AclEntry aclEntry = acl.getEntryFor(authorizationSubject)
                        .orElseGet(
                                () -> AclEntry.newInstance(authorizationSubject, ThingsModelFactory.noPermissions()));
                notifySender(RetrieveAclEntryResponse.of(thingId, aclEntry, dittoHeaders));
            } else {
                aclEntryNotFound(authorizationSubject, dittoHeaders);
            }
        }

    }

    /**
     * This strategy handles the {@link ModifyAttributes} command.
     */
    @NotThreadSafe
    private final class ModifyAttributesStrategy extends AbstractThingCommandStrategy<ModifyAttributes> {

        /**
         * Constructs a new {@code ModifyAttributesStrategy} object.
         */
        public ModifyAttributesStrategy() {
            super(ModifyAttributes.class, log);
        }

        @Override
        protected void doApply(final ModifyAttributes command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final ThingModifiedEvent eventToPersist;
            final ThingModifyCommandResponse response;

            if (thing.getAttributes().isPresent()) {
                eventToPersist = AttributesModified.of(thingId, command.getAttributes(), nextRevision(),
                        eventTimestamp(), dittoHeaders);
                response = ModifyAttributesResponse.modified(thingId, dittoHeaders);
            } else {
                eventToPersist = AttributesCreated.of(thingId, command.getAttributes(), nextRevision(),
                        eventTimestamp(), dittoHeaders);
                response = ModifyAttributesResponse.created(thingId, command.getAttributes(), dittoHeaders);
            }

            persistAndApplyEvent(eventToPersist, event -> notifySender(response));
        }

    }

    /**
     * This strategy handles the {@link ModifyAttribute} command.
     */
    @NotThreadSafe
    private final class ModifyAttributeStrategy extends AbstractThingCommandStrategy<ModifyAttribute> {

        /**
         * Constructs a new {@code ModifyAttributeStrategy} object.
         */
        public ModifyAttributeStrategy() {
            super(ModifyAttribute.class, log);
        }

        @Override
        protected void doApply(final ModifyAttribute command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final Optional<Attributes> optionalAttributes = thing.getAttributes();

            final ThingModifiedEvent eventToPersist;
            final ThingModifyCommandResponse response;

            final JsonPointer attributeJsonPointer = command.getAttributePointer();
            final JsonValue attributeValue = command.getAttributeValue();
            if (optionalAttributes.isPresent() && optionalAttributes.get().contains(attributeJsonPointer)) {
                eventToPersist = AttributeModified.of(thingId, attributeJsonPointer, attributeValue, nextRevision(),
                        eventTimestamp(), dittoHeaders);
                response = ModifyAttributeResponse.modified(thingId, attributeJsonPointer, dittoHeaders);
            } else {
                eventToPersist = AttributeCreated.of(thingId, attributeJsonPointer, attributeValue, nextRevision(),
                        eventTimestamp(), dittoHeaders);
                response = ModifyAttributeResponse.created(thingId, attributeJsonPointer, attributeValue, dittoHeaders);
            }

            persistAndApplyEvent(eventToPersist, event -> notifySender(response));
        }

    }

    /**
     * This strategy handles the {@link DeleteAttributes} command.
     */
    @NotThreadSafe
    private final class DeleteAttributesStrategy extends AbstractThingCommandStrategy<DeleteAttributes> {

        /**
         * Constructs a new {@code DeleteAttributesStrategy} object.
         */
        public DeleteAttributesStrategy() {
            super(DeleteAttributes.class, log);
        }

        @Override
        protected void doApply(final DeleteAttributes command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();

            if (thing.getAttributes().isPresent()) {
                final AttributesDeleted attributesDeleted = AttributesDeleted.of(command.getThingId(), nextRevision(),
                        eventTimestamp(), dittoHeaders);
                persistAndApplyEvent(attributesDeleted,
                        event -> notifySender(DeleteAttributesResponse.of(thingId, dittoHeaders)));
            } else {
                attributesNotFound(dittoHeaders);
            }
        }

    }

    /**
     * This strategy handles the {@link DeleteAttribute} command.
     */
    @NotThreadSafe
    private final class DeleteAttributeStrategy extends AbstractThingCommandStrategy<DeleteAttribute> {

        /**
         * Constructs a new {@code DeleteAttributeStrategy} object.
         */
        public DeleteAttributeStrategy() {
            super(DeleteAttribute.class, log);
        }

        @Override
        protected void doApply(final DeleteAttribute command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final JsonPointer attributeJsonPointer = command.getAttributePointer();
            final Optional<Attributes> attributesOptional = thing.getAttributes();
            if (attributesOptional.isPresent()) {
                final Attributes attributes = attributesOptional.get();
                if (attributes.contains(attributeJsonPointer)) {
                    final AttributeDeleted attributeDeleted = AttributeDeleted.of(command.getThingId(),
                            attributeJsonPointer, nextRevision(), eventTimestamp(), dittoHeaders);

                    persistAndApplyEvent(attributeDeleted, event -> notifySender(
                            DeleteAttributeResponse.of(thingId, attributeJsonPointer, dittoHeaders)));
                } else {
                    attributeNotFound(attributeJsonPointer, command.getDittoHeaders());
                }
            } else {
                attributeNotFound(attributeJsonPointer, command.getDittoHeaders());
            }
        }

    }

    /**
     * This strategy handles the {@link RetrieveAttributes} command.
     */
    @NotThreadSafe
    private final class RetrieveAttributesStrategy extends AbstractThingCommandStrategy<RetrieveAttributes> {

        /**
         * Constructs a new {@code RetrieveAttributesStrategy} object.
         */
        public RetrieveAttributesStrategy() {
            super(RetrieveAttributes.class, log);
        }

        @Override
        protected void doApply(final RetrieveAttributes command) {
            final Optional<Attributes> optionalAttributes = thing.getAttributes();
            final DittoHeaders dittoHeaders = command.getDittoHeaders();

            if (optionalAttributes.isPresent()) {
                final Attributes attributes = optionalAttributes.get();
                final Optional<JsonFieldSelector> selectedFields = command.getSelectedFields();
                final JsonObject attributesJson = selectedFields
                        .map(sf -> attributes.toJson(command.getImplementedSchemaVersion(), sf))
                        .orElseGet(() -> attributes.toJson(command.getImplementedSchemaVersion()));
                notifySender(RetrieveAttributesResponse.of(thingId, attributesJson, dittoHeaders));
            } else {
                attributesNotFound(dittoHeaders);
            }
        }

    }

    /**
     * This strategy handles the {@link RetrieveAttribute} command.
     */
    @NotThreadSafe
    private final class RetrieveAttributeStrategy extends AbstractThingCommandStrategy<RetrieveAttribute> {

        /**
         * Constructs a new {@code RetrieveAttributeStrategy} object.
         */
        public RetrieveAttributeStrategy() {
            super(RetrieveAttribute.class, log);
        }

        @Override
        protected void doApply(final RetrieveAttribute command) {
            final Optional<Attributes> optionalAttributes = thing.getAttributes();
            final DittoHeaders dittoHeaders = command.getDittoHeaders();

            if (optionalAttributes.isPresent()) {
                final Attributes attributes = optionalAttributes.get();
                final JsonPointer jsonPointer = command.getAttributePointer();
                final Optional<JsonValue> jsonValue = attributes.getValue(jsonPointer);
                if (jsonValue.isPresent()) {
                    notifySender(RetrieveAttributeResponse.of(thingId, jsonPointer, jsonValue.get(), dittoHeaders));
                } else {
                    attributeNotFound(jsonPointer, dittoHeaders);
                }
            } else {
                attributesNotFound(dittoHeaders);
            }
        }

    }

    /**
     * This strategy handles the {@link ModifyFeatures} command.
     */
    @NotThreadSafe
    private final class ModifyFeaturesStrategy extends AbstractThingCommandStrategy<ModifyFeatures> {

        /**
         * Constructs a new {@code ModifyFeaturesStrategy} object.
         */
        public ModifyFeaturesStrategy() {
            super(ModifyFeatures.class, log);
        }

        @Override
        protected void doApply(final ModifyFeatures command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final ThingModifiedEvent eventToPersist;
            final ThingModifyCommandResponse response;

            if (thing.getFeatures().isPresent()) {
                eventToPersist = FeaturesModified.of(command.getId(), command.getFeatures(), nextRevision(),
                        eventTimestamp(), dittoHeaders);
                response = ModifyFeaturesResponse.modified(thingId, dittoHeaders);
            } else {
                eventToPersist = FeaturesCreated.of(command.getId(), command.getFeatures(), nextRevision(),
                        eventTimestamp(), dittoHeaders);
                response = ModifyFeaturesResponse.created(thingId, command.getFeatures(), dittoHeaders);
            }

            persistAndApplyEvent(eventToPersist, event -> notifySender(response));
        }

    }

    /**
     * This strategy handles the {@link ModifyFeature} command.
     */
    @NotThreadSafe
    private final class ModifyFeatureStrategy extends AbstractThingCommandStrategy<ModifyFeature> {

        /**
         * Constructs a new {@code ModifyFeatureStrategy} object.
         */
        public ModifyFeatureStrategy() {
            super(ModifyFeature.class, log);
        }

        @Override
        protected void doApply(final ModifyFeature command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final Optional<Features> features = thing.getFeatures();
            final ThingModifiedEvent eventToPersist;
            final ThingModifyCommandResponse response;

            if (features.isPresent() && features.get().getFeature(command.getFeatureId()).isPresent()) {
                eventToPersist = FeatureModified.of(command.getId(), command.getFeature(), nextRevision(),
                        eventTimestamp(), dittoHeaders);
                response = ModifyFeatureResponse.modified(thingId, command.getFeatureId(), dittoHeaders);
            } else {
                eventToPersist = FeatureCreated.of(command.getId(), command.getFeature(), nextRevision(),
                        eventTimestamp(), dittoHeaders);
                response = ModifyFeatureResponse.created(thingId, command.getFeature(), dittoHeaders);
            }

            persistAndApplyEvent(eventToPersist, event -> notifySender(response));
        }

    }

    /**
     * This strategy handles the {@link DeleteFeatures} command.
     */
    @NotThreadSafe
    private final class DeleteFeaturesStrategy extends AbstractThingCommandStrategy<DeleteFeatures> {

        /**
         * Constructs a new {@code DeleteFeaturesStrategy} object.
         */
        public DeleteFeaturesStrategy() {
            super(DeleteFeatures.class, log);
        }

        @Override
        protected void doApply(final DeleteFeatures command) {
            if (thing.getFeatures().isPresent()) {
                final DittoHeaders dittoHeaders = command.getDittoHeaders();
                final FeaturesDeleted featuresDeleted =
                        FeaturesDeleted.of(thingId, nextRevision(), eventTimestamp(), dittoHeaders);

                persistAndApplyEvent(featuresDeleted,
                        event -> notifySender(DeleteFeaturesResponse.of(thingId, dittoHeaders)));
            } else {
                featuresNotFound(command.getDittoHeaders());
            }
        }

    }

    /**
     * This strategy handles the {@link DeleteFeature} command.
     */
    @NotThreadSafe
    private final class DeleteFeatureStrategy extends AbstractThingCommandStrategy<DeleteFeature> {

        /**
         * Constructs a new {@code DeleteFeatureStrategy} object.
         */
        public DeleteFeatureStrategy() {
            super(DeleteFeature.class, log);
        }

        @Override
        protected void doApply(final DeleteFeature command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final Optional<String> featureIdOptional = thing.getFeatures()
                    .flatMap(features -> features.getFeature(command.getFeatureId()))
                    .map(Feature::getId);

            if (featureIdOptional.isPresent()) {
                final FeatureDeleted featureDeleted = FeatureDeleted.of(thingId, featureIdOptional.get(),
                        nextRevision(), eventTimestamp(), dittoHeaders);
                persistAndApplyEvent(featureDeleted,
                        event -> notifySender(DeleteFeatureResponse.of(thingId, command.getFeatureId(), dittoHeaders)));
            } else {
                featureNotFound(command.getFeatureId(), dittoHeaders);
            }
        }

    }

    /**
     * This strategy handles the {@link RetrieveFeatures} command.
     */
    @NotThreadSafe
    private final class RetrieveFeaturesStrategy extends AbstractThingCommandStrategy<RetrieveFeatures> {

        /**
         * Constructs a new {@code RetrieveFeaturesStrategy} object.
         */
        public RetrieveFeaturesStrategy() {
            super(RetrieveFeatures.class, log);
        }

        @Override
        protected void doApply(final RetrieveFeatures command) {
            final Optional<Features> optionalFeatures = thing.getFeatures();
            if (optionalFeatures.isPresent()) {
                final Features features = optionalFeatures.get();
                final Optional<JsonFieldSelector> selectedFields = command.getSelectedFields();
                final JsonObject featuresJson = selectedFields
                        .map(sf -> features.toJson(command.getImplementedSchemaVersion(), sf))
                        .orElseGet(() -> features.toJson(command.getImplementedSchemaVersion()));
                notifySender(getSender(),
                        RetrieveFeaturesResponse.of(thingId, featuresJson, command.getDittoHeaders()));
            } else {
                featuresNotFound(command.getDittoHeaders());
            }
        }

    }

    /**
     * This strategy handles the {@link RetrieveFeature} command.
     */
    @NotThreadSafe
    private final class RetrieveFeatureStrategy extends AbstractThingCommandStrategy<RetrieveFeature> {

        /**
         * Constructs a new {@code RetrieveFeatureStrategy} object.
         */
        public RetrieveFeatureStrategy() {
            super(RetrieveFeature.class, log);
        }

        @Override
        protected void doApply(final RetrieveFeature command) {
            final Optional<Feature> feature = thing.getFeatures().flatMap(fs -> fs.getFeature(command.getFeatureId()));
            if (feature.isPresent()) {
                final Feature f = feature.get();
                final Optional<JsonFieldSelector> selectedFields = command.getSelectedFields();
                final JsonObject featureJson = selectedFields
                        .map(sf -> f.toJson(command.getImplementedSchemaVersion(), sf))
                        .orElseGet(() -> f.toJson(command.getImplementedSchemaVersion()));
                notifySender(getSender(), RetrieveFeatureResponse.of(thingId, command.getFeatureId(), featureJson,
                        command.getDittoHeaders()));
            } else {
                featureNotFound(command.getFeatureId(), command.getDittoHeaders());
            }
        }

    }

    /**
     * This strategy handles the {@link ModifyFeatureDefinition} command.
     */
    @NotThreadSafe
    private final class ModifyFeatureDefinitionStrategy extends AbstractThingCommandStrategy<ModifyFeatureDefinition> {

        /**
         * Constructs a new {@code ModifyFeatureDefinitionStrategy} object.
         */
        public ModifyFeatureDefinitionStrategy() {
            super(ModifyFeatureDefinition.class, log);
        }

        @Override
        protected void doApply(final ModifyFeatureDefinition command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final Optional<Features> features = thing.getFeatures();

            if (features.isPresent()) {
                final Optional<Feature> feature = features.get().getFeature(command.getFeatureId());

                if (feature.isPresent()) {
                    final ThingModifiedEvent eventToPersist;
                    final ThingModifyCommandResponse response;

                    if (feature.get().getDefinition().isPresent()) {
                        eventToPersist = FeatureDefinitionModified.of(command.getId(), command.getFeatureId(),
                                command.getDefinition(), nextRevision(), eventTimestamp(), dittoHeaders);
                        response =
                                ModifyFeatureDefinitionResponse.modified(thingId, command.getFeatureId(), dittoHeaders);
                    } else {
                        eventToPersist = FeatureDefinitionCreated.of(command.getId(), command.getFeatureId(),
                                command.getDefinition(), nextRevision(), eventTimestamp(), dittoHeaders);
                        response = ModifyFeatureDefinitionResponse.created(thingId, command.getFeatureId(),
                                command.getDefinition(), dittoHeaders);
                    }

                    persistAndApplyEvent(eventToPersist, event -> notifySender(response));
                } else {
                    featureNotFound(command.getFeatureId(), command.getDittoHeaders());
                }
            } else {
                featureNotFound(command.getFeatureId(), command.getDittoHeaders());
            }
        }

    }

    /**
     * This strategy handles the {@link DeleteFeatureDefinition} command.
     */
    @NotThreadSafe
    private final class DeleteFeatureDefinitionStrategy extends AbstractThingCommandStrategy<DeleteFeatureDefinition> {

        /**
         * Constructs a new {@code DeleteFeatureDefinitionStrategy} object.
         */
        public DeleteFeatureDefinitionStrategy() {
            super(DeleteFeatureDefinition.class, log);
        }

        @Override
        protected void doApply(final DeleteFeatureDefinition command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final Optional<Features> features = thing.getFeatures();

            if (features.isPresent()) {
                final Optional<Feature> feature = features.get().getFeature(command.getFeatureId());

                if (feature.isPresent()) {
                    if (feature.get().getDefinition().isPresent()) {
                        final FeatureDefinitionDeleted definitionDeleted =
                                FeatureDefinitionDeleted.of(command.getThingId(), command.getFeatureId(),
                                        nextRevision(), eventTimestamp(), dittoHeaders);
                        persistAndApplyEvent(definitionDeleted, event -> notifySender(
                                DeleteFeatureDefinitionResponse.of(thingId, command.getFeatureId(), dittoHeaders)));
                    } else {
                        featureDefinitionNotFound(command.getFeatureId(), dittoHeaders);
                    }
                } else {
                    featureNotFound(command.getFeatureId(), dittoHeaders);
                }
            } else {
                featureNotFound(command.getFeatureId(), dittoHeaders);
            }
        }

    }

    /**
     * This strategy handles the {@link RetrieveFeatureDefinition} command.
     */
    @NotThreadSafe
    private final class RetrieveFeatureDefinitionStrategy
            extends AbstractThingCommandStrategy<RetrieveFeatureDefinition> {

        /**
         * Constructs a new {@code RetrieveFeatureDefinitionStrategy} object.
         */
        public RetrieveFeatureDefinitionStrategy() {
            super(RetrieveFeatureDefinition.class, log);
        }

        @Override
        protected void doApply(final RetrieveFeatureDefinition command) {
            final Optional<Features> optionalFeatures = thing.getFeatures();

            if (optionalFeatures.isPresent()) {
                final Optional<FeatureDefinition> optionalDefinition = optionalFeatures.flatMap(features -> features
                        .getFeature(command.getFeatureId()))
                        .flatMap(Feature::getDefinition);
                if (optionalDefinition.isPresent()) {
                    final FeatureDefinition definition = optionalDefinition.get();
                    notifySender(RetrieveFeatureDefinitionResponse.of(thingId, command.getFeatureId(), definition,
                            command.getDittoHeaders()));
                } else {
                    featureDefinitionNotFound(command.getFeatureId(), command.getDittoHeaders());
                }
            } else {
                featureNotFound(command.getFeatureId(), command.getDittoHeaders());
            }
        }

    }

    /**
     * This strategy handles the {@link ModifyFeatureProperties} command.
     */
    @NotThreadSafe
    private final class ModifyFeaturePropertiesStrategy extends AbstractThingCommandStrategy<ModifyFeatureProperties> {

        /**
         * Constructs a new {@code ModifyFeaturePropertiesStrategy} object.
         */
        public ModifyFeaturePropertiesStrategy() {
            super(ModifyFeatureProperties.class, log);
        }

        @Override
        protected void doApply(final ModifyFeatureProperties command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final Optional<Features> features = thing.getFeatures();

            final String featureId = command.getFeatureId();
            if (features.isPresent()) {
                final Optional<Feature> feature = features.get().getFeature(featureId);

                if (feature.isPresent()) {
                    final ThingModifiedEvent eventToPersist;
                    final ThingModifyCommandResponse response;

                    final FeatureProperties featureProperties = command.getProperties();
                    if (feature.get().getProperties().isPresent()) {
                        eventToPersist = FeaturePropertiesModified.of(command.getId(), featureId, featureProperties,
                                nextRevision(), eventTimestamp(), dittoHeaders);
                        response = ModifyFeaturePropertiesResponse.modified(thingId, featureId, dittoHeaders);
                    } else {
                        eventToPersist = FeaturePropertiesCreated.of(command.getId(), featureId, featureProperties,
                                nextRevision(), eventTimestamp(), dittoHeaders);
                        response = ModifyFeaturePropertiesResponse.created(thingId, featureId, featureProperties,
                                dittoHeaders);
                    }

                    persistAndApplyEvent(eventToPersist, event -> notifySender(response));
                } else {
                    featureNotFound(featureId, command.getDittoHeaders());
                }
            } else {
                featureNotFound(featureId, command.getDittoHeaders());
            }
        }

    }

    /**
     * This strategy handles the {@link ModifyFeatureProperty} command.
     */
    @NotThreadSafe
    private final class ModifyFeaturePropertyStrategy extends AbstractThingCommandStrategy<ModifyFeatureProperty> {

        /**
         * Constructs a new {@code ModifyFeaturePropertyStrategy} object.
         */
        public ModifyFeaturePropertyStrategy() {
            super(ModifyFeatureProperty.class, log);
        }

        @Override
        protected void doApply(final ModifyFeatureProperty command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final Optional<Features> features = thing.getFeatures();

            final String featureId = command.getFeatureId();
            if (features.isPresent()) {
                final Optional<Feature> feature = features.get().getFeature(featureId);

                if (feature.isPresent()) {
                    final Optional<FeatureProperties> optionalProperties = feature.get().getProperties();
                    final ThingModifiedEvent eventToPersist;
                    final ThingModifyCommandResponse response;

                    final JsonPointer propertyJsonPointer = command.getPropertyPointer();
                    final JsonValue propertyValue = command.getPropertyValue();
                    if (optionalProperties.isPresent() && optionalProperties.get().contains(propertyJsonPointer)) {
                        eventToPersist = FeaturePropertyModified.of(command.getId(), featureId, propertyJsonPointer,
                                propertyValue, nextRevision(), eventTimestamp(), dittoHeaders);
                        response = ModifyFeaturePropertyResponse.modified(thingId, featureId, propertyJsonPointer,
                                dittoHeaders);
                    } else {
                        eventToPersist = FeaturePropertyCreated.of(command.getId(), featureId, propertyJsonPointer,
                                propertyValue, nextRevision(), eventTimestamp(), dittoHeaders);
                        response = ModifyFeaturePropertyResponse.created(thingId, featureId, propertyJsonPointer,
                                propertyValue, dittoHeaders);
                    }

                    persistAndApplyEvent(eventToPersist, event -> notifySender(response));
                } else {
                    featureNotFound(featureId, command.getDittoHeaders());
                }
            } else {
                featureNotFound(featureId, command.getDittoHeaders());
            }
        }

    }

    /**
     * This strategy handles the {@link DeleteFeatureProperties} command.
     */
    @NotThreadSafe
    private final class DeleteFeaturePropertiesStrategy extends AbstractThingCommandStrategy<DeleteFeatureProperties> {

        /**
         * Constructs a new {@code DeleteFeaturePropertiesStrategy} object.
         */
        public DeleteFeaturePropertiesStrategy() {
            super(DeleteFeatureProperties.class, log);
        }

        @Override
        protected void doApply(final DeleteFeatureProperties command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final Optional<Features> features = thing.getFeatures();

            final String featureId = command.getFeatureId();
            if (features.isPresent()) {
                final Optional<Feature> feature = features.get().getFeature(featureId);

                if (feature.isPresent()) {
                    if (feature.get().getProperties().isPresent()) {
                        final FeaturePropertiesDeleted propertiesDeleted =
                                FeaturePropertiesDeleted.of(command.getThingId(), featureId, nextRevision(),
                                        eventTimestamp(), dittoHeaders);
                        persistAndApplyEvent(propertiesDeleted, event -> notifySender(
                                DeleteFeaturePropertiesResponse.of(thingId, featureId, dittoHeaders)));
                    } else {
                        featurePropertiesNotFound(featureId, dittoHeaders);
                    }
                } else {
                    featureNotFound(featureId, dittoHeaders);
                }
            } else {
                featureNotFound(featureId, dittoHeaders);
            }
        }

    }

    /**
     * This strategy handles the {@link DeleteFeatureProperty} command.
     */
    @NotThreadSafe
    private final class DeleteFeaturePropertyStrategy extends AbstractThingCommandStrategy<DeleteFeatureProperty> {

        /**
         * Constructs a new {@code DeleteFeaturePropertyStrategy} object.
         */
        public DeleteFeaturePropertyStrategy() {
            super(DeleteFeatureProperty.class, log);
        }

        @Override
        protected void doApply(final DeleteFeatureProperty command) {
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            final Optional<Features> featuresOptional = thing.getFeatures();

            final String featureId = command.getFeatureId();
            if (featuresOptional.isPresent()) {
                final Optional<Feature> featureOptional =
                        featuresOptional.flatMap(features -> features.getFeature(featureId));

                if (featureOptional.isPresent()) {
                    final JsonPointer propertyJsonPointer = command.getPropertyPointer();
                    final Feature feature = featureOptional.get();
                    final boolean containsProperty = feature.getProperties()
                            .filter(featureProperties -> featureProperties.contains(propertyJsonPointer))
                            .isPresent();

                    if (containsProperty) {
                        final FeaturePropertyDeleted propertyDeleted = FeaturePropertyDeleted.of(command.getThingId(),
                                featureId, propertyJsonPointer, nextRevision(), eventTimestamp(), dittoHeaders);

                        persistAndApplyEvent(propertyDeleted, event -> notifySender(
                                DeleteFeaturePropertyResponse.of(thingId, featureId, propertyJsonPointer,
                                        dittoHeaders)));
                    } else {
                        featurePropertyNotFound(featureId, propertyJsonPointer, dittoHeaders);
                    }
                } else {
                    featureNotFound(featureId, dittoHeaders);
                }
            } else {
                featureNotFound(featureId, dittoHeaders);
            }
        }

    }

    /**
     * This strategy handles the {@link RetrieveFeatureProperties} command.
     */
    @NotThreadSafe
    private final class RetrieveFeaturePropertiesStrategy
            extends AbstractThingCommandStrategy<RetrieveFeatureProperties> {

        /**
         * Constructs a new {@code RetrieveFeaturePropertiesStrategy} object.
         */
        public RetrieveFeaturePropertiesStrategy() {
            super(RetrieveFeatureProperties.class, log);
        }

        @Override
        protected void doApply(final RetrieveFeatureProperties command) {
            final Optional<Features> optionalFeatures = thing.getFeatures();

            final String featureId = command.getFeatureId();
            final DittoHeaders dittoHeaders = command.getDittoHeaders();
            if (optionalFeatures.isPresent()) {
                final Optional<FeatureProperties> optionalProperties = optionalFeatures.flatMap(features -> features
                        .getFeature(featureId))
                        .flatMap(Feature::getProperties);
                if (optionalProperties.isPresent()) {
                    final FeatureProperties properties = optionalProperties.get();
                    final Optional<JsonFieldSelector> selectedFields = command.getSelectedFields();
                    final JsonObject propertiesJson = selectedFields
                            .map(sf -> properties.toJson(command.getImplementedSchemaVersion(), sf))
                            .orElseGet(() -> properties.toJson(command.getImplementedSchemaVersion()));
                    notifySender(
                            RetrieveFeaturePropertiesResponse.of(thingId, featureId, propertiesJson, dittoHeaders));
                } else {
                    featurePropertiesNotFound(featureId, dittoHeaders);
                }
            } else {
                featureNotFound(featureId, dittoHeaders);
            }
        }

    }

    /**
     * This strategy handles the {@link RetrieveFeatureProperty} command.
     */
    @NotThreadSafe
    private final class RetrieveFeaturePropertyStrategy extends AbstractThingCommandStrategy<RetrieveFeatureProperty> {

        /**
         * Constructs a new {@code RetrieveFeaturePropertyStrategy} object.
         */
        public RetrieveFeaturePropertyStrategy() {
            super(RetrieveFeatureProperty.class, log);
        }

        @Override
        protected void doApply(final RetrieveFeatureProperty command) {
            final Optional<Feature> featureOptional = thing.getFeatures()
                    .flatMap(features -> features.getFeature(command.getFeatureId()));
            if (featureOptional.isPresent()) {
                final DittoHeaders dittoHeaders = command.getDittoHeaders();
                final Optional<FeatureProperties> optionalProperties = featureOptional.flatMap(Feature::getProperties);
                if (optionalProperties.isPresent()) {
                    final FeatureProperties properties = optionalProperties.get();
                    final JsonPointer jsonPointer = command.getPropertyPointer();
                    final Optional<JsonValue> propertyJson = properties.getValue(jsonPointer);
                    if (propertyJson.isPresent()) {
                        notifySender(RetrieveFeaturePropertyResponse.of(thingId, command.getFeatureId(), jsonPointer,
                                propertyJson.get(), dittoHeaders));
                    } else {
                        featurePropertyNotFound(command.getFeatureId(), jsonPointer, dittoHeaders);
                    }
                } else {
                    featurePropertiesNotFound(command.getFeatureId(), command.getDittoHeaders());
                }
            } else {
                featureNotFound(command.getFeatureId(), command.getDittoHeaders());
            }
        }

    }

    /**
     * This strategy handles all commands which were not explicitly handled beforehand. Those commands are logged as
     * unknown messages and are marked as unhandled.
     */
    @NotThreadSafe
    private final class MatchAnyAfterInitializeStrategy extends AbstractReceiveStrategy<Object> {

        /**
         * Constructs a new {@code MatchAnyAfterInitializeStrategy} object.
         */
        public MatchAnyAfterInitializeStrategy() {
            super(Object.class, log);
        }

        @Override
        protected void doApply(final Object message) {
            log.warning("Unknown message: {}", message);
            unhandled(message);
        }

    }

    /**
     * This strategy handles all messages which were received before the Thing was initialized. Those messages are
     * logged as unexpected messages and cause the actor to be stopped.
     */
    @NotThreadSafe
    private final class MatchAnyDuringInitializeStrategy extends AbstractReceiveStrategy<Object> {

        /**
         * Constructs a new {@code MatchAnyDuringInitializeStrategy} object.
         */
        public MatchAnyDuringInitializeStrategy() {
            super(Object.class, log);
        }

        @Override
        protected void doApply(final Object message) {
            log.debug("Unexpected message after initialization of actor received: {} - "
                            + "Terminating this actor and sending <{}> to requester ...", message,
                    ThingNotAccessibleException.class.getName());
            final ThingNotAccessibleException.Builder builder = ThingNotAccessibleException.newBuilder(thingId);
            if (message instanceof WithDittoHeaders) {
                builder.dittoHeaders(((WithDittoHeaders) message).getDittoHeaders());
            }
            notifySender(builder.build());
            scheduleCheckForThingActivity(activityCheckInterval.getSeconds());
        }

    }

    /**
     * This strategy handles any messages for a previous deleted Thing.
     */
    @NotThreadSafe
    private final class ThingNotFoundStrategy extends AbstractReceiveStrategy<Object> {

        /**
         * Constructs a new {@code ThingNotFoundStrategy} object.
         */
        public ThingNotFoundStrategy() {
            super(Object.class, log);
        }

        @Override
        protected void doApply(final Object message) {
            final ThingNotAccessibleException.Builder builder = ThingNotAccessibleException.newBuilder(thingId);
            if (message instanceof WithDittoHeaders) {
                builder.dittoHeaders(((WithDittoHeaders) message).getDittoHeaders());
            }
            notifySender(builder.build());
        }

    }

    /**
     * This strategy handles the {@link CheckForActivity} message which checks for activity of the Actor and
     * terminates itself if there was no activity since the last check.
     */
    @NotThreadSafe
    private final class CheckForActivityStrategy extends AbstractReceiveStrategy<CheckForActivity> {

        /**
         * Constructs a new {@code CheckForActivityStrategy} object.
         */
        public CheckForActivityStrategy() {
            super(CheckForActivity.class, log);
        }

        @Override
        protected void doApply(final CheckForActivity message) {
            if (isThingDeleted() && !thingSnapshotter.lastSnapshotCompletedAndUpToDate()) {
                // take a snapshot after a period of inactivity if:
                // - thing is deleted,
                // - the latest snapshot is out of date or is still ongoing.
                thingSnapshotter.takeSnapshotInternal();
                scheduleCheckForThingActivity(activityCheckDeletedInterval.getSeconds());
            } else if (accessCounter > message.getCurrentAccessCounter()) {
                // if the Thing was accessed in any way since the last check
                scheduleCheckForThingActivity(activityCheckInterval.getSeconds());
            } else {
                // safe to shutdown after a period of inactivity if:
                // - thing is active (and taking regular snapshots of itself), or
                // - thing is deleted and the latest snapshot is up to date
                if (isThingActive()) {
                    shutdown("Thing <{}> was not accessed in a while. Shutting Actor down ...", thingId);
                } else {
                    shutdown("Thing <{}> was deleted recently. Shutting Actor down ...", thingId);
                }
            }
        }

        private void shutdown(final String shutdownLogTemplate, final String thingId) {
            log.debug(shutdownLogTemplate, thingId);
            // stop the supervisor (otherwise it'd restart this actor) which causes this actor to stop, too.
            getContext().getParent().tell(PoisonPill.getInstance(), getSelf());
        }

    }

    /**
     * This extension of {@link AbstractReceiveStrategy} is for handling {@link ThingCommand}.
     *
     * @param <T> type of the class this strategy matches against.
     */
    @NotThreadSafe
    abstract class AbstractThingCommandStrategy<T extends Command> extends AbstractReceiveStrategy<T> {

        /**
         * Constructs a new {@code AbstractThingCommandStrategy} object.
         *
         * @param theMatchingClass the class of the message this strategy reacts to.
         * @param theLogger the logger to use for logging.
         * @throws NullPointerException if {@code theMatchingClass} is {@code null}.
         */
        AbstractThingCommandStrategy(final Class<T> theMatchingClass, final DiagnosticLoggingAdapter theLogger) {
            super(theMatchingClass, theLogger);
        }

        @Override
        public FI.TypedPredicate<T> getPredicate() {
            return command -> null != thing && thing.getId()
                    .filter(command.getId()::equals)
                    .isPresent();
        }

        @Override
        public FI.UnitApply<T> getUnhandledFunction() {
            return command -> {
                throw new IllegalArgumentException(MessageFormat.format(UNHANDLED_MESSAGE_TEMPLATE, command.getId()));
            };
        }

    }

    /**
     * This consumer logs the correlation ID, the thing ID as well as the type of any incoming message.
     */
    private final class LogIncomingMessagesConsumer implements Consumer<Object> {

        @Override
        public void accept(final Object message) {
            if (message instanceof WithDittoHeaders) {
                LogUtil.enhanceLogWithCorrelationId(log, (WithDittoHeaders) message);
            }

            final String messageType = getMessageType(message);
            if (isWithThingId(message)) {
                final String messageThingId = getMessageThingId((WithThingId) message);
                if (isEqualToActorThingId(messageThingId)) {
                    logInfoAboutIncomingMessage(messageType);
                } else {
                    log.warning("<{} got <{}> with different thing ID <{}>!", thingId, messageType, messageThingId);
                }
            } else {
                logInfoAboutIncomingMessage(messageType);
            }
        }

        private String getMessageType(final Object message) {
            if (isCommand(message)) {
                return ((WithType) message).getType();
            } else {
                return message.getClass().getSimpleName();
            }
        }

        private boolean isCommand(final Object message) {
            return message instanceof Command<?>;
        }

        private boolean isWithThingId(final Object message) {
            return message instanceof WithThingId;
        }

        private boolean isEqualToActorThingId(final String messageThingId) {
            return Objects.equals(thingId, messageThingId);
        }

        private String getMessageThingId(final WithThingId withThingId) {
            return withThingId.getThingId();
        }

        private void logInfoAboutIncomingMessage(final String messageType) {
            log.debug("<{}> got <{}>.", thingId, messageType);
        }

    }

}
