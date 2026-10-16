package com.google.eclipse.protobuf.ui.refactoring;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.ui.refactoring.IRenameStrategy;
import org.eclipse.xtext.ui.refactoring.impl.DefaultRenameStrategyProvider;
import org.eclipse.xtext.ui.refactoring.ui.IRenameElementContext;

import com.google.eclipse.protobuf.protobuf.BuiltinLiteral;
import com.google.eclipse.protobuf.protobuf.MapType;
import com.google.eclipse.protobuf.protobuf.MessageField;

/** Implicit map roles and scalar constants are not user declarations. */
public class ProtobufRenameStrategyProvider extends DefaultRenameStrategyProvider {
  @Override public IRenameStrategy get(EObject target, IRenameElementContext context) throws NoSuchStrategyException {
    if (target instanceof BuiltinLiteral
        || target instanceof MessageField && target.eContainer() instanceof MapType) {
      return null;
    }
    return super.get(target, context);
  }
}
