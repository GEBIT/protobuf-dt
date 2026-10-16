/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.model.util;

import static org.hamcrest.core.IsSame.sameInstance;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertNull;

import static com.google.eclipse.protobuf.junit.core.UnitTestModule.unitTestModule;
import static com.google.eclipse.protobuf.junit.core.XtextRule.overrideRuntimeModuleWith;

import org.eclipse.emf.ecore.EObject;
import org.junit.Rule;
import org.junit.Test;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.eclipse.protobuf.protobuf.MessageField;
import com.google.inject.Inject;

/**
 * Tests for <code>{@link ModelObjects#rootOf(EObject)}</code>.
 *
 * @author alruiz@google.com (Alex Ruiz)
 */
public class ModelObjects_rootOf_Test {
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(unitTestModule());

  @Inject private ModelObjects modelObjects;

  // syntax = "proto2";
  //
  // message Person {
  //   optional string name = 1;
  // }
  @Test public void should_return_root_of_proto() {
    MessageField field = xtext.find("name", MessageField.class);
    assertThat(modelObjects.rootOf(field), sameInstance(xtext.root()));
  }

  @Test public void should_return_null_for_null_or_detached_context() {
    assertNull(modelObjects.rootOf(null));
    assertNull(modelObjects.rootOf(com.google.eclipse.protobuf.protobuf.ProtobufFactory.eINSTANCE.createMessage()));
    assertNull(modelObjects.rootOf(com.google.eclipse.protobuf.protobuf.ProtobufFactory.eINSTANCE.createProtobuf()));
    assertNull(modelObjects.packageOf(null));
    assertNull(modelObjects.packageOf(com.google.eclipse.protobuf.protobuf.ProtobufFactory.eINSTANCE.createMessage()));
  }
}
