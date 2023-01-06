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
import * as Utils from '../utils.js';

class CrudToolbar extends HTMLElement {
  isEditing = false;
  dom = {
    label: null,
    inputIdValue: null,
    buttonCrudEdit: null,
    buttonCreate: null,
    buttonUpdate: null,
    buttonDelete: null,
    iEdit: null,
    divRoot: null,
  };

  get idValue() {
    return this.dom.inputIdValue.value;
  }

  set idValue(newValue) {
    this.dom.inputIdValue.value = newValue;
    this.dom.buttonDelete.disabled = (newValue === null);
  }

  get editDisabled() {
    return this.dom.buttonCrudEdit.disabled;
  }

  set editDisabled(newValue) {
    this.dom.buttonCrudEdit.disabled = newValue;
  }

  get validationElement() {
    return this.dom.inputIdValue;
  }

  constructor() {
    super();
    this.attachShadow({mode: 'open'});
  }

  connectedCallback() {
    this.shadowRoot.append(document.getElementById('templateCrudToolbar').content.cloneNode(true));
    setTimeout(() => {
      Utils.getAllElementsById(this.dom, this.shadowRoot);
      this.dom.buttonCrudEdit.onclick = () => this.toggleEdit();
      this.dom.label.innerText = this.getAttribute('label') || 'Label';
      this.dom.buttonCreate.onclick = this.eventDispatcher('onCreateClick');
      this.dom.buttonUpdate.onclick = this.eventDispatcher('onUpdateClick');
      this.dom.buttonDelete.onclick = this.eventDispatcher('onDeleteClick');
    });
  };

  eventDispatcher(eventName) {
    return () => {
      this.dispatchEvent(new CustomEvent(eventName, {
        composed: true,
      }));
    };
  }

  toggleEdit() {
    this.isEditing = !this.isEditing;
    document.getElementById('modalCrudEdit').classList.toggle('editBackground');
    this.dom.divRoot.classList.toggle('editForground');
    this.dom.iEdit.classList.toggle('bi-pencil-square');
    this.dom.iEdit.classList.toggle('bi-x-square');
    this.dom.buttonCrudEdit.title = this.isEditing ? 'Cancel' : 'Edit';
    this.dom.buttonCreate.disabled = !(this.isEditing && !this.dom.inputIdValue.value);
    this.dom.buttonUpdate.disabled = !(this.isEditing && this.dom.inputIdValue.value);
    if (this.isEditing || !this.dom.inputIdValue.value) {
      this.dom.buttonDelete.disabled = true;
    }
    const allowIdChange = this.isEditing && (!this.dom.inputIdValue.value || this.hasAttribute('allowIdChange'));
    this.dom.inputIdValue.disabled = !allowIdChange;
    this.dispatchEvent(new CustomEvent('onEditToggle', {
      composed: true,
      detail: this.isEditing,
    }));
  }
}

customElements.define('crud-toolbar', CrudToolbar);
