package com.google.eclipse.protobuf.formatting;

import com.google.inject.ImplementedBy;

@ImplementedBy(IMaximumLineWidthProvider.Default.class)
public interface IMaximumLineWidthProvider {
	
	int maximumLineWidth();
	
	class Default implements IMaximumLineWidthProvider {
		
		@Override
		public int maximumLineWidth() {
			return 120;
		}
		
	}

}
