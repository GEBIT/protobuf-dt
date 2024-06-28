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

import org.eclipse.osgi.util.NLS;

/**
 * @author alruiz@google.com (Alex Ruiz)
 */
public class Messages extends NLS {
  public static String header;
  public static String insertSpacesForTabs;
  public static String maximumLineWidth;

  static {
    // initialize resource bundle
    Class<Messages> type = Messages.class;
    NLS.initializeMessages(type.getName(), type);
  }

  private Messages() {}
}
