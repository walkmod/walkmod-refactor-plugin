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

import org.walkmod.javalang.ast.expr.ArrayAccessExpr;
import org.walkmod.javalang.ast.expr.ArrayCreationExpr;
import org.walkmod.javalang.ast.expr.BinaryExpr;
import org.walkmod.javalang.ast.expr.BinaryExpr.Operator;
import org.walkmod.javalang.ast.expr.BooleanLiteralExpr;
import org.walkmod.javalang.ast.expr.CastExpr;
import org.walkmod.javalang.ast.expr.CharLiteralExpr;
import org.walkmod.javalang.ast.expr.ClassExpr;
import org.walkmod.javalang.ast.expr.ConditionalExpr;
import org.walkmod.javalang.ast.expr.DoubleLiteralExpr;
import org.walkmod.javalang.ast.expr.Expression;
import org.walkmod.javalang.ast.expr.FieldAccessExpr;
import org.walkmod.javalang.ast.expr.InstanceOfExpr;
import org.walkmod.javalang.ast.expr.IntegerLiteralExpr;
import org.walkmod.javalang.ast.expr.IntegerLiteralMinValueExpr;
import org.walkmod.javalang.ast.expr.LongLiteralExpr;
import org.walkmod.javalang.ast.expr.LongLiteralMinValueExpr;
import org.walkmod.javalang.ast.expr.MethodCallExpr;
import org.walkmod.javalang.ast.expr.NameExpr;
import org.walkmod.javalang.ast.expr.NullLiteralExpr;
import org.walkmod.javalang.ast.expr.ObjectCreationExpr;
import org.walkmod.javalang.ast.expr.QualifiedNameExpr;
import org.walkmod.javalang.ast.expr.StringLiteralExpr;
import org.walkmod.javalang.ast.expr.SuperExpr;
import org.walkmod.javalang.ast.expr.ThisExpr;
import org.walkmod.javalang.visitors.VoidVisitorAdapter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.walkmod.exceptions.WalkModException;
import org.walkmod.javalang.compiler.SymbolTable;
import org.walkmod.javalang.compiler.Type;
import org.walkmod.javalang.compiler.TypeTable;
import org.walkmod.walkers.VisitorContext;

public class ExpressionTypeAnalyzer extends VoidVisitorAdapter<VisitorContext> {

	public static final String TYPE_KEY = "type_key";

	/**
	 * Metode que ha de tenir el tipus de retorn
	 */
	public static final String REQUIRED_METHOD = "required_method";

	/**
	 * Atribut que ha de tenir el tipus de retorn
	 */
	public static final String REQUIRED_ATTRIBUTE = "required_attribute";

	private TypeTable typeTable;

	private SymbolTable symbolTable;

	public ExpressionTypeAnalyzer(TypeTable typeTable, SymbolTable symbolTable) {
		this.typeTable = typeTable;
		this.symbolTable = symbolTable;

	}

	@Override
	public void visit(ArrayAccessExpr n, VisitorContext arg) {
		n.getName().accept(this, arg);
		org.walkmod.javalang.compiler.Type arrayType = (org.walkmod.javalang.compiler.Type) arg
				.remove(TYPE_KEY);
		org.walkmod.javalang.compiler.Type newType = new org.walkmod.javalang.compiler.Type();
		newType.setName(arrayType.getName());
		newType.setParameterizedTypes(arrayType.getParameterizedTypes());
		newType.setArrayCount(arrayType.getArrayCount() - 1);
		arg.put(TYPE_KEY, newType);
	}

	@Override
	public void visit(ArrayCreationExpr n, VisitorContext arg) {
		Type arrayType = typeTable.valueOf(n.getType());
		arrayType.setArrayCount(1);
		arg.put(TYPE_KEY, arrayType);
	}

	@Override
	public void visit(BinaryExpr n, VisitorContext arg) {

		n.getLeft().accept(this, arg);
		org.walkmod.javalang.compiler.Type leftType = (org.walkmod.javalang.compiler.Type) arg
				.remove(TYPE_KEY);

		n.getRight().accept(this, arg);
		org.walkmod.javalang.compiler.Type rightType = (org.walkmod.javalang.compiler.Type) arg
				.remove(TYPE_KEY);

		org.walkmod.javalang.compiler.Type resultType = leftType;

		try {
			if (TypeTable.isCompatible(typeTable.getJavaClass(leftType),
					typeTable.getJavaClass(rightType))) {
				resultType = rightType;
			}
		} catch (ClassNotFoundException e) {
			throw new WalkModException(e);
		}

		if (n.getOperator().equals(Operator.plus)) {

			if (leftType.getName().equals("java.lang.String")) {
				resultType = leftType;
			} else if (rightType.getName().equals("java.lang.String")) {
				resultType = rightType;
			}
		}

		arg.put(TYPE_KEY, resultType);

	}

	@Override
	public void visit(BooleanLiteralExpr n, VisitorContext arg) {
		arg.put(TYPE_KEY, new org.walkmod.javalang.compiler.Type("boolean"));
	}

	@Override
	public void visit(CastExpr n, VisitorContext arg) {
		arg.put(TYPE_KEY, typeTable.valueOf(n.getType()));
	}

	@Override
	public void visit(CharLiteralExpr n, VisitorContext arg) {
		arg.put(TYPE_KEY, new org.walkmod.javalang.compiler.Type("char"));
	}

	@Override
	public void visit(ClassExpr n, VisitorContext arg) {
		arg.put(TYPE_KEY, new org.walkmod.javalang.compiler.Type(
				"java.lang.Class"));
	}

	@Override
	public void visit(ConditionalExpr n, VisitorContext arg) {
		// then and else expression must have the same type
		n.getThenExpr().accept(this, arg);
	}

	@Override
	public void visit(DoubleLiteralExpr n, VisitorContext arg) {
		arg.put(TYPE_KEY, new org.walkmod.javalang.compiler.Type("double"));
	}

	@Override
	public void visit(FieldAccessExpr n, VisitorContext arg) {

		arg.put(REQUIRED_ATTRIBUTE, n);

		MethodCallExpr requiredMethod = (MethodCallExpr) arg
				.remove(REQUIRED_METHOD);

		n.getScope().accept(this, arg);

		arg.remove(REQUIRED_ATTRIBUTE);

		if (requiredMethod != null) {
			arg.put(REQUIRED_METHOD, requiredMethod);
		}

		org.walkmod.javalang.compiler.Type scopeType = (org.walkmod.javalang.compiler.Type) arg
				.remove(TYPE_KEY);

		Class<?> c = null;

		try {
			c = typeTable.getJavaClass(scopeType);
			Field field = null;
			if (c.isArray() && n.getField().equals("length")) {

				arg.put(TYPE_KEY, new org.walkmod.javalang.compiler.Type("int"));
			} else {
				try {

					field = c.getDeclaredField(n.getField());

				} catch (NoSuchFieldException fe) {

					try {
						field = c.getField(n.getField());

					} catch (NoSuchFieldException fe2) {
						// it is an inner class parsed as a field declaration
						c = typeTable.getJavaClass(c.getName() + "$"
								+ n.getField());
						scopeType.setName(c.getName());
						arg.put(TYPE_KEY, scopeType);
						return;
					}

				}

				Map<String, Type> typeMapping = new HashMap<String, Type>();

				TypeVariable<?>[] typeParams = c.getTypeParameters();

				if (typeParams != null) {

					for (int i = 0; i < typeParams.length; i++) {
						if (scopeType != null
								&& scopeType.getParameterizedTypes() != null) {
							typeMapping.put(typeParams[i].getName(), scopeType
									.getParameterizedTypes().get(i));
						} else {
							typeMapping.put(typeParams[i].getName(), new Type(
									"java.lang.Object"));
						}
					}

				}
				arg.put(TYPE_KEY,
						typeTable.valueOf(field.getType(), typeMapping));
			}

		} catch (ClassNotFoundException e) {
			throw new WalkModException(e);

		} catch (Exception e) {
			throw new WalkModException(e);

		}
	}

	@Override
	public void visit(InstanceOfExpr n, VisitorContext arg) {
		arg.put(TYPE_KEY, new org.walkmod.javalang.compiler.Type("boolean"));
	}

	@Override
	public void visit(IntegerLiteralExpr n, VisitorContext arg) {
		arg.put(TYPE_KEY, new org.walkmod.javalang.compiler.Type("int"));
	}

	@Override
	public void visit(IntegerLiteralMinValueExpr n, VisitorContext arg) {
		arg.put(TYPE_KEY, new org.walkmod.javalang.compiler.Type("int"));
	}

	@Override
	public void visit(LongLiteralExpr n, VisitorContext arg) {
		arg.put(TYPE_KEY, new org.walkmod.javalang.compiler.Type("long"));
	}

	@Override
	public void visit(LongLiteralMinValueExpr n, VisitorContext arg) {
		arg.put(TYPE_KEY, new org.walkmod.javalang.compiler.Type("long"));
	}

	@Override
	public void visit(MethodCallExpr n, VisitorContext arg) {
		try {
			Type scope;

			MethodCallExpr antRequiredMethod = (MethodCallExpr) arg
					.remove(ExpressionTypeAnalyzer.REQUIRED_METHOD);

			FieldAccessExpr faccess = (FieldAccessExpr) arg
					.remove(REQUIRED_ATTRIBUTE);

			if (n.getScope() != null) {

				arg.put(ExpressionTypeAnalyzer.REQUIRED_METHOD, n);

				n.getScope().accept(this, arg);

				arg.remove(ExpressionTypeAnalyzer.REQUIRED_METHOD);

				scope = (Type) arg.remove(TYPE_KEY);

			} else {
				scope = symbolTable.getType("this");
			}

			Class<?>[] typeArgs = null;
			if (n.getArgs() != null) {
				typeArgs = new Class[n.getArgs().size()];
				int i = 0;
				for (Expression e : n.getArgs()) {

					e.accept(this, arg);
					org.walkmod.javalang.compiler.Type argType = (org.walkmod.javalang.compiler.Type) arg
							.remove(TYPE_KEY);
					typeArgs[i] = typeTable.getJavaClass(argType);
					i++;
				}
			}

			if (antRequiredMethod != null) {
				arg.put(REQUIRED_METHOD, antRequiredMethod);
			}
			if (faccess != null) {
				arg.put(REQUIRED_ATTRIBUTE, faccess);
			}

			MethodCallExpr requiredMethod = (MethodCallExpr) arg
					.remove(REQUIRED_METHOD);

			FieldAccessExpr requiredField = (FieldAccessExpr) arg
					.remove(REQUIRED_ATTRIBUTE);

			Map<String, Type> typeMapping = new HashMap<String, Type>();

			Class<?> clazz = typeTable.getJavaClass(scope);

			TypeVariable<?>[] typeParams = clazz.getTypeParameters();

			if (typeParams != null) {

				for (int i = 0; i < typeParams.length; i++) {
					if (scope != null && scope.getParameterizedTypes() != null) {
						typeMapping.put(typeParams[i].getName(), scope
								.getParameterizedTypes().get(i));
					} else {
						typeMapping.put(typeParams[i].getName(), new Type(
								"java.lang.Object"));
					}
				}

			}

			Method method = getMethod(scope, n.getName(), typeArgs,
					n.getArgs(), requiredMethod, requiredField, arg,
					typeMapping);

			arg.put(TYPE_KEY, typeTable.getMethodType(method, typeMapping));

		} catch (ClassNotFoundException e) {
			throw new WalkModException(e);

		} catch (Exception e) {
			throw new WalkModException(e);
		}

	}

	private Method getMethod(org.walkmod.javalang.compiler.Type scope, // scope
																		// to
																		// find
			// the method
			String methodName, // method name to look for.
								// Multiple methods with the same name can exist
								// in a taxonomy.
								// Methods are not included into the
								// symbolTable. These are found by Java
								// introspection.
			Class<?>[] typeArgs, // java types of the argument expressions
			List<Expression> argumentValues, MethodCallExpr requiredMethod, // required
																			// method
																			// into
																			// the
																			// return
																			// type
			FieldAccessExpr requiredField, // required field into the return
											// type
			VisitorContext arg, // context
			Map<String, Type> typeMapping // mapping for Java Generics applied
											// into the scope
	) throws ClassNotFoundException {

		Class<?> clazz = typeTable.getJavaClass(scope);

		Method[] classMethods = clazz.getMethods();

		for (Method method : classMethods) {

			TypeVariable<?>[] typeVariables = method.getTypeParameters();

			if (typeVariables != null) {
				for (int i = 0; i < typeVariables.length; i++) {

					java.lang.reflect.Type[] parameterTypes = method
							.getGenericParameterTypes();

					if (parameterTypes != null && argumentValues != null) {

						for (int j = 0; j < parameterTypes.length
								&& j < argumentValues.size(); j++) {

							if (parameterTypes[j] instanceof ParameterizedType) {

								String variableName = ((ParameterizedType) parameterTypes[j])
										.getActualTypeArguments()[0].toString();

								if (variableName.length() == 1) {
									if (argumentValues.get(j) instanceof ClassExpr) {
										Class<?> paramClass = typeTable
												.getJavaClass(((ClassExpr) argumentValues
														.get(j)).getType());

										org.walkmod.javalang.compiler.Type auxType = new Type();
										auxType.setName(paramClass.getName());

										typeMapping.put(variableName, auxType);
									}
								}
							}
						}
					}
				}
			}

			org.walkmod.javalang.compiler.Type returnType = typeTable
					.getMethodType(method, typeMapping);

			if (isCompatible(method, methodName, typeArgs,
					typeTable.getJavaClass(returnType), requiredMethod,
					requiredField, arg)) {
				arg.put(TYPE_KEY, returnType);
				typeMapping.putAll(typeMapping);
				return method;
			}

		}

		classMethods = clazz.getDeclaredMethods();

		for (Method method : classMethods) {

			TypeVariable<?>[] typeVariables = method.getTypeParameters();

			if (typeVariables != null) {

				for (int i = 0; i < typeVariables.length; i++) {

					java.lang.reflect.Type[] parameterTypes = method
							.getGenericParameterTypes();

					if (parameterTypes != null && argumentValues != null) {

						for (int j = 0; j < parameterTypes.length
								&& j < argumentValues.size(); j++) {

							if (parameterTypes[j] instanceof ParameterizedType) {

								String variableName = ((ParameterizedType) parameterTypes[j])
										.getActualTypeArguments()[0].toString();

								if (variableName.length() == 1) {
									if (argumentValues.get(j) instanceof ClassExpr) {
										Class<?> paramClass = typeTable
												.getJavaClass(((ClassExpr) argumentValues
														.get(j)).getType());

										org.walkmod.javalang.compiler.Type auxType = new Type();
										auxType.setName(paramClass.getName());

										typeMapping.put(variableName, auxType);
									}
								}
							}
						}
					}
				}
			}

			org.walkmod.javalang.compiler.Type returnType = typeTable
					.getMethodType(method, typeMapping);

			if (isCompatible(method, methodName, typeArgs,
					typeTable.getJavaClass(returnType), requiredMethod,
					requiredField, arg)) {
				arg.put(TYPE_KEY, returnType);
				typeMapping.putAll(typeMapping);
				return method;
			}
		}

		Method result = null;
		if (clazz.isMemberClass()) {

			result = getMethod(new org.walkmod.javalang.compiler.Type(clazz
					.getEnclosingClass().getName()), methodName, typeArgs,
					argumentValues, requiredMethod, requiredField, arg,
					typeMapping);
		} else if (clazz.isAnonymousClass()) {
			result = getMethod(new org.walkmod.javalang.compiler.Type(clazz
					.getEnclosingClass().getName()), methodName, typeArgs,
					argumentValues, requiredMethod, requiredField, arg,
					typeMapping);
		}
		if (result == null && clazz.getSuperclass() != null) {

			return getMethod(new org.walkmod.javalang.compiler.Type(clazz
					.getSuperclass().getName()), methodName, typeArgs,
					argumentValues, requiredMethod, requiredField, arg,
					typeMapping);
		}
		return result;

	}

	private boolean isCompatible(Method method, String name,
			Class<?>[] typeArgs, Class<?> returnType,
			MethodCallExpr requiredMethod, FieldAccessExpr requiredField,
			VisitorContext arg) throws ClassNotFoundException {

		Class<?> lastVariableTypeArg = null;

		if (method.getName().equals(name)) {

			int numParams = typeArgs == null ? 0 : typeArgs.length;

			if ((method.getParameterTypes().length == numParams)
					|| method.isVarArgs()) {

				if (method.isVarArgs()) {

					if (method.getParameterTypes().length < numParams) {

						lastVariableTypeArg = method.getParameterTypes()[method
								.getParameterTypes().length - 1];

						numParams = method.getParameterTypes().length;
					}
					if (method.getParameterTypes().length <= numParams) {
						// changing the last argument to an array
						Class<?>[] newTypeArgs = new Class<?>[method
								.getParameterTypes().length];

						for (int i = 0; i < newTypeArgs.length - 1; i++) {
							newTypeArgs[i] = typeArgs[i];
						}

						newTypeArgs[newTypeArgs.length - 1] = method
								.getParameterTypes()[method.getParameterTypes().length - 1];

						typeArgs = newTypeArgs;
					}
				}

				boolean isCompatible = true;
				Class<?>[] methodParameterTypes = method.getParameterTypes();

				for (int i = 0; i < numParams && isCompatible; i++) {

					isCompatible = TypeTable.isCompatible(typeArgs[i],
							methodParameterTypes[i]);
				}

				if (isCompatible && lastVariableTypeArg != null) {

					for (int j = numParams; j < typeArgs.length && isCompatible; j++) {
						isCompatible = TypeTable.isCompatible(typeArgs[j],
								lastVariableTypeArg);

					}

				}

				if (isCompatible) {

					if (requiredMethod != null) {

						List<Method> methods = new LinkedList<Method>();

						methods.addAll(Arrays.asList(returnType
								.getDeclaredMethods()));

						methods.addAll(Arrays.asList(returnType.getMethods()));

						Iterator<Method> it = methods.iterator();

						boolean returnTypeCompatible = false;

						while (it.hasNext() && !returnTypeCompatible) {

							Method currentMethod = it.next();

							// checking method name
							if (currentMethod.getName().equals(
									requiredMethod.getName())) {
								List<Expression> args = requiredMethod
										.getArgs();
								Class<?>[] parameterTypes = currentMethod
										.getParameterTypes();
								if (args != null) {
									boolean compatibleArgs = true;
									int k = 0;
									for (Expression argExpr : args) {
										argExpr.accept(this, arg);
										org.walkmod.javalang.compiler.Type typeArg = (org.walkmod.javalang.compiler.Type) arg
												.remove(TYPE_KEY);
										if (!TypeTable
												.isCompatible(typeTable
														.getJavaClass(typeArg),
														parameterTypes[k])) {
											compatibleArgs = false;
										}
										k++;
									}
									returnTypeCompatible = compatibleArgs;
								} else {
									returnTypeCompatible = true;
								}

							}
						}
						isCompatible = returnTypeCompatible;
					} else if (requiredField != null) {
						try {

							if (returnType.isArray()
									&& requiredField.getField()
											.equals("length")) {
								return true;
							}
							// the return type has the required field as public?
							returnType.getField(requiredField.getField());
							// the field has been found. Then, the method is
							// compatible
							return true;

						} catch (NoSuchFieldException e) {
							// searching in all fields
							Field[] fields = returnType.getDeclaredFields();
							String fieldName = requiredField.getField();
							isCompatible = false;
							for (int i = 0; i < fields.length && !isCompatible; i++) {
								isCompatible = (fields[i].getName()
										.equals(fieldName));
							}
						}

					}

				}

				if (isCompatible) {

					return true;
				}

			}
		}
		return false;
	}

	@Override
	public void visit(NameExpr n, VisitorContext arg) {
		Type type = symbolTable.getType(n.getName());

		if (type == null) {
			try {
				String className = typeTable.getJavaClass(n.getName())
						.getName();
				type = new Type();
				type.setName(className);
			} catch (ClassNotFoundException e) {

				throw new WalkModException(e);

			}
		}
		arg.put(TYPE_KEY, type);
	}

	@Override
	public void visit(NullLiteralExpr n, VisitorContext arg) {
		arg.put(TYPE_KEY, null);
	}

	@Override
	public void visit(ObjectCreationExpr n, VisitorContext arg) {
		arg.put(TYPE_KEY, typeTable.valueOf(n.getType()));
	}

	@Override
	public void visit(QualifiedNameExpr n, VisitorContext arg) {
		org.walkmod.javalang.compiler.Type type = new org.walkmod.javalang.compiler.Type(
				n.getName());
		NameExpr aux = n.getQualifier();
		while (aux != null) {
			type.setName(type.getName() + "." + aux.getName());
			if (aux instanceof QualifiedNameExpr) {
				aux = ((QualifiedNameExpr) aux).getQualifier();
			} else {
				aux = null;
			}
		}
		arg.put(TYPE_KEY, type);
	}

	@Override
	public void visit(StringLiteralExpr n, VisitorContext arg) {
		arg.put(TYPE_KEY, new org.walkmod.javalang.compiler.Type(
				"java.lang.String"));
	}

	@Override
	public void visit(SuperExpr n, VisitorContext arg) {
		arg.put(TYPE_KEY, symbolTable.getType("super"));
	}

	@Override
	public void visit(ThisExpr n, VisitorContext arg) {
		arg.put(TYPE_KEY, symbolTable.getType("this"));
	}

}
