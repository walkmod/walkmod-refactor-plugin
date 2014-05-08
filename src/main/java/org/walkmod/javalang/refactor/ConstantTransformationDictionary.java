/* 
  Copyright (C) 2013 Raquel Pau and Albert Coroleu.
 
 Walkmod is free software: you can redistribute it and/or modify
 it under the terms of the GNU Lesser General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.
 
 Walkmod is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Lesser General Public License for more details.
 
 You should have received a copy of the GNU Lesser General Public License
 along with Walkmod.  If not, see <http://www.gnu.org/licenses/>.*/
package org.walkmod.javalang.refactor;

import org.walkmod.javalang.ast.expr.Expression;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;



public class ConstantTransformationDictionary {
	
	private Collection<ConstantTransformationRule> constantTransformations = new LinkedList<ConstantTransformationRule>();
	
	public Expression get(String originalValue, String targetType){
		for(ConstantTransformationRule transformation : constantTransformations){
			if(transformation.match(originalValue, targetType)){
				return transformation.getTargetASTExpr();
			}
		}
		return null;
	}
	
	public Collection<Expression> get(String originalValue){
		Collection<Expression> result = new LinkedList<Expression>();
		for(ConstantTransformationRule transformation : constantTransformations){
			if(transformation.matchOriginalValue(originalValue)){
				result.add(transformation.getTargetASTExpr());
			}
		}
		return result;
	}

	public void addAll(Map<String, String> transformations) {
		for(Entry<String,String> entry : transformations.entrySet()){
			ConstantTransformationRule rule = new ConstantTransformationRule();
			rule.setSourceConstantExpr(entry.getKey());
			rule.setTargetConstantExpr(entry.getValue());
			constantTransformations.add(rule);
		}
	}
	
	public boolean hasEnumTransformation(String originalValue){
		
		for(ConstantTransformationRule transformation : constantTransformations){
			if(transformation.matchOriginalValue(originalValue)){
				return transformation.isEnum();
			}
		}
		return false;
	}

}
