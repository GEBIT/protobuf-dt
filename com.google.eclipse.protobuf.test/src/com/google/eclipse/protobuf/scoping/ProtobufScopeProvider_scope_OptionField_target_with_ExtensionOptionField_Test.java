/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.scoping;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static com.google.eclipse.protobuf.junit.IEObjectDescriptions.descriptionsIn;
import static com.google.eclipse.protobuf.junit.core.IntegrationTestModule.integrationTestModule;
import static com.google.eclipse.protobuf.junit.core.XtextRule.overrideRuntimeModuleWith;
import static com.google.eclipse.protobuf.junit.matchers.ContainNames.contain;
import static com.google.eclipse.protobuf.protobuf.ProtobufPackage.Literals.OPTION_FIELD__TARGET;

import java.util.List;

import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;
import org.junit.Rule;
import org.junit.Test;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.eclipse.protobuf.protobuf.CustomFieldOption;
import com.google.eclipse.protobuf.protobuf.CustomOption;
import com.google.eclipse.protobuf.protobuf.ExtensionOptionField;
import com.google.eclipse.protobuf.protobuf.MessageField;
import com.google.eclipse.protobuf.protobuf.OptionField;
import com.google.inject.Inject;

/**
 * Tests for <code>{@link ProtobufScopeProvider#getScope(OptionField, EReference)}</code>.
 *
 * @author alruiz@google.com (Alex Ruiz)
 */
public class ProtobufScopeProvider_scope_OptionField_target_with_ExtensionOptionField_Test {
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(integrationTestModule());

  @Inject private ProtobufScopeProvider scopeProvider;
  @Inject private IResourceValidator validator;

  // syntax = "proto2";
  //
  // package com.google.proto;
  //
  // import 'google/protobuf/descriptor.proto';
  //
  // message Type {
  //   optional double code = 1;
  //   optional string name = 2;
  //   extensions 10 to max;
  // }
  //
  // extend Type {
  //   optional bool active = 10;
  // }
  //
  // extend google.protobuf.FileOptions {
  //   optional Type type = 1000;
  // }
  //
  // option (type).(active) = true;
  @Test public void should_provide_extend_message_fields_for_first_field_in_custom_option() {
    CustomOption option = xtext.find("type", ")", CustomOption.class);
    ExtensionOptionField codeOptionField = (ExtensionOptionField) option.getFields().get(0);
    IScope scope = scopeProvider.getScope(codeOptionField, OPTION_FIELD__TARGET);
    assertThat(descriptionsIn(scope), contain("active", "proto.active", "google.proto.active", "com.google.proto.active"));
    assertSame(xtext.find("active", MessageField.class), codeOptionField.getTarget());
    assertValidResource();
  }

  // syntax = "proto2";
  //
  // package com.google.proto;
  //
  // import 'google/protobuf/descriptor.proto';
  //
  // message Type {
  //   optional double code = 1;
  //   optional string name = 2;
  //   extensions 10 to max;
  // }
  //
  // extend Type {
  //   optional bool active = 10;
  // }
  //
  // extend .google.protobuf.FieldOptions {
  //   optional Type type = 1000;
  // }
  //
  // message Person {
  //   optional bool active = 1 [(type).(com.google.proto.active) = true];
  // }
  @Test public void should_provide_message_fields_for_first_field_in_field_custom_option() {
    CustomFieldOption option = xtext.find("type", ")", CustomFieldOption.class);
    ExtensionOptionField codeOptionField = (ExtensionOptionField) option.getFields().get(0);
    IScope scope = scopeProvider.getScope(codeOptionField, OPTION_FIELD__TARGET);
    assertThat(descriptionsIn(scope), contain("proto.active", "google.proto.active", "com.google.proto.active"));
    assertSame(xtext.find("active", MessageField.class), codeOptionField.getTarget());
    assertValidResource();
  }

  private void assertValidResource() {
    List<Issue> issues = validator.validate(xtext.resource(), CheckMode.ALL, null);
    assertTrue(issues.toString(), issues.isEmpty());
  }
}
