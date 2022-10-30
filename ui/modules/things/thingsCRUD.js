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
// @ts-check
import * as API from '../api.js';

import * as Utils from '../utils.js';
import * as ThingsSearch from './thingsSearch.js';
import * as Things from './things.js';

let thingJsonEditor;

let thingTemplates;

const dom = {
  thingDetails: null,
  thingId: null,
  buttonCreateThing: null,
  buttonSaveThing: null,
  buttonDeleteThing: null,
  inputThingDefinition: null,
  ulThingDefinitions: null,
};

/**
 * Initializes components. Should be called after DOMContentLoaded event
 */
export async function ready() {
  Things.addChangeListener(onThingChanged);

  Utils.getAllElementsById(dom);

  thingJsonEditor = Utils.createAceEditor('thingJsonEditor', 'ace/mode/json');

  loadThingTemplates();

  dom.ulThingDefinitions.addEventListener('click', onThingDefinitionsClick);
  dom.buttonCreateThing.onclick = onCreateThingClick;
  dom.buttonSaveThing.onclick = onSaveThingClick;
  dom.buttonDeleteThing.onclick = onDeleteThingClick;

  document.querySelector('a[data-bs-target="#tabModifyThing"]').addEventListener('shown.bs.tab', (event) => {
    thingJsonEditor.renderer.updateFull();
  });
}

function onDeleteThingClick() {
  return () => {
    Utils.assert(dom.thingId.value, 'Thing ID is empty', dom.thingId);
    Utils.confirm(`Are you sure you want to delete thing<br>'${dom.thingId.value}'?`, 'Delete', () => {
      deleteThing(dom.thingId.value);
    });
  };
}

function onSaveThingClick() {
  Utils.assert(dom.thingId.value, 'Thing ID is empty', dom.thingId);
  putThing(dom.thingId.value, JSON.parse(thingJsonEditor.getValue()));
}

async function onCreateThingClick() {
  const editorValue = thingJsonEditor.getValue();
  if (dom.thingId.value !== undefined && dom.thingId.value !== '') {
    API.callDittoREST('PUT',
        '/things/' + dom.thingId.value,
        editorValue === '' ? {} : JSON.parse(editorValue),
        {
          'if-none-match': '*',
        },
    ).then((data) => {
      Things.refreshThing(data.thingId, () => {
        ThingsSearch.getThings([data.thingId]);
      });
    });
  } else {
    API.callDittoREST('POST', '/things', editorValue === '' ? {} : JSON.parse(editorValue))
        .then((data) => {
          Things.refreshThing(data.thingId, () => {
            ThingsSearch.getThings([data.thingId]);
          });
        });
  }
}

function onThingDefinitionsClick(event) {
  Things.setTheThing(null);
  dom.inputThingDefinition.value = event.target.textContent;
  thingJsonEditor.setValue(JSON.stringify(thingTemplates[event.target.textContent], null, 2), -1);
}

function loadThingTemplates() {
  fetch('templates/thingTemplates.json')
      .then((response) => {
        response.json().then((loadedTemplates) => {
          thingTemplates = loadedTemplates;
          Utils.addDropDownEntries(dom.ulThingDefinitions, Object.keys(thingTemplates));
        });
      });
}

function putThing(thingId, thingJson) {
  API.callDittoREST('PUT', `/things/${thingId}`, thingJson,
      {
        'if-match': '*',
      },
  ).then(() => {
    Things.refreshThing(thingId, null);
  });
}

function deleteThing(thingId) {
  API.callDittoREST('DELETE', `/things/${thingId}`, null,
      {
        'if-match': '*',
      },
  ).then(() => {
    ThingsSearch.performLastSearch();
  });
}

/**
 * Update UI components for the given Thing
 * @param {Object} thingJson Thing json
 */
function onThingChanged(thingJson) {
  updateThingDetailsTable();
  updateThingJsonEditor();

  function updateThingDetailsTable() {
    dom.thingDetails.innerHTML = '';
    if (thingJson) {
      Utils.addTableRow(dom.thingDetails, 'thingId', false, true, thingJson.thingId);
      Utils.addTableRow(dom.thingDetails, 'policyId', false, true, thingJson.policyId);
      Utils.addTableRow(dom.thingDetails, 'definition', false, true, thingJson.definition ?? '');
      Utils.addTableRow(dom.thingDetails, 'revision', false, true, thingJson._revision);
      Utils.addTableRow(dom.thingDetails, 'created', false, true, thingJson._created);
      Utils.addTableRow(dom.thingDetails, 'modified', false, true, thingJson._modified);
    }
  }

  function updateThingJsonEditor() {
    if (thingJson) {
      dom.thingId.value = thingJson.thingId;
      dom.inputThingDefinition.value = thingJson.definition ?? '';
      const thingCopy = JSON.parse(JSON.stringify(thingJson));
      delete thingCopy['_revision'];
      delete thingCopy['_created'];
      delete thingCopy['_modified'];
      thingJsonEditor.setValue(JSON.stringify(thingCopy, null, 2), -1);
    } else {
      dom.thingId.value = null;
      dom.inputThingDefinition.value = null;
      thingJsonEditor.setValue('');
    }
  }
}


