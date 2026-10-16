/*
 * Copyright (c) 2026 GEBIT Solutions GmbH.
 *
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package com.google.eclipse.protobuf.junit.core;

import static com.google.eclipse.protobuf.junit.core.IntegrationTestModule.integrationTestModule;
import static com.google.eclipse.protobuf.junit.core.UnitTestModule.unitTestModule;
import static com.google.eclipse.protobuf.junit.core.XtextRule.overrideRuntimeModuleWith;
import static org.junit.Assert.*;

import java.util.List;

import org.eclipse.emf.ecore.EValidator;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.resource.IResourceServiceProvider;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import com.google.eclipse.protobuf.protobuf.ComplexTypeLink;
import com.google.eclipse.protobuf.protobuf.ComplexValueField;
import com.google.eclipse.protobuf.protobuf.CustomFieldOption;
import com.google.eclipse.protobuf.protobuf.CustomOption;
import com.google.eclipse.protobuf.protobuf.ExtensionOptionField;
import com.google.eclipse.protobuf.protobuf.Message;
import com.google.eclipse.protobuf.protobuf.MessageField;
import com.google.eclipse.protobuf.protobuf.ProtobufPackage;
import com.google.eclipse.protobuf.protobuf.SimpleValueField;
import com.google.inject.Injector;
import com.google.inject.Module;

public class XtextRule_Test {
  @Rule public XtextRule xtext = overrideRuntimeModuleWith(integrationTestModule());

  // // Create file injector-isolation.proto
  // syntax = "proto3";
  // package fixture.isolation;
  // message Payload { int32 id = 1; }

  // syntax = "proto3";
  // import "injector-isolation.proto";
  // message Holder { fixture.isolation.Payload payload = 1; }
  @Test public void should_validate_with_its_own_injector_after_an_unrelated_fixture() {
    Object validator = EValidator.Registry.INSTANCE.get(ProtobufPackage.eINSTANCE);
    Object factory = Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().get("proto");
    Object provider = IResourceServiceProvider.Registry.INSTANCE.getExtensionToFactoryMap().get("proto");
    XtextRule unrelated = overrideRuntimeModuleWith(unitTestModule());
    unrelated.parseText("syntax = \"proto3\"; message Foreign { int32 id = 1; }");
    List<Issue> unrelatedIssues = issues(unrelated);
    assertTrue(unrelatedIssues.toString(), unrelatedIssues.isEmpty());
    assertSame(validator, EValidator.Registry.INSTANCE.get(ProtobufPackage.eINSTANCE));
    assertSame(factory, Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().get("proto"));
    assertSame(provider, IResourceServiceProvider.Registry.INSTANCE.getExtensionToFactoryMap().get("proto"));
    List<Issue> issues = issues(xtext);
    assertTrue(issues.toString(), issues.isEmpty());
    assertFalse(xtext.findFirst(ComplexTypeLink.class).getTarget().eIsProxy());

    xtext.parseText("syntax = \"proto3\"; message Broken { required int32 id = 1; }");
    assertFalse(issues(xtext).isEmpty());
  }

  @Test public void should_restore_registrations_when_a_fixture_fails() throws Throwable {
    final Object validator = EValidator.Registry.INSTANCE.get(ProtobufPackage.eINSTANCE);
    final Object provider = IResourceServiceProvider.Registry.INSTANCE.getExtensionToFactoryMap().get("proto");
    final AssertionError failure = new AssertionError("fixture failed");
    XtextRule nested = overrideRuntimeModuleWith(unitTestModule());
    Statement statement = nested.apply(new Statement() {
      @Override public void evaluate() {
        assertNotSame(validator, EValidator.Registry.INSTANCE.get(ProtobufPackage.eINSTANCE));
        assertNotSame(provider, IResourceServiceProvider.Registry.INSTANCE.getExtensionToFactoryMap().get("proto"));
        throw failure;
      }
    }, new FrameworkMethod(getClass().getMethod("should_restore_registrations_when_a_fixture_fails")), new Object());
    try {
      statement.evaluate();
      fail("Expected fixture failure");
    } catch (AssertionError expected) {
      assertSame(failure, expected);
    }
    assertSame(validator, EValidator.Registry.INSTANCE.get(ProtobufPackage.eINSTANCE));
    assertSame(provider, IResourceServiceProvider.Registry.INSTANCE.getExtensionToFactoryMap().get("proto"));
  }

  @Test public void should_restore_registrations_when_setup_fails() {
    Object validator = EValidator.Registry.INSTANCE.get(ProtobufPackage.eINSTANCE);
    Object factory = Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().get("proto");
    Object provider = IResourceServiceProvider.Registry.INSTANCE.getExtensionToFactoryMap().get("proto");
    final IllegalStateException failure = new IllegalStateException("setup failed");
    try {
      XtextRule.createWith(new OverrideRuntimeModuleSetup(new Module[] { unitTestModule() }) {
        @Override public void register(Injector injector) {
          super.register(injector);
          throw failure;
        }
      });
      fail("Expected setup failure");
    } catch (IllegalStateException expected) {
      assertSame(failure, expected);
    }
    assertSame(validator, EValidator.Registry.INSTANCE.get(ProtobufPackage.eINSTANCE));
    assertSame(factory, Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().get("proto"));
    assertSame(provider, IResourceServiceProvider.Registry.INSTANCE.getExtensionToFactoryMap().get("proto"));
  }

  // Create file grouped-fixture-first.proto
  // syntax = "proto3";
  // package fixture.grouped.first;
  // message First { int32 id = 1; }
  // // Create file grouped-fixture-second.proto
  // syntax = "proto3";
  // package fixture.grouped.second;
  // message Second { string text = 1; }

  // syntax = "proto3";
  // import "grouped-fixture-first.proto";
  // import "grouped-fixture-second.proto";
  // message Both { fixture.grouped.first.First first = 1; fixture.grouped.second.Second second = 2; }
  @Test public void should_create_each_named_file_in_a_shared_comment_block() {
    List<Issue> issues = issues(xtext);
    assertTrue(issues.toString(), issues.isEmpty());
    List<ComplexTypeLink> links = xtext.findAll(ComplexTypeLink.class);
    assertEquals(2, links.size());
    for (ComplexTypeLink link : links) {
      assertFalse(link.getTarget().eIsProxy());
    }
  }

  // syntax = "proto3";
  // message Previous { int32 old_field = 1; }
  @Test public void should_find_objects_in_the_latest_manually_parsed_resource() {
    Message previous = xtext.find("Previous", Message.class);
    xtext.parseText("syntax = \"proto3\"; message Current { int32 new_field = 1; }");
    Message current = xtext.find("Current", Message.class);
    assertNotSame(previous.eResource(), current.eResource());
    assertSame(xtext.resource(), current.eResource());
    assertSame(current, xtext.findAll(Message.class).get(0));
    assertEquals("new_field", xtext.findNode("new_field").getText());
  }

  @Test public void should_find_custom_roots_and_extension_paths_from_their_source_names() {
    xtext.parseText("syntax = \"proto2\"; import \"google/protobuf/descriptor.proto\"; "
        + "package fixture.finder; message Settings { optional string name = 1; "
        + "optional Settings child = 2; extensions 100 to max; } "
        + "extend Settings { optional string extra = 100; } "
        + "extend google.protobuf.FileOptions { optional Settings root = 50000; } "
        + "extend google.protobuf.FieldOptions { optional Settings field_root = 50001; } "
        + "option (fixture.finder.root).(fixture.finder.extra) = \"path\"; "
        + "message Holder { optional string text = 1 [(fixture.finder.field_root) = { child { name: \"value\" } }]; }");
    CustomOption option = xtext.find("fixture.finder.root", ")", CustomOption.class);
    assertEquals("root", ((MessageField) option.getSource().getTarget()).getName());
    ExtensionOptionField field = xtext.find("fixture.finder.extra", ")", ExtensionOptionField.class);
    assertSame(option.getFields().get(0), field);
    assertEquals("extra", ((MessageField) field.getTarget()).getName());
    CustomFieldOption fieldOption = xtext.find("fixture.finder.field_root", ")", CustomFieldOption.class);
    assertEquals("field_root", ((MessageField) fieldOption.getSource().getTarget()).getName());
    ComplexValueField child = xtext.find("child", " {", ComplexValueField.class);
    assertEquals("child", ((MessageField) child.getName().getTarget()).getName());
    SimpleValueField value = xtext.find("name", ":", SimpleValueField.class);
    assertEquals("name", ((MessageField) value.getName().getTarget()).getName());
    List<Issue> issues = issues(xtext);
    assertTrue(issues.toString(), issues.isEmpty());
  }

  @Test public void should_find_an_unresolved_option_without_resolving_its_declaration() {
    xtext.parseText("syntax = \"proto2\"; option (missing.root) = { field: 1 };");
    CustomOption option = xtext.find("missing.root", ")", CustomOption.class);
    assertTrue(option.getSource().getTarget().eIsProxy());
    assertSame(option, xtext.findAll(CustomOption.class).get(0));
  }

  private List<Issue> issues(XtextRule fixture) {
    return fixture.injector().getInstance(IResourceValidator.class)
        .validate(fixture.resource(), CheckMode.ALL, CancelIndicator.NullImpl);
  }
}
