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
import * as Things from './things.js';
import * as ThingsSearch from './thingsSearch.js';
import thingTemplates from './thingTemplates.json';

let thingJsonEditor;

let eTag;

const dom = {
  tbodyThingDetails: null,
  crudThings: null,
  buttonThingDefinitions: null,
  inputThingDefinition: null,
  ulThingDefinitions: null,
};

/**
 * Initializes components. Should be called after DOMContentLoaded event
 */
export async function ready() {
  Things.addChangeListener(onThingChanged);

  Utils.getAllElementsById(dom);

  thingJsonEditor = Utils.createAceEditor('thingJsonEditor', 'ace/mode/json', true);

  Utils.addDropDownEntries(dom.ulThingDefinitions, Object.keys(thingTemplates));

  dom.ulThingDefinitions.addEventListener('click', onThingDefinitionsClick);
  dom.crudThings.addEventListener('onCreateClick', onCreateThingClick);
  dom.crudThings.addEventListener('onUpdateClick', onUpdateThingClick);
  dom.crudThings.addEventListener('onDeleteClick', onDeleteThingClick);
  dom.crudThings.addEventListener('onEditToggle', onEditToggle);

  document.querySelector('a[data-bs-target="#tabModifyThing"]').addEventListener('shown.bs.tab', (event) => {
    thingJsonEditor.renderer.updateFull();
  });
}

function onDeleteThingClick() {
  Utils.confirm(`Are you sure you want to delete thing<br>'${dom.crudThings.idValue}'?`, 'Delete', () => {
    API.callDittoREST('DELETE', `/things/${dom.crudThings.idValue}`, null,
        {
          'if-match': '*',
        },
    ).then(() => {
      ThingsSearch.performLastSearch();
    });
  });
}

function onUpdateThingClick() {
  API.callDittoREST('PATCH', `/things/${dom.crudThings.idValue}`, JSON.parse(thingJsonEditor.getValue()),
      {
        'if-match': eTag || "*",
        'if-equal': 'skip-minimizing-merge'
      },
  ).then(() => {
    dom.crudThings.toggleEdit();
    Things.refreshThing(dom.crudThings.idValue, null);
  });
}

async function onCreateThingClick() {
  const editorValue = thingJsonEditor.getValue();
  if (dom.crudThings.idValue !== undefined && dom.crudThings.idValue !== '') {
    API.callDittoREST('PUT',
        '/things/' + dom.crudThings.idValue,
        editorValue === '' ? {} : JSON.parse(editorValue),
        {
          'if-none-match': '*',
        },
    ).then((data) => {
      dom.crudThings.toggleEdit();
      Things.refreshThing(data.thingId, () => {
        ThingsSearch.getThings([data.thingId]);
      });
    });
  } else {
    API.callDittoREST('POST', '/things', editorValue === '' ? {} : JSON.parse(editorValue))
        .then((data) => {
          dom.crudThings.toggleEdit();
          Things.refreshThing(data.thingId, () => {
            ThingsSearch.getThings([data.thingId]);
          });
        });
  }
}

function onThingDefinitionsClick(event) {
  Things.setTheThing(null);
  // isEditing = true;
  dom.inputThingDefinition.value = event.target.textContent;
  thingJsonEditor.setValue(Utils.stringifyPretty(thingTemplates[event.target.textContent]), -1);
}

/**
 * Update UI components for the given Thing
 * @param {Object} thingJson Thing json
 */
function onThingChanged(thingJson) {
  if (dom.crudThings.isEditing) {
    return;
  }

  updateThingDetailsTable();
  updateThingJsonEditor();

  function updateThingDetailsTable() {
    dom.tbodyThingDetails.innerHTML = '';
    if (thingJson) {
      Utils.addTableRow(dom.tbodyThingDetails, 'thingId', false, thingJson.thingId, thingJson.thingId);
      Utils.addTableRow(dom.tbodyThingDetails, 'policyId', false, thingJson.policyId, thingJson.policyId);
      Utils.addTableRow(dom.tbodyThingDetails, 'definition', false, thingJson.definition ?? ' ', thingJson.definition ?? '');
      Utils.addTableRow(dom.tbodyThingDetails, 'revision', false, thingJson._revision, thingJson._revision);
      Utils.addTableRow(dom.tbodyThingDetails, 'created', false, thingJson._created, thingJson._created);
      Utils.addTableRow(dom.tbodyThingDetails, 'modified', false, thingJson._modified, thingJson._modified);
    }
  }

  function updateThingJsonEditor() {
    if (thingJson) {
      dom.crudThings.idValue = thingJson.thingId;
      dom.inputThingDefinition.value = thingJson.definition ?? '';
      const thingCopy = JSON.parse(JSON.stringify(thingJson));
      delete thingCopy['_revision'];
      delete thingCopy['_created'];
      delete thingCopy['_modified'];
      delete thingCopy['_context'];
      thingJsonEditor.setValue(Utils.stringifyPretty(thingCopy), -1);
      thingJsonEditor.session.getUndoManager().reset();
    } else {
      dom.crudThings.idValue = null;
      dom.inputThingDefinition.value = null;
      thingJsonEditor.setValue('');
      thingJsonEditor.session.getUndoManager().reset();
    }
  }
}

function onEditToggle(event) {
  const isEditing = event.detail.isEditing;
  if (isEditing && Things.theThing) {
    API.callDittoREST('GET', `/things/${Things.theThing.thingId}`, null, null, true)
        .then((response) => {
          eTag = response.headers.get('ETag');
          return response.json();
        })
        .then((thingJson) => {
          enableDisableEditors();
          initializeEditors(thingJson);
        });
  } else {
    enableDisableEditors();
    resetEditors();
  }

  function enableDisableEditors() {
    dom.buttonThingDefinitions.disabled = !isEditing;
    dom.inputThingDefinition.disabled = !isEditing;
    thingJsonEditor.setReadOnly(!isEditing);
    thingJsonEditor.renderer.setShowGutter(isEditing);
  }

  function initializeEditors(thingJson) {
    dom.inputThingDefinition.value = thingJson.definition ?? '';
    thingJsonEditor.setValue(Utils.stringifyPretty(thingJson), -1);
  }

  function resetEditors() {
    if (dom.crudThings.idValue && dom.crudThings.idValue !== '') {
      Things.refreshThing(dom.crudThings.idValue, null);
    } else {
      dom.inputThingDefinition.value = null;
      thingJsonEditor.setValue('');
    }
  }
}


