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
package org.walkmod.javalangrefactor.visitors;

import org.walkmod.javalang.ast.body.Parameter;
import org.walkmod.javalang.ast.expr.VariableDeclarationExpr;
import org.walkmod.javalang.ast.type.ClassOrInterfaceType;
import org.walkmod.javalang.visitors.VoidVisitorAdapter;
import org.walkmod.walkers.VisitorContext;

public class VariableTypeRefactor extends VoidVisitorAdapter<VisitorContext>{

	private ClassOrInterfaceType type;
	
	@Override
	public void visit(VariableDeclarationExpr n, VisitorContext arg) {
	
		n.setType(type);
		//TODO: Refactoring the type of the init expression for the last call method type
	}

	public ClassOrInterfaceType getType() {
		return type;
	}

	public void setType(ClassOrInterfaceType type) {
		this.type = type;
	}
	
	public void visit(Parameter n, VisitorContext arg){
		n.setType(type);
	}
	
	
}
