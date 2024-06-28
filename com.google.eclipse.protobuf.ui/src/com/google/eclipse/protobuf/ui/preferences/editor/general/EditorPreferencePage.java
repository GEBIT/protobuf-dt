/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.ui.preferences.editor.general;

import static org.eclipse.ui.dialogs.PreferencesUtil.createPreferenceDialogOn;

import static com.google.eclipse.protobuf.ui.preferences.editor.general.Messages.header;
import static com.google.eclipse.protobuf.ui.preferences.editor.general.Messages.insertSpacesForTabs;
import static com.google.eclipse.protobuf.ui.preferences.editor.general.PreferenceNames.INSERT_SPACES_FOR_TABS;
import static com.google.eclipse.protobuf.ui.preferences.pages.binding.BindingToButtonSelection.bindSelectionOf;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.xtext.ui.editor.preferences.IPreferenceStoreAccess;

import com.google.eclipse.protobuf.ui.preferences.pages.binding.PreferenceBinder;
import com.google.eclipse.protobuf.ui.preferences.pages.binding.PreferenceFactory;
import com.google.inject.Inject;

/**
 * General editor preferences.
 *
 * @author alruiz@google.com (Alex Ruiz)
 */
public class EditorPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
  @Inject private IPreferenceStoreAccess preferenceStoreAccess;

  private final PreferenceBinder preferenceBinder = new PreferenceBinder();

  private Button btnInsertSpacesForTabs;
  private IntegerFieldEditor fMaximumLineWidth;
	
  @Override protected Control createContents(Composite parent) {
    Composite contents = new Composite(parent, NONE);
    contents.setLayout(new GridLayout(1, false));

    Link link = new Link(contents, SWT.NONE);
    GridData gridData = new GridData(SWT.FILL, SWT.BEGINNING, true, false);
    gridData.widthHint = 150; // only expand further if anyone else requires it
    gridData.horizontalSpan = 2;
    link.setLayoutData(gridData);
    link.setText(header);
    link.addListener(SWT.Selection, new Listener() {
      @Override public void handleEvent(Event event) {
        String text = event.text;
        createPreferenceDialogOn(getShell(), text, null, null);
      }
    });
    
    btnInsertSpacesForTabs = new Button(contents, SWT.CHECK);
    btnInsertSpacesForTabs.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, true, false, 2, 1));
    btnInsertSpacesForTabs.setText(insertSpacesForTabs);
    
    fMaximumLineWidth = new IntegerFieldEditor(PreferenceNames.MAXIMUM_LINE_WIDTH, Messages.maximumLineWidth, contents);
	fMaximumLineWidth.setValidRange(80, 1000);
	fMaximumLineWidth.setPage(this);
	fMaximumLineWidth.setPreferenceStore(getPreferenceStore());
	fMaximumLineWidth.load();
    
    setUpBinding();
    preferenceBinder.applyValues();
    updateContents();
    addEventListeners();
    
    return contents;
  }
  
  private void setUpBinding() {
    PreferenceFactory factory = new PreferenceFactory(getPreferenceStore());
    preferenceBinder.addAll(
        bindSelectionOf(btnInsertSpacesForTabs).to(factory.newBooleanPreference(INSERT_SPACES_FOR_TABS))
    );
  }
  
  private void addEventListeners() {
	  btnInsertSpacesForTabs.addSelectionListener(new SelectionAdapter() {
      @Override public void widgetSelected(SelectionEvent e) {
        updateContents();
      }
    });
  }

  private void updateContents() {
    //boolean enabled = btnInsertSpacesForTabs.getSelection();
    //btnInAllLines.setEnabled(enabled);
    //btnInEditedLines.setEnabled(enabled);
  }

  @Override protected IPreferenceStore doGetPreferenceStore() {
    return preferenceStoreAccess.getWritablePreferenceStore();
  }

  @Override public boolean performOk() {
	fMaximumLineWidth.store();
	preferenceBinder.saveValues();
    
    return true;
  }

  @Override protected void performDefaults() {
    preferenceBinder.applyDefaults();
    fMaximumLineWidth.loadDefault();
    super.performDefaults();
    updateContents();
  }

  @Override public void init(IWorkbench workbench) {}
}
