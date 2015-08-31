/* 
  Copyright (C) 2015 Raquel Pau and Albert Coroleu.
 
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
package org.walkmod.refactor.visitors;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.walkmod.javalang.ast.Node;
import org.walkmod.javalang.ast.TypeParameter;
import org.walkmod.javalang.ast.expr.ObjectCreationExpr;
import org.walkmod.javalang.ast.type.ClassOrInterfaceType;
import org.walkmod.javalang.ast.type.PrimitiveType;
import org.walkmod.javalang.ast.type.PrimitiveType.Primitive;
import org.walkmod.javalang.ast.type.ReferenceType;
import org.walkmod.javalang.ast.type.Type;
import org.walkmod.javalang.ast.type.VoidType;
import org.walkmod.javalang.ast.type.WildcardType;
import org.walkmod.javalang.compiler.symbols.SymbolType;
import org.walkmod.javalang.compiler.symbols.SymbolTypeResolver;
import org.walkmod.javalang.compiler.types.TypesLoaderVisitor;
import org.walkmod.javalang.visitors.GenericVisitorAdapter;

public class ASTTypeNameResolver extends
		GenericVisitorAdapter<SymbolType, List<TypeParameter>> implements
		SymbolTypeResolver<Type> {

	private static ASTTypeNameResolver instance = null;


	private ASTTypeNameResolver() {

	}

	public static ASTTypeNameResolver getInstance() {
		if (instance == null) {
			instance = new ASTTypeNameResolver();
		}
		return instance;
	}

	@Override
	public SymbolType visit(PrimitiveType n, List<TypeParameter> arg) {
		SymbolType result = new SymbolType();
		Primitive pt = n.getType();
		if (pt.equals(Primitive.Boolean)) {
			result.setName(boolean.class.getName());

		} else if (pt.equals(Primitive.Char)) {
			result.setName(char.class.getName());
		} else if (pt.equals(Primitive.Double)) {
			result.setName(double.class.getName());
		} else if (pt.equals(Primitive.Float)) {
			result.setName(float.class.getName());
		} else if (pt.equals(Primitive.Int)) {
			result.setName(int.class.getName());
		} else if (pt.equals(Primitive.Long)) {
			result.setName(long.class.getName());
		} else if (pt.equals(Primitive.Short)) {
			result.setName(short.class.getName());
		} else if (pt.equals(Primitive.Byte)) {
			result.setName(byte.class.getName());
		}
		return result;
	}

	@Override
	public SymbolType visit(ClassOrInterfaceType type, List<TypeParameter> arg) {
		SymbolType result = null;

		String name = type.getName();
		ClassOrInterfaceType scope = type.getScope();
		Node parent = type.getParentNode();
		boolean isObjectCreationCtxt = (parent != null && parent instanceof ObjectCreationExpr);

		if (scope == null) {

			if (arg != null) {
				Iterator<TypeParameter> it = arg.iterator();
				while (it.hasNext() && result == null) {
					TypeParameter next = it.next();
					if (next.getName().equals(name)) {
						List<ClassOrInterfaceType> bounds = next.getTypeBound();
						if (bounds == null || bounds.isEmpty()) {
							result = new SymbolType(Object.class);
						} else {
							List<SymbolType> params = new LinkedList<SymbolType>();
							for (ClassOrInterfaceType bound : bounds) {
								params.add(bound.accept(this, arg));
							}
							result = new SymbolType(params);
						}
					}
				}
			}
			if (result == null) {

				result = new SymbolType(name);
				
			}

		} else {
			// it is a fully qualified name or a inner class (>1 hop)

			String scopeName = "";
			String parentName = "";

			ClassOrInterfaceType ctxt = type;
			while (ctxt.getScope() != null) {
				ctxt = (ClassOrInterfaceType) ctxt.getScope();
				if (ctxt.getSymbolData() != null) {
					scopeName = ctxt.getName() + "$" + scopeName;
				} else {
					scopeName = ctxt.getName() + "." + scopeName;
				}
			}
			scopeName = parentName + scopeName;

			String innerClassName = name;
			if (scopeName.length() > 1) {
				innerClassName = scopeName.substring(0, scopeName.length() - 1)
						+ "$" + name;
			}
			String fullName = scopeName + name;

			result = new SymbolType();
			try {
				TypesLoaderVisitor.getClassLoader().loadClass(fullName);
				result.setName(fullName);
			} catch (ClassNotFoundException e) {
				
				result.setName(innerClassName);
			}

		}

		if (type.getTypeArgs() != null) {
			if (result == null) {
				result = new SymbolType();
			}
			List<SymbolType> typeArgs = new LinkedList<SymbolType>();

			for (Type typeArg : type.getTypeArgs()) {
				SymbolType aux = valueOf(typeArg);
				if (aux == null) {
					aux = new SymbolType(Object.class);
				}
				typeArgs.add(aux);
			}
			if (!typeArgs.isEmpty()) {
				result.setParameterizedTypes(typeArgs);
			}
		}
		
		return result;
	}

	@Override
	public SymbolType visit(VoidType n, List<TypeParameter> arg) {
		return new SymbolType(Void.class.getName());
	}

	@Override
	public SymbolType visit(WildcardType n, List<TypeParameter> arg) {
		SymbolType result = null;
		if (n.toString().equals("?")) {
			result = new SymbolType("java.lang.Object");
		} else {
			List<SymbolType> upperBounds = null;
			List<SymbolType> lowerBounds = null;
			ReferenceType extendsRef = n.getExtends();
			ReferenceType superRef = n.getSuper();
			if (extendsRef != null) {

				SymbolType aux = extendsRef.accept(this, arg);
				if (aux != null) {
					upperBounds = new LinkedList<SymbolType>();
					upperBounds.add(aux);
				}

			} else {

				SymbolType aux = superRef.accept(this, arg);
				if (aux != null) {
					lowerBounds = new LinkedList<SymbolType>();
					lowerBounds.add(aux);
				}
			}
			if (upperBounds != null || lowerBounds != null) {
				result = new SymbolType(upperBounds, lowerBounds);
			}

		}
		return result;
	}

	public SymbolType visit(ReferenceType n, List<TypeParameter> arg) {
		Type containerType = n.getType();
		SymbolType result = null;
		if (containerType instanceof PrimitiveType) {
			result = new SymbolType(containerType.accept(this, arg).getName());

		} else if (containerType instanceof ClassOrInterfaceType) {

			result = containerType.accept(this, arg);

		}
		if (result != null) {
			result.setArrayCount(n.getArrayCount());
		}
		return result;
	}

	@Override
	public SymbolType valueOf(Type parserType) {
		return valueOf(parserType, (List<TypeParameter>) null);
	}

	public SymbolType valueOf(Type parserType, List<TypeParameter> tps) {
		return parserType.accept(this, tps);
	}

	@Override
	public SymbolType[] valueOf(List<Type> nodes) {
		if (nodes == null) {
			return new SymbolType[0];
		}
		SymbolType[] result = new SymbolType[nodes.size()];
		int i = 0;
		for (Type node : nodes) {
			result[i] = valueOf(node);
			i++;
		}
		return result;
	}

}
