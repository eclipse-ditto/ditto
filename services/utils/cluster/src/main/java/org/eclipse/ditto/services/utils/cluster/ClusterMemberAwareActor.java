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
package org.eclipse.ditto.services.utils.cluster;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.ditto.services.utils.akka.LogUtil;

import akka.actor.AbstractActor;
import akka.actor.Address;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.Member;
import akka.event.DiagnosticLoggingAdapter;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import scala.collection.JavaConversions;
import scala.concurrent.duration.FiniteDuration;

/**
 * Actor aware of cluster members coming + leaving + becoming unreachable.
 */
public final class ClusterMemberAwareActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "clusterMemberAwareActor";

    private final DiagnosticLoggingAdapter log = LogUtil.obtain(this);

    private final Cluster cluster = Cluster.get(getContext().system());
    private final String serviceName;
    private final boolean majorityCheckEnabled;
    private final Duration majorityCheckDelay;
    private final Map<String, Address> knownAddresses = new HashMap<>();
    private Cancellable majorityCheck = null;

    private ClusterMemberAwareActor(final String serviceName, final boolean majorityCheckEnabled,
            final Duration majorityCheckDelay) {
        this.serviceName = serviceName;
        this.majorityCheckEnabled = majorityCheckEnabled;
        this.majorityCheckDelay = checkNotNull(majorityCheckDelay, "majority check delay");
    }

    /**
     * Creates Akka configuration object Props for this ClusterMemberAwareActor.
     *
     * @param serviceName the name of the service
     * @param majorityCheckEnabled true to enable the majority check, false otherwise.
     * @param majorityCheckDelay the delay after which the majority will be checked.
     * @return the Akka configuration Props object.
     */
    public static Props props(final String serviceName, final boolean majorityCheckEnabled,
            final Duration majorityCheckDelay) {
        return Props.create(ClusterMemberAwareActor.class, new Creator<ClusterMemberAwareActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ClusterMemberAwareActor create() throws Exception {
                return new ClusterMemberAwareActor(serviceName, majorityCheckEnabled, majorityCheckDelay);
            }
        });
    }

    @Override
    public void preStart() {
        //subscribe to cluster changes
        cluster.subscribe(getSelf(), ClusterEvent.initialStateAsEvents(), ClusterEvent.MemberEvent.class,
                ClusterEvent.UnreachableMember.class);
    }

    @Override
    public void postStop() {
        //re-subscribe when restart
        cluster.unsubscribe(getSelf());
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(ClusterEvent.CurrentClusterState.class, this::handleCurrentClusterState)
                .match(ClusterEvent.MemberJoined.class, this::handleMemberJoined)
                .match(ClusterEvent.MemberWeaklyUp.class, this::handleMemberWeaklyUp)
                .match(ClusterEvent.MemberUp.class, this::handleMemberUp)
                .match(ClusterEvent.UnreachableMember.class, this::handleUnreachableMember)
                .match(ClusterEvent.MemberRemoved.class, this::handleMemberRemoved)
                .match(ClusterEvent.MemberLeft.class, this::handleMemberLeft)
                .match(ClusterEvent.MemberExited.class, this::handleMemberExited)
                .match(ClusterEvent.MemberEvent.class, this::handleMemberEvent)
                .match(CheckForMajority.class, checkForMajority -> handleCheckForMajority())
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void handleCurrentClusterState(final ClusterEvent.CurrentClusterState clusterState) {
        log.debug("Current cluster state: members = {}, unreachable = {}, seenBy = {}, leader = {}",
                clusterState.members(), clusterState.unreachable(), clusterState.seenBy(),
                clusterState.leader());
    }

    private void handleMemberJoined(final ClusterEvent.MemberJoined memberJoined) {
        log.info("Member JOINED: {}", memberJoined.member());
    }

    private void handleMemberWeaklyUp(final ClusterEvent.MemberWeaklyUp memberWeaklyUp) {
        final Member weaklyUpMember = memberWeaklyUp.member();
        log.debug("Member is WEAKLY UP: {}", weaklyUpMember);

        final Address address = weaklyUpMember.address();
        if (address.host().isDefined()) {
            try {
                final InetAddress inetAddress = InetAddress.getByName(address.host().get());
                log.debug("Found DNS entry '{}' for WEAKLY UP member: '{}'", inetAddress, weaklyUpMember);

                if (knownAddresses.containsKey(inetAddress.getHostName())) {
                    final Address knownAddress = knownAddresses.get(inetAddress.getHostName());
                    log.debug("New WEAKLY UP member '{}' is already known with address '{}'",
                            weaklyUpMember,
                            knownAddress);

                    final Set<Member> unreachableMembers = JavaConversions.setAsJavaSet(cluster.state().unreachable());
                    unreachableMembers.stream().map(Member::address).filter(a -> a.equals(knownAddress))
                            .findFirst().ifPresent(a ->
                    {
                        log.info("Old known address '{}' for WEAKLY UP member '{}' is unreachable, "
                                + "manually DOWN old node.", a, weaklyUpMember);
                        cluster.down(a);
                    });
                } else {
                    log.warning("New WEAKLY UP member is not known yet: '{}'", weaklyUpMember);
                }
            } catch (final UnknownHostException ex) {
                log.error(ex, "No DNS entry found for WEAKLY UP member: '{}'", weaklyUpMember);
            }
        } else {
            log.warning("No host defined in address '{}' for WEAKLY UP member: '{}'", address,
                    weaklyUpMember);
        }
    }

    private void handleMemberUp(final ClusterEvent.MemberUp memberUp) {
        final Member upMember = memberUp.member();
        log.debug("Member is UP: {}", upMember);

        final Address address = upMember.address();
        if (address.host().isDefined()) {
            try {
                final InetAddress inetAddress = InetAddress.getByName(address.host().get());
                log.debug("Found DNS entry '{}' for UP member: '{}'", inetAddress, upMember);

                knownAddresses.put(inetAddress.getHostName(), address);
            } catch (final UnknownHostException ex) {
                log.error(ex, "No DNS entry found for UP member: '{}'", upMember);
            }
        } else {
            log.warning("No host defined in address '{}' for UP member: '{}'", address, upMember);
        }

        scheduleMajorityCheck();
        cluster.sendCurrentClusterState(getSelf());
    }

    private void handleUnreachableMember(final ClusterEvent.UnreachableMember unreachableMemberEvent) {
        log.info("Member detected as UNREACHABLE: {}", unreachableMemberEvent.member());
        scheduleMajorityCheck();
        cluster.sendCurrentClusterState(getSelf());
    }

    private void handleMemberRemoved(final ClusterEvent.MemberRemoved memberRemoved) {
        log.info("Member was REMOVED: {}", memberRemoved.member());
        knownAddresses.keySet()
                .stream()
                .filter(hostname -> knownAddresses.get(hostname).equals(memberRemoved.member().address()))
                .findFirst()
                .ifPresent(knownAddresses::remove);
        scheduleMajorityCheck();
        cluster.sendCurrentClusterState(getSelf());
    }

    private void handleMemberLeft(final ClusterEvent.MemberLeft memberLeft) {
        log.info("Member LEFT: {}", memberLeft.member());
        knownAddresses.keySet()
                .stream()
                .filter(hostname -> knownAddresses.get(hostname).equals(memberLeft.member().address()))
                .findFirst()
                .ifPresent(knownAddresses::remove);
        scheduleMajorityCheck();
        cluster.sendCurrentClusterState(getSelf());
    }

    private void handleMemberExited(final ClusterEvent.MemberExited memberExited) {
        log.info("Member EXITED: {}", memberExited.member());
        knownAddresses.keySet()
                .stream()
                .filter(hostname -> knownAddresses.get(hostname).equals(memberExited.member().address()))
                .findFirst()
                .ifPresent(knownAddresses::remove);
        scheduleMajorityCheck();
        cluster.sendCurrentClusterState(getSelf());
    }

    private void handleMemberEvent(final ClusterEvent.MemberEvent memberEvent) {
        log.info("Unhandled Member event: {}", memberEvent);
    }

    private void handleCheckForMajority() {
        majorityCheck = null;

        final Set<Member> unreachableMembers = JavaConversions.setAsJavaSet(cluster.state().unreachable());
        final Set<Member> currentMembers = JavaConversions.setAsJavaSet(cluster.state().members());
        final Set<Member> reachableMembers =
                currentMembers.stream().filter(member -> !unreachableMembers.contains(member))
                        .collect(Collectors.toSet());

        if (!unreachableMembers.isEmpty()) {
            log.info("{} unreachable member(s) '{}' cause a check for majority against the remaining"
                            + " {} reachable member(s) '{}'", unreachableMembers.size(), unreachableMembers,
                    reachableMembers.size(), reachableMembers);

            if (unreachableMembers.size() > reachableMembers.size()) {
                // there is a network partition and we are in the minority part of the cluster --> DOWN us
                log.warning("Minority for service '{}' detected, manually DOWN myself ({})", serviceName,
                        cluster.selfAddress());
                cluster.down(cluster.selfAddress());
            } else if (unreachableMembers.size() < reachableMembers.size()) {
                // there is a network partition and we are in the majority part of the cluster --> DOWN others
                log.info("Majority for service '{}' detected, manually DOWN the minority: {}", serviceName,
                        unreachableMembers);
                unreachableMembers.stream().map(Member::address).forEach(cluster::down);
            } else {
                // there is a network partition and both sides are equal in size --> wait
                log.warning("Ongoing network partition for service '{}'.", serviceName);
                scheduleMajorityCheck();
            }
        } else {
            log.info("No unreachable members, cluster is working properly.");
        }
    }

    private void scheduleMajorityCheck() {
        scheduleMajorityCheck(CheckForMajority.newInstance());
    }

    private void scheduleMajorityCheck(final CheckForMajority message) {
        if (majorityCheckEnabled) {
            if (majorityCheck != null) {
                log.debug("Cancel previously scheduled Majority check.");
                majorityCheck.cancel();
            }

            log.debug("Majority check is scheduled with a delay of {} ms.", majorityCheckDelay.toMillis());
            majorityCheck = getContext().system().scheduler()
                    .scheduleOnce(new FiniteDuration(majorityCheckDelay.toNanos(), TimeUnit.NANOSECONDS), getSelf(),
                            message,
                            getContext().dispatcher(), getSelf());
        }
    }

    private static class CheckForMajority {

        private CheckForMajority() {
            // no-op
        }

        static CheckForMajority newInstance() {
            return new CheckForMajority();
        }

    }

}
