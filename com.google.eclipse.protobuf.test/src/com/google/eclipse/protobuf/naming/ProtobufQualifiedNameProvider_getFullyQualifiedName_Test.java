/*
 * Copyright (c) 2011 Google Inc.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.naming;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

import static com.google.eclipse.protobuf.junit.core.UnitTestModule.unitTestModule;
import static com.google.eclipse.protobuf.junit.core.XtextRule.overrideRuntimeModuleWith;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.naming.QualifiedName;
import org.junit.Rule;
import org.junit.Test;

import com.google.eclipse.protobuf.junit.core.XtextRule;
import com.google.eclipse.protobuf.protobuf.Message;
import com.google.eclipse.protobuf.protobuf.MessageField;
import com.google.inject.Inject;

/**
 * Tests for <code>{@link ProtobufQualifiedNameProvider#getFullyQualifiedName(EObject)}</code>.
 *
 * @author alruiz@google.com (Alex Ruiz)
 */
public class ProtobufQualifiedNameProvider_getFullyQualifiedName_Test {
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(unitTestModule());

  @Inject private IProtobufQualifiedNameProvider provider;

  // syntax = "proto2";
  //
  // package fqn.test;
  //
  // message Person {
  //   optional string name = 1;
  // }
  @Test public void should_include_existing_package_name_as_part_of_message_FQN() {
    Message message = xtext.find("Person", Message.class);
    QualifiedName fqn = provider.getFullyQualifiedName(message);
    assertThat(fqn.toString(), equalTo("fqn.test.Person"));
  }

  // syntax = "proto2";
  //
  // package fqn.test;
  //
  // message Person {
  //   optional string name = 1;
  // }
  @Test public void should_include_existing_package_name_as_part_of_field_FQN() {
    MessageField field = xtext.find("name", MessageField.class);
    QualifiedName fqn = provider.getFullyQualifiedName(field);
    assertThat(fqn.toString(), equalTo("fqn.test.Person.name"));
  }

  // syntax = "proto2";
  //
  // message Person {
  //   optional string name = 1;
  // }
  @Test public void should_not_include_package_name_as_part_of_message_FQN_if_package_is_not_specified() {
    Message message = xtext.find("Person", Message.class);
    QualifiedName fqn = provider.getFullyQualifiedName(message);
    assertThat(fqn.toString(), equalTo("Person"));
  }

  // syntax = "proto2";
  //
  // message Person {
  //   optional string name = 1;
  // }
  @Test public void should_not_include_package_name_as_part_of_field_FQN_if_package_is_not_specified() {
    MessageField field = xtext.find("name", MessageField.class);
    QualifiedName fqn = provider.getFullyQualifiedName(field);
    assertThat(fqn.toString(), equalTo("Person.name"));
  }

  // syntax = "proto3";
  // package names;
  // message M { oneof choice { string member = 1; } enum E { VALUE = 0; } }
  @Test public void should_export_oneof_fields_and_enum_values_in_enclosing_scope() {
    assertThat(provider.getFullyQualifiedName(xtext.find("member", MessageField.class)).toString(),
        equalTo("names.M.member"));
    assertThat(provider.getFullyQualifiedName(xtext.find("choice", com.google.eclipse.protobuf.protobuf.OneOf.class)).toString(),
        equalTo("names.M.choice"));
    assertThat(provider.getFullyQualifiedName(xtext.find("VALUE", com.google.eclipse.protobuf.protobuf.Literal.class)).toString(),
        equalTo("names.M.VALUE"));
    assertThat(provider.getFullyQualifiedName(xtext.find("E", com.google.eclipse.protobuf.protobuf.Enum.class)).toString(),
        equalTo("names.M.E"));
  }

  // syntax = "proto2";
  // package same;
  // message same { optional same child = 1; }
  @Test public void should_qualify_local_names_even_when_they_match_the_package() {
    Message message = xtext.findFirst(Message.class);
    MessageField child = xtext.findFirst(MessageField.class);
    assertThat(provider.getFullyQualifiedName(message).toString(), equalTo("same.same"));
    assertThat(provider.getFullyQualifiedName(child).toString(), equalTo("same.same.child"));
    assertThat(((com.google.eclipse.protobuf.protobuf.ComplexTypeLink) child.getType()).getTarget(), equalTo(message));
  }
}
