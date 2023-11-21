/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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

import * as Utils from '../utils.js';
import * as API from '../api.js';
import * as Environments from '../environments/environments.js'
import * as ace from 'ace-builds/src-noconflict/ace';
import { CrudOperation, CrudToolbar } from '../utils/crudToolbar.js';
import * as Policies from './policies.js';
import policyTemplates from './policyTemplates.json';


type DomElements = {
  crudPolicyJson: CrudToolbar,
  selectPolicyJSONTemplate: HTMLSelectElement,
}

let dom: DomElements = {
  crudPolicyJson: null,
  selectPolicyJSONTemplate: null,
};

let policyEditor: ace.Editor;

export function ready() {
  
  Utils.getAllElementsById(dom);
  Policies.observable.addChangeListener(onPolicyChanged);

  policyEditor = Utils.createAceEditor('policyEditor', 'ace/mode/json', true);

  // dom.crudPolicyJson.editDisabled = true;
  dom.crudPolicyJson.addEventListener('onCreateClick', onCreatePolicyClick);
  dom.crudPolicyJson.addEventListener('onDeleteClick', onDeletePolicyClick);
  dom.crudPolicyJson.addEventListener('onUpdateClick', onUpdatePolicyClick);
  dom.crudPolicyJson.addEventListener('onEditToggle', onToggleEditJson);

  Utils.setOptions(dom.selectPolicyJSONTemplate, ['Select an example', ...Object.keys(policyTemplates)]);
  dom.selectPolicyJSONTemplate.addEventListener('change', onJSONTemplateChange);

  document.querySelector('a[data-bs-target="#tabPolicyJson"]').addEventListener('shown.bs.tab', (event) => {
    policyEditor.renderer.updateFull();
  });
}

function onJSONTemplateChange(event) {
  policyEditor.setValue(JSON.stringify(policyTemplates[event.target.value], null, 2), -1);
}

function onToggleEditJson(event) {
  policyEditor.setReadOnly(!event.detail.isEditing);
  policyEditor.renderer.setShowGutter(event.detail.isEditing);
  dom.selectPolicyJSONTemplate.disabled = !event.detail.isEditing;
  dom.selectPolicyJSONTemplate.selectedIndex = 0;
  if (event.detail.isCancel) {
    onPolicyChanged(Policies.thePolicy);
  }
}

function onCreatePolicyClick() {
  Utils.assert(dom.crudPolicyJson.idValue, 'PolicyId must not be empty', dom.crudPolicyJson.validationElement);
  modifyPolicy(
    dom.crudPolicyJson.idValue,
    JSON.parse(policyEditor.getValue()),
    () => {
      dom.crudPolicyJson.toggleEdit(false);
      Policies.refreshPolicy(dom.crudPolicyJson.idValue);
    }
  );
}

function onUpdatePolicyClick() {
  modifyPolicy(
    dom.crudPolicyJson.idValue,
    JSON.parse(policyEditor.getValue()),
    Policies.finishEditing(dom.crudPolicyJson, CrudOperation.UPDATE)
  );
}

function onDeletePolicyClick() {
  Utils.confirm(`Are you sure you want to delete policy<br>'${dom.crudPolicyJson.idValue}'?`, 'Delete', () => {
    modifyPolicy(dom.crudPolicyJson.idValue, null, () => {
      deleteFromRecentPolicies(dom.crudPolicyJson.idValue);
      Policies.setThePolicy(null);
    });
  });
  
  function deleteFromRecentPolicies(policyId: string) {
    const index = Environments.current().recentPolicyIds.indexOf(policyId);
    if (index >= 0) {
      Environments.current().recentPolicyIds.splice(index, 1);
    }
    Environments.environmentsJsonChanged('recentPolicyIds');
  }
}

function modifyPolicy(policyId, value, onSuccess) {
  API.callDittoREST(value ? 'PUT' : 'DELETE',
      `/policies/${policyId}`,
      value
  ).then(onSuccess);
};

function onPolicyChanged(policy: Policies.Policy) {
  dom.crudPolicyJson.idValue = policy && policy.policyId;
  if (policy) {
    policyEditor.setValue(JSON.stringify(policy, null, 2), -1);
  } else {
    policyEditor.setValue('');
  }
}
