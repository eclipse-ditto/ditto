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

/* eslint-disable require-jsdoc */
import * as API from '../api.js';
import * as Utils from '../utils.js';
import * as Things from './things.js';

export function WoTDescription(targetTab, forFeature) {
  let tabLink;
  let aceWoTDescription;
  let viewDirty = false;
  const _forFeature = forFeature;
  const domTheFeatureId = document.getElementById('theFeatureId');

  const ready = async () => {
    const tabId = Utils.addTab(
        document.getElementById(targetTab.itemsId),
        document.getElementById(targetTab.contentId),
        'Description',
        await( await fetch('modules/things/wotDescription.html')).text(),
    );

    tabLink = document.querySelector(`a[data-bs-target="#${tabId}"]`);
    tabLink.onclick = onTabActivated;

    const aceId = Math.random().toString(36).replace('0.', '');
    document.getElementById('aceDescription').id = aceId;

    aceWoTDescription = Utils.createAceEditor(aceId, 'ace/mode/json', true);
  };

  const onReferenceChanged = () => {
    if (tabLink.classList.contains('active')) {
      refreshDescription();
    } else {
      viewDirty = true;
    }
  };

  return {
    ready,
    onReferenceChanged,
  };

  function onTabActivated() {
    if (viewDirty) {
      refreshDescription();
      viewDirty = false;
    }
  }

  function refreshDescription() {
    let featurePath = '';
    if (_forFeature) {
      featurePath = '/features/' + domTheFeatureId.value;
    }
    aceWoTDescription.setValue('');
    if (Things.theThing && (!_forFeature || domTheFeatureId.value)) {
      API.callDittoREST(
          'GET',
          '/things/' + Things.theThing.thingId + featurePath,
          null,
          {
            'accept': 'application/td+json',
          },
      ).then((description) => {
        aceWoTDescription.setValue(JSON.stringify(description, null, 2), -1);
      }).catch(
          // nothing to clean-up
      );
    }
  };
}
