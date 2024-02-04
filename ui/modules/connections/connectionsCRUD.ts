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

import * as API from '../api.js';
import * as Utils from '../utils.js';
import * as Connections from './connections.js';
import connectionTemplates from './connectionTemplates.json';
/* eslint-disable prefer-const */
/* eslint-disable max-len */
/* eslint-disable no-invalid-this */
/* eslint-disable require-jsdoc */

let dom = {
  buttonConnectionTemplates: null,
  ulConnectionTemplates: null,
  inputConnectionTemplate: null,
  crudConnection: null,
  editorValidationConnection: null,
};

let connectionEditor;
let incomingEditor;
let outgoingEditor;

let theConnection;
let hasErrors;


export function ready() {
  Connections.addChangeListener(setConnection);

  Utils.getAllElementsById(dom);

  Utils.addDropDownEntries(dom.ulConnectionTemplates, Object.keys(connectionTemplates));

  connectionEditor = Utils.createAceEditor('connectionEditor', 'ace/mode/json', true);
  incomingEditor = Utils.createAceEditor('connectionIncomingScript', 'ace/mode/javascript', true);
  outgoingEditor = Utils.createAceEditor('connectionOutgoingScript', 'ace/mode/javascript', true);

  dom.ulConnectionTemplates.addEventListener('click', onConnectionTemplatesClick);

  incomingEditor.on('blur', onScriptEditorBlur(incomingEditor, 'incomingScript'));
  outgoingEditor.on('blur', onScriptEditorBlur(outgoingEditor, 'outgoingScript'));

  connectionEditor.on('input', onConnectionEditorInput);
  connectionEditor.getSession().on('changeAnnotation', onConnectionEditorChangeAnnotation);

  dom.crudConnection.addEventListener('onEditToggle', onEditToggle);
  dom.crudConnection.addEventListener('onCreateClick', onCreateConnectionClick);
  dom.crudConnection.addEventListener('onUpdateClick', onUpdateConnectionClick);
  dom.crudConnection.addEventListener('onDeleteClick', onDeleteConnectionClick);
}

function onScriptEditorBlur(scriptEditor, fieldName) {
  return () => {
    if (!dom.crudConnection.isEditing || hasErrors) {
      return;
    }
    const editConnection = JSON.parse(connectionEditor.getValue());
    initializeMappings(editConnection);
    editConnection.mappingDefinitions.javascript.options[fieldName] = scriptEditor.getValue();
    connectionEditor.setValue(Utils.stringifyPretty(editConnection), -1);
  };

  function initializeMappings(connection) {
    if (!connection['mappingDefinitions']) {
      connection.mappingDefinitions = {
        javascript: {
          mappingEngine: 'JavaScript',
          options: {
            incomingScript: '',
            outgoingScript: '',
          },
        },
      };
    }
  }
}

function onConnectionEditorInput() {
  if (!connectionEditor.session.getUndoManager().isClean()) {
    dom.inputConnectionTemplate.value = null;
    dom.editorValidationConnection.classList.remove('is-invalid');
  }
}

function onConnectionEditorChangeAnnotation() {
  hasErrors = connectionEditor.getSession().getAnnotations().filter((a) => a.type === 'error').length > 0;
}

function onUpdateConnectionClick() {
  Utils.assert(!hasErrors, 'Errors in connection json', dom.editorValidationConnection);
  theConnection = JSON.parse(connectionEditor.getValue());
  API.callConnectionsAPI(
      'modifyConnection',
      () => {
        Connections.loadConnections(),
        dom.crudConnection.toggleEdit();
      },
      dom.crudConnection.idValue,
      theConnection,
  );
}

function onDeleteConnectionClick() {
  Utils.confirm(`Are you sure you want to delete connection<br>'${theConnection.name}'?`, 'Delete', () => {
    API.callConnectionsAPI(
        'deleteConnection',
        () => {
          Connections.setConnection(null);
          Connections.loadConnections();
        },
        dom.crudConnection.idValue,
    );
  });
}

function onCreateConnectionClick() {
  Utils.assert(connectionEditor.getValue() !== '', 'Please enter a connection configuration (select a template as a basis)', dom.editorValidationConnection);
  Utils.assert(!hasErrors, 'Errors in connection json', dom.editorValidationConnection);
  const newConnection = JSON.parse(connectionEditor.getValue());
  if (API.env() === 'ditto_2') {
    newConnection.id = Math.random().toString(36).replace('0.', '');
  } else {
    delete newConnection.id;
  };
  API.callConnectionsAPI(
      'createConnection',
      (connection) => {
        Connections.setConnection(connection, true);
        Connections.loadConnections();
        dom.crudConnection.toggleEdit();
      },
      null,
      newConnection,
  );
}

function onConnectionTemplatesClick(event) {
  dom.inputConnectionTemplate.value = event.target.textContent;
  const newConnection = JSON.parse(JSON.stringify(connectionTemplates[dom.inputConnectionTemplate.value]));
  setConnection(newConnection);
  dom.editorValidationConnection.classList.remove('is-invalid');
  connectionEditor.session.getUndoManager().markClean();
}

function setConnection(connection) {
  theConnection = connection;
  incomingEditor.setValue('');
  outgoingEditor.setValue('');
  if (theConnection) {
    dom.crudConnection.idValue = theConnection.id ? theConnection.id : null;
    connectionEditor.setValue(Utils.stringifyPretty(theConnection), -1);
    if (theConnection.mappingDefinitions && theConnection.mappingDefinitions.javascript) {
      incomingEditor.setValue(theConnection.mappingDefinitions.javascript.options.incomingScript, -1);
      outgoingEditor.setValue(theConnection.mappingDefinitions.javascript.options.outgoingScript, -1);
    }
  } else {
    dom.crudConnection.idValue = null;
    connectionEditor.setValue('');
  }
}

function onEditToggle(event) {
  const isEditing = event.detail.isEditing;
  dom.buttonConnectionTemplates.disabled = !isEditing;
  connectionEditor.setReadOnly(!isEditing);
  connectionEditor.renderer.setShowGutter(isEditing);
  incomingEditor.setReadOnly(!isEditing);
  incomingEditor.renderer.setShowGutter(isEditing);
  outgoingEditor.setReadOnly(!isEditing);
  outgoingEditor.renderer.setShowGutter(isEditing);
  if (!isEditing) {
    if (dom.crudConnection.idValue && dom.crudConnection.idValue !== '') {
      setConnection(theConnection);
    } else {
      setConnection(null);
    }
  }
}


