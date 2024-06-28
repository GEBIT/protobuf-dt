/*
 * Copyright (c) 2012 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.ui.editor;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.DefaultLineTracker;
import org.eclipse.jface.text.ITextViewerExtension7;
import org.eclipse.jface.text.TabsToSpacesConverter;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.xtext.ui.editor.XtextEditor;
import org.eclipse.xtext.ui.editor.XtextSourceViewerConfiguration;

import com.google.eclipse.protobuf.ui.preferences.editor.general.EditorPreferences;
import com.google.inject.Inject;

/**
 * @author alruiz@google.com (Alex Ruiz)
 */
public class ProtobufEditor extends XtextEditor {
	
	@Inject EditorPreferences preferences;
	
	@Override
	protected void handlePreferenceStoreChanged(PropertyChangeEvent event) {
		super.handlePreferenceStoreChanged(event);
		
		fixTabsToSpaces();
	}
	
	@Override
	public void createPartControl(Composite parent) {
		super.createPartControl(parent);
		
		// Needed because super.createPartControl will call initializeSourceViewer
		// which sets this setting and which we cannot override
		fixTabsToSpaces();
	}
	
	
	protected void fixTabsToSpaces() {
		// We have our own tabs-to-spaces setting in order to comply with protolint style guide
		if(preferences.shouldInsertSpacesForTabs()) {
			installTabsToSpacesConverter();
		} else {
			uninstallTabsToSpacesConverter();
		}
	}
	
	@Override
	protected void installTabsToSpacesConverter() {
		// Code mostly duplicated from superclass; overridden to fix indent to 2 spaces
		if (getSourceViewer() instanceof ITextViewerExtension7) {
			int tabWidth= 2; // The protobuf indent is fixed to 2 by style guide
			TabsToSpacesConverter tabToSpacesConverter= new TabsToSpacesConverter();
			tabToSpacesConverter.setLineTracker(new DefaultLineTracker());
			tabToSpacesConverter.setNumberOfSpacesPerTab(tabWidth);
			((ITextViewerExtension7) getSourceViewer()).setTabsToSpacesConverter(tabToSpacesConverter);
			updateIndentPrefixes();
		}
	}
	
}
