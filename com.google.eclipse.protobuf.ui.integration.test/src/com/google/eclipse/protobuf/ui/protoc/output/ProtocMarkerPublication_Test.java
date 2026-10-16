/*
 * Copyright (c) 2026 GEBIT Solutions GmbH.
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.ui.protoc.output;

import static com.google.eclipse.protobuf.junit.core.XtextRule.createWith;
import static com.google.eclipse.protobuf.ui.validation.MarkerTypes.EDITOR_CHECK;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.eclipse.protobuf.ui.plugin.ProtobufEditorPlugIn;
import com.google.inject.Inject;

/** Exercises the registered Eclipse markers, including compiler/editor error deduplication. */
public class ProtocMarkerPublication_Test {
  private static final String MESSAGE = "Required fields are not allowed in proto3.";
  private static final String OUTPUT = "negative.proto:3:12: " + MESSAGE;

  @Rule public XtextRule xtext = createWith(ProtobufEditorPlugIn.injector());
  @Inject private ProtocOutputParser parser;
  private IProject project;
  private IFile file;

  @Before public void setUp() throws Exception {
    project = ResourcesPlugin.getWorkspace().getRoot().getProject("compiler-markers-" + System.nanoTime());
    project.create(null);
    project.open(null);
    file = project.getFile("negative.proto");
    file.create(new ByteArrayInputStream(("syntax = \"proto3\";\nmessage Negative {\n"
        + "  required int32 value = 1;\n}\n").getBytes(StandardCharsets.UTF_8)), true, null);
  }

  @After public void tearDown() throws Exception {
    if (project != null && project.exists()) project.delete(true, true, null);
  }

  @Test public void should_publish_compiler_errors_on_the_reported_file_and_line() throws Exception {
    assertTrue(parser.parseAndAddMarkerIfNecessary(OUTPUT, new ProtocMarkerFactory(file)));
    IMarker[] markers = file.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO);
    assertEquals(1, markers.length);
    assertEquals(file, markers[0].getResource());
    assertEquals(3, markers[0].getAttribute(IMarker.LINE_NUMBER, -1));
    assertEquals(IMarker.SEVERITY_ERROR, markers[0].getAttribute(IMarker.SEVERITY, -1));
    assertEquals(MESSAGE, markers[0].getAttribute(IMarker.MESSAGE, ""));
  }

  @Test public void should_replace_stale_compiler_errors_without_duplicating_editor_errors() throws Exception {
    IMarker editor = file.createMarker(EDITOR_CHECK);
    editor.setAttribute(IMarker.MESSAGE, MESSAGE);
    editor.setAttribute(IMarker.LINE_NUMBER, 3);
    editor.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
    new ProtocMarkerFactory(file).createErrorIfNecessary("negative.proto", 1, "Previous compiler error");
    assertTrue(parser.parseAndAddMarkerIfNecessary(OUTPUT, new ProtocMarkerFactory(file)));
    IMarker[] markers = file.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO);
    assertEquals(1, markers.length);
    assertEquals(editor.getId(), markers[0].getId());
    assertEquals(3, markers[0].getAttribute(IMarker.LINE_NUMBER, -1));
    assertEquals(MESSAGE, markers[0].getAttribute(IMarker.MESSAGE, ""));
  }
}
