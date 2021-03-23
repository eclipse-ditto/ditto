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
 package org.eclipse.ditto.protocoladapter.things;

 import static java.util.Objects.requireNonNull;

 import org.eclipse.ditto.protocoladapter.Adaptable;
 import org.eclipse.ditto.protocoladapter.HeaderTranslator;
 import org.eclipse.ditto.protocoladapter.QueryCommandAdapter;
 import org.eclipse.ditto.protocoladapter.TopicPath;
 import org.eclipse.ditto.protocoladapter.adaptables.MappingStrategiesFactory;
 import org.eclipse.ditto.protocoladapter.signals.SignalMapper;
 import org.eclipse.ditto.protocoladapter.signals.SignalMapperFactory;
 import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;

 public final class RetrieveThingsCommandAdapter extends AbstractThingAdapter<RetrieveThings>
         implements QueryCommandAdapter<RetrieveThings> {

     private final SignalMapper<RetrieveThings> retrieveThingsSignalMapper =
             SignalMapperFactory.newRetrieveThingsSignalMapper();

     private RetrieveThingsCommandAdapter(final HeaderTranslator headerTranslator) {
         super(MappingStrategiesFactory.getRetrieveThingsCommandMappingStrategies(), headerTranslator);
     }

     /**
      * Returns a new RetrieveThingsCommandAdapter.
      *
      * @param headerTranslator translator between external and Ditto headers.
      * @return the adapter.
      */
     public static RetrieveThingsCommandAdapter of(final HeaderTranslator headerTranslator) {
         return new RetrieveThingsCommandAdapter(requireNonNull(headerTranslator));
     }

     @Override
     public boolean wildcardTopicRequired() {
         return true;
     }

     @Override
     protected String getType(final Adaptable adaptable) {
         return RetrieveThings.TYPE;
     }

     @Override
     protected Adaptable mapSignalToAdaptable(final RetrieveThings command, final TopicPath.Channel channel) {
         return retrieveThingsSignalMapper.mapSignalToAdaptable(command, channel);
     }
 }
