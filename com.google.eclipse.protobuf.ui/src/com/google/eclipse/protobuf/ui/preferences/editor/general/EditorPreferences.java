/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.ui.preferences.editor.general;

import static com.google.eclipse.protobuf.ui.preferences.editor.general.PreferenceNames.INSERT_SPACES_FOR_TABS;
import static com.google.eclipse.protobuf.ui.preferences.editor.general.PreferenceNames.MAXIMUM_LINE_WIDTH;

import com.google.eclipse.protobuf.preferences.DefaultPreservingInitializer;
import com.google.inject.Inject;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.xtext.ui.editor.preferences.IPreferenceStoreAccess;

/**
 * @author Rene Schneider
 */
public class EditorPreferences {
  private final IPreferenceStore store;

  @Inject public EditorPreferences(IPreferenceStoreAccess storeAccess) {
    this.store = storeAccess.getWritablePreferenceStore();
  }

  public boolean shouldInsertSpacesForTabs() {
    return store.getBoolean(INSERT_SPACES_FOR_TABS);
  }
  
  public int maximumLineWidth() {
	  return store.getInt(MAXIMUM_LINE_WIDTH);
  }

  public static class Initializer extends DefaultPreservingInitializer {
    @Override
    public void setDefaults() {
      setDefault(INSERT_SPACES_FOR_TABS, true);
      setDefault(MAXIMUM_LINE_WIDTH, 120);
    }
  }
}