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
package org.walkmod.javalangrefactor.config;

import java.util.LinkedList;
import java.util.List;

import org.walkmod.javalang.ast.body.Parameter;
import org.walkmod.javalang.ast.type.ClassOrInterfaceType;
import org.walkmod.javalang.ast.type.ReferenceType;
import org.walkmod.javalang.ast.type.Type;
import org.walkmod.javalang.compiler.TypeTable;


public class MethodHeaderDeclaration {

	private String scope;

	private String name;

	private List<Parameter> args = new LinkedList<Parameter>();

	private List<ClassOrInterfaceType> exceptions;

	private Type result;

	private int modifiers;
	
	private TypeTable typeTable;
	
	public void setTypeTable(TypeTable typeTable){
		this.typeTable=typeTable;
	}

	public String getScope() {
		return scope;
	}

	public void setScope(String scope) {
		this.scope = scope;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<Parameter> getArgs() {
		return args;
	}

	public void setArgs(List<Parameter> args) {
		if (args != null) {
			this.args = args;
		}
	}

	public Class<?>[] getArgTypeClasses() throws ClassNotFoundException {
		Class<?>[] res = new Class[this.args.size()];
		int i = 0;
		for (Parameter type : getArgs()) {
			Type t = type.getType();
			String typeName = "";
			if (t instanceof ReferenceType) {
				typeName = ((ClassOrInterfaceType) ((ReferenceType) t)
						.getType()).getName();
			}

			res[i] = typeTable.loadClass(typeName);
			i++;
		}
		return res;
	}

	public List<ClassOrInterfaceType> getExceptions() {
		return exceptions;
	}

	public void setExceptions(List<ClassOrInterfaceType> exceptions) {
		this.exceptions = exceptions;
	}

	public Type getResult() {
		return result;
	}

	public void setResult(Type result) {
		this.result = result;
	}

	public int getModifiers() {
		return modifiers;
	}

	public void setModifiers(int modifiers) {
		this.modifiers = modifiers;
	}

}
