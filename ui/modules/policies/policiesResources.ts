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

import * as ace from 'ace-builds/src-noconflict/ace';
import * as Utils from '../utils.js';
import * as API from '../api.js';
import * as Policies from './policies.js';
import * as PolicyEntries from './policiesEntries.js';
import resourceTemplates from './resourceTemplates.json';
import { CrudOperation, CrudToolbar } from '../utils/crudToolbar.js';

let selectedResource: string;

type DomElements = {
  tbodyPolicyResources: HTMLTableElement,
  selectResourceTemplates: HTMLSelectElement,
  tableValidationResources: HTMLInputElement,
  crudResource: CrudToolbar,
}

let dom : DomElements = {
  tbodyPolicyResources: null,
  selectResourceTemplates: null,
  tableValidationResources: null,
  crudResource: null,
} ;

let resourceEditor: ace.Editor;

export function ready() {
  Utils.getAllElementsById(dom);
  PolicyEntries.observable.addChangeListener(onEntryChanged);
 
  Utils.addValidatorToTable(dom.tbodyPolicyResources, dom.tableValidationResources);
  
  resourceEditor = Utils.createAceEditor('resourceEditor', 'ace/mode/json', true);
  
  dom.tbodyPolicyResources.onclick = onPolicyResourcesClick;

  dom.crudResource.editDisabled = true;
  dom.crudResource.addEventListener('onCreateClick', onCreatePolicyResourceClick);
  dom.crudResource.addEventListener('onUpdateClick', onUpdatePolicyResourceClick);
  dom.crudResource.addEventListener('onDeleteClick', onDeletePolicyResourceClick);
  dom.crudResource.addEventListener('onEditToggle', onEditToggleResource);
  
  
  Utils.setOptions(dom.selectResourceTemplates, ['Select a resource template', ...Object.keys(resourceTemplates.resources)]);
  dom.selectResourceTemplates.addEventListener('change', onSelectResourceTemplateChange);
}

function onSelectResourceTemplateChange(event) {
  dom.crudResource.idValue = event.target.value;
  resourceEditor.setValue(JSON.stringify(resourceTemplates.resources[event.target.value], null, 2), -1);
}

function onPolicyResourcesClick(event): any {
  if (selectedResource === event.target.parentNode.id) {
    selectedResource = null;
    dom.crudResource.idValue = null;
    resourceEditor.setValue('');
  } else {
    selectedResource = event.target.parentNode.id;
    dom.crudResource.idValue = selectedResource;
    resourceEditor.setValue(
      JSON.stringify(Policies.thePolicy.entries[PolicyEntries.selectedEntry].resources[selectedResource], null, 2), -1);
  }
  dom.crudResource.idValue = selectedResource;
}

function onCreatePolicyResourceClick() {
  // validations(false, true, false, false, true);
  PolicyEntries.validateSelected();
  Utils.assert(dom.crudResource.idValue, 'Please enter a resource', dom.crudResource.validationElement);
  Utils.assert(!Object.keys(Policies.thePolicy.entries[PolicyEntries.selectedEntry].resources).includes(dom.crudResource.idValue),
      `Resource already exists`, dom.crudResource.validationElement);
  selectedResource = dom.crudResource.idValue;
  modifyResource(
    dom.crudResource.idValue,
    resourceEditor.getValue() !== '' ?
    JSON.parse(resourceEditor.getValue()) :
    {grant: ['READ', 'WRITE'], revoke: []},
    Policies.finishEditing(dom.crudResource, CrudOperation.CREATE)
  );
}

function onUpdatePolicyResourceClick() {
//  validations(false, true, false, false, false, true);
  PolicyEntries.validateSelected();
  Utils.assert(selectedResource, 'Please select a resource', dom.tableValidationResources);
  modifyResource(
    selectedResource,
    JSON.parse(resourceEditor.getValue()),
    Policies.finishEditing(dom.crudResource, CrudOperation.UPDATE)
  );
}

function onDeletePolicyResourceClick() {
  // validations(false, true, false, false, false, true);
  PolicyEntries.validateSelected();
  Utils.assert(selectedResource, 'Please select a resource', dom.tableValidationResources);
  modifyResource(selectedResource, null, Policies.finishEditing(dom.crudResource, CrudOperation.DELETE));
}

function modifyResource(key, value, onSuccess: (value: any) => any) {
  API.callDittoREST(value ? 'PUT' : 'DELETE',
      `/policies/${Policies.thePolicy.policyId}/entries/${PolicyEntries.selectedEntry}/resources/${key}`, value
  ).then(onSuccess);
};


function onEditToggleResource(event: CustomEvent) {
  dom.selectResourceTemplates.disabled = !event.detail.isEditing;
  dom.selectResourceTemplates.selectedIndex = 0;
  resourceEditor.setReadOnly(!event.detail.isEditing);
  resourceEditor.renderer.setShowGutter(event.detail.isEditing);
  if (event.detail.isCancel) {
    dom.crudResource.idValue = selectedResource;
    if (Policies.thePolicy && PolicyEntries.selectedEntry && selectedResource) {
      resourceEditor.setValue(JSON.stringify(Policies.thePolicy.entries[PolicyEntries.selectedEntry].resources[selectedResource], null, 2), -1);
    } else {
      resourceEditor.setValue('');
    }
  }
}



function onEntryChanged(entryLabel: string) {
  selectedResource = null;

  dom.tbodyPolicyResources.innerHTML = '';
  dom.crudResource.idValue = null;
  resourceEditor.setValue('');
  dom.crudResource.editDisabled = (entryLabel === null);

  if (Policies.thePolicy && entryLabel) {
    Object.keys(Policies.thePolicy.entries[entryLabel].resources).forEach((key) => {
      Utils.addTableRow(dom.tbodyPolicyResources, key, key === selectedResource, null,
          JSON.stringify(Policies.thePolicy.entries[entryLabel].resources[key])
      );
      if (key === selectedResource) {
        dom.crudResource.idValue = key;
        resourceEditor.setValue(JSON.stringify(Policies.thePolicy.entries[entryLabel].resources[key], null, 2), -1);
      }
    });
  }

}