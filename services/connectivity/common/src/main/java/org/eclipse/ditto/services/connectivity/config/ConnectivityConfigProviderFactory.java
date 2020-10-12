/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

/*
 * Copyright Bosch.IO GmbH 2020
 *
 * All rights reserved, also regarding any disposal, exploitation,
 * reproduction, editing, distribution, as well as in the event of
 * applications for industrial property rights.
 *
 * This software is the confidential and proprietary information
 * of Bosch.IO GmbH. You shall not disclose
 * such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you
 * entered into with Bosch.IO GmbH.
 */

package org.eclipse.ditto.services.connectivity.config;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.atteo.classindex.ClassIndex;

import akka.actor.ActorSystem;
import akka.actor.DynamicAccess;
import akka.actor.ExtendedActorSystem;
import scala.Tuple2;
import scala.jdk.javaapi.CollectionConverters;
import scala.reflect.ClassTag;
import scala.reflect.ClassTag$;
import scala.util.Try;

/**
 * TODO DG
 */
public final class ConnectivityConfigProviderFactory {

    public static ConnectivityConfigProvider getInstance(final ActorSystem actorSystem) {

        final Iterable<Class<? extends ConnectivityConfigProvider>> subclasses =
                ClassIndex.getSubclasses(ConnectivityConfigProvider.class);

        return StreamSupport.stream(subclasses.spliterator(), false)
                .filter(c -> !DittoConnectivityConfigProvider.class.equals(c))
                .map(c -> {
                    final ClassTag<ConnectivityConfigProvider> tag =
                            ClassTag$.MODULE$.apply(ConnectivityConfigProvider.class);

                    final List<Tuple2<Class<?>, Object>> args = Stream.of(c.getConstructors())
                            .filter(con -> con.getParameterCount() == 1 &&
                                    con.getParameterTypes()[0].equals(ActorSystem.class))
                            .findAny()
                            .map(con -> new Tuple2<Class<?>, Object>(ActorSystem.class, actorSystem))
                            .map(Collections::singletonList)
                            .orElse(Collections.emptyList());

                    final DynamicAccess dynamicAccess = ((ExtendedActorSystem) actorSystem).dynamicAccess();
                    final Try<ConnectivityConfigProvider> providerBox = dynamicAccess.createInstanceFor(c,
                            CollectionConverters.asScala(args).toList(), tag);

                    return providerBox.get();
                })
                .findFirst().orElse(new DittoConnectivityConfigProvider(actorSystem));
    }

}
