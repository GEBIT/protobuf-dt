package com.google.eclipse.protobuf.linking;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.resource.DerivedStateAwareResource;
import org.eclipse.xtext.resource.IDerivedStateComputer;

import com.google.eclipse.protobuf.protobuf.BuiltinLiteral;
import com.google.eclipse.protobuf.protobuf.MapType;
import com.google.eclipse.protobuf.protobuf.MessageField;
import com.google.eclipse.protobuf.protobuf.Protobuf;
import com.google.eclipse.protobuf.protobuf.ProtobufFactory;

/** Resource-contained, nonserialized declarations used only by expected-value scopes. */
public class ProtobufDerivedStateComputer implements IDerivedStateComputer {
  @Override public void installDerivedState(DerivedStateAwareResource resource, boolean preLinkingPhase) {
    if (resource.getParseResult() == null || !(resource.getParseResult().getRootASTElement() instanceof Protobuf)) {
      return;
    }
    Protobuf root = (Protobuf) resource.getParseResult().getRootASTElement();
    if (root.getBuiltinLiterals().isEmpty()) {
      addBuiltin(root, "true", true, 0);
      addBuiltin(root, "false", false, 0);
      addBuiltin(root, "inf", false, Double.POSITIVE_INFINITY);
      addBuiltin(root, "infinity", false, Double.POSITIVE_INFINITY);
      addBuiltin(root, "nan", false, Double.NaN);
    }
    TreeIterator<EObject> contents = root.eAllContents();
    while (contents.hasNext()) {
      EObject element = contents.next();
      if (element instanceof MapType) {
        MapType map = (MapType) element;
        if (map.getEntryFields().isEmpty()) {
          addRole(map, "key", 1);
          addRole(map, "value", 2);
        }
      }
    }
  }

  private void addBuiltin(Protobuf root, String name, boolean booleanValue, double floatingValue) {
    BuiltinLiteral literal = ProtobufFactory.eINSTANCE.createBuiltinLiteral();
    literal.setName(name);
    literal.setBooleanValue(booleanValue);
    literal.setFloatingValue(floatingValue);
    root.getBuiltinLiterals().add(literal);
  }

  private void addRole(MapType map, String name, long index) {
    MessageField role = ProtobufFactory.eINSTANCE.createMessageField();
    role.setName(name);
    role.setIndex(index);
    map.getEntryFields().add(role);
  }

  @Override public void discardDerivedState(DerivedStateAwareResource resource) {
    if (resource.getParseResult() == null || !(resource.getParseResult().getRootASTElement() instanceof Protobuf)) {
      return;
    }
    Protobuf root = (Protobuf) resource.getParseResult().getRootASTElement();
    TreeIterator<EObject> contents = root.eAllContents();
    while (contents.hasNext()) {
      EObject element = contents.next();
      if (element instanceof MapType) {
        ((MapType) element).getEntryFields().clear();
      }
    }
    root.getBuiltinLiterals().clear();
  }
}
