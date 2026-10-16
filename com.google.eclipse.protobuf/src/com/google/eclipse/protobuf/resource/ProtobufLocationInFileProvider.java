package com.google.eclipse.protobuf.resource;

import static com.google.eclipse.protobuf.protobuf.ProtobufPackage.Literals.MAP_TYPE__KEY_TYPE;
import static com.google.eclipse.protobuf.protobuf.ProtobufPackage.Literals.MAP_TYPE__VALUE_TYPE;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.resource.DefaultLocationInFileProvider;
import org.eclipse.xtext.util.ITextRegion;

import com.google.eclipse.protobuf.protobuf.BuiltinLiteral;
import com.google.eclipse.protobuf.protobuf.MapType;
import com.google.eclipse.protobuf.protobuf.MessageField;

/** Derived values have references, not fabricated source declarations. */
public class ProtobufLocationInFileProvider extends DefaultLocationInFileProvider {
  @Override protected ITextRegion doGetTextRegion(EObject object, RegionDescription query) {
    if (object instanceof BuiltinLiteral) {
      return ITextRegion.EMPTY_REGION;
    }
    if (object instanceof MessageField && object.eContainer() instanceof MapType) {
      MessageField role = (MessageField) object;
      MapType map = (MapType) role.eContainer();
      if (role.getIndex() == 1L) {
        return getTextRegion(map, MAP_TYPE__KEY_TYPE, 0, query);
      }
      if (role.getIndex() == 2L) {
        return getTextRegion(map, MAP_TYPE__VALUE_TYPE, 0, query);
      }
      return ITextRegion.EMPTY_REGION;
    }
    return super.doGetTextRegion(object, query);
  }
}
