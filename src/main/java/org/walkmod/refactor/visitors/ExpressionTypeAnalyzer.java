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
package org.walkmod.refactor.visitors;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.walkmod.exceptions.WalkModException;
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
import org.walkmod.javalang.compiler.SymbolTable;
import org.walkmod.javalang.compiler.SymbolType;
import org.walkmod.javalang.compiler.Types;
import org.walkmod.javalang.compiler.TypeTable;
import org.walkmod.javalang.visitors.VoidVisitorAdapter;
import org.walkmod.refactor.exceptions.InvalidTypeException;
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

	private static Logger LOG = Logger.getLogger(ExpressionTypeAnalyzer.class);

	public ExpressionTypeAnalyzer(TypeTable typeTable, SymbolTable symbolTable) {
		this.typeTable = typeTable;
		this.symbolTable = symbolTable;

	}

	@Override
	public void visit(ArrayAccessExpr n, VisitorContext arg) {
		n.getName().accept(this, arg);
		SymbolType arrayType = (SymbolType) arg.remove(TYPE_KEY);
		SymbolType newType = new SymbolType();
		newType.setName(arrayType.getName());
		newType.setParameterizedTypes(arrayType.getParameterizedTypes());
		newType.setArrayCount(arrayType.getArrayCount() - 1);
		arg.put(TYPE_KEY, newType);
	}

	@Override
	public void visit(ArrayCreationExpr n, VisitorContext arg) {
		SymbolType arrayType = typeTable.valueOf(n.getType());
		arrayType.setArrayCount(1);
		arg.put(TYPE_KEY, arrayType);
	}

	@Override
	public void visit(BinaryExpr n, VisitorContext arg) {

		n.getLeft().accept(this, arg);
		SymbolType leftType = (SymbolType) arg.remove(TYPE_KEY);

		n.getRight().accept(this, arg);
		SymbolType rightType = (SymbolType) arg.remove(TYPE_KEY);

		SymbolType resultType = leftType;

		try {
			if (Types.isCompatible(typeTable.loadClass(leftType),
					typeTable.loadClass(rightType))) {
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
		arg.put(TYPE_KEY, new SymbolType("boolean"));
	}

	@Override
	public void visit(CastExpr n, VisitorContext arg) {
		arg.put(TYPE_KEY, typeTable.valueOf(n.getType()));
	}

	@Override
	public void visit(CharLiteralExpr n, VisitorContext arg) {
		arg.put(TYPE_KEY, new SymbolType("char"));
	}

	@Override
	public void visit(ClassExpr n, VisitorContext arg) {
		arg.put(TYPE_KEY, new SymbolType("java.lang.Class"));
	}

	@Override
	public void visit(ConditionalExpr n, VisitorContext arg) {
		// then and else expression must have the same type
		n.getThenExpr().accept(this, arg);
	}

	@Override
	public void visit(DoubleLiteralExpr n, VisitorContext arg) {
		arg.put(TYPE_KEY, new SymbolType("double"));
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

		SymbolType scopeType = (SymbolType) arg.remove(TYPE_KEY);

		Class<?> c = null;

		try {
			if (scopeType == null) {
				try {
					c = typeTable.loadClass(n.toString());
					if (c != null) {
						String className = c.getName();
						scopeType = new SymbolType();
						scopeType.setName(className);
						arg.put(TYPE_KEY, scopeType);
					} else {
						arg.put(TYPE_KEY, null);
					}
				} catch (ClassNotFoundException e) {
					arg.put(TYPE_KEY, null);
				}
			} else {
				c = typeTable.loadClass(scopeType);

				Field field = null;
				if (c.isArray() && n.getField().equals("length")) {

					arg.put(TYPE_KEY, new SymbolType("int"));
				} else {
					try {

						field = c.getDeclaredField(n.getField());

					} catch (NoSuchFieldException fe) {

						try {
							field = c.getField(n.getField());

						} catch (NoSuchFieldException fe2) {
							// it is an inner class parsed as a field
							// declaration
							c = typeTable.loadClass(c.getName() + "$"
									+ n.getField());
							scopeType.setName(c.getName());
							arg.put(TYPE_KEY, scopeType);
							return;
						}

					}

					Map<String, SymbolType> typeMapping = new HashMap<String, SymbolType>();

					TypeVariable<?>[] typeParams = c.getTypeParameters();

					if (typeParams != null) {

						for (int i = 0; i < typeParams.length; i++) {
							if (scopeType != null
									&& scopeType.getParameterizedTypes() != null) {
								typeMapping.put(typeParams[i].getName(),
										scopeType.getParameterizedTypes()
												.get(i));
							} else {
								typeMapping.put(typeParams[i].getName(),
										new SymbolType("java.lang.Object"));
							}
						}

					}
					arg.put(TYPE_KEY,
							valueOf(field.getGenericType(), typeMapping));
				}
			}

		} catch (ClassNotFoundException e) {
			throw new WalkModException(e);

		} catch (Exception e) {
			throw new WalkModException(e);

		}
	}

	private SymbolType valueOf(Type type, Map<String, SymbolType> typeMapping)
			throws InvalidTypeException {

		SymbolType returnType = null;

		if (type instanceof Class<?>) {
			Class<?> aux = ((Class<?>) type);
			returnType = new SymbolType(aux.getName());
			if (aux.isArray()) {
				returnType.setArrayCount(1);
				returnType.setName(aux.getComponentType().getName());
			}

		} else if (type instanceof TypeVariable) {

			String variableName = ((TypeVariable<?>) type).getName();
			SymbolType aux = typeMapping.get(variableName);

			if (aux == null) {
				aux = new SymbolType(Object.class.getName());
				return aux;
			} else {
				return aux;
			}

		} else if (type instanceof ParameterizedType) {
			Class<?> auxClass = (Class<?>) ((ParameterizedType) type)
					.getRawType();

			Type[] types = ((ParameterizedType) type).getActualTypeArguments();

			returnType = new SymbolType(auxClass.getName());

			if (types != null) {
				List<SymbolType> params = new LinkedList<SymbolType>();
				returnType.setParameterizedTypes(params);
				for (Type t : types) {
					SymbolType param = typeMapping.get(t.toString());
					if (param != null) {
						params.add(param);
					} else {
						try {
							SymbolType st = valueOf(t, typeMapping);
							if (st != null) {
								params.add(st);
							}
						} catch (InvalidTypeException e) {
							LOG.warn("Unmappeable type " + t.toString());
						}
					}
				}
				if (params.isEmpty()) {
					returnType.setParameterizedTypes(null);
				}
			}

		} else if (type instanceof GenericArrayType) {
			// method.getReturnType();(
			returnType = new SymbolType(valueOf(
					((GenericArrayType) type).getGenericComponentType(),
					typeMapping).getName());

			returnType.setArrayCount(1);

		} else {
			throw new InvalidTypeException(type);
		}
		return returnType;

	}

	@Override
	public void visit(InstanceOfExpr n, VisitorContext arg) {
		arg.put(TYPE_KEY, new SymbolType("boolean"));
	}

	@Override
	public void visit(IntegerLiteralExpr n, VisitorContext arg) {
		arg.put(TYPE_KEY, new SymbolType("int"));
	}

	@Override
	public void visit(IntegerLiteralMinValueExpr n, VisitorContext arg) {
		arg.put(TYPE_KEY, new SymbolType("int"));
	}

	@Override
	public void visit(LongLiteralExpr n, VisitorContext arg) {
		arg.put(TYPE_KEY, new SymbolType("long"));
	}

	@Override
	public void visit(LongLiteralMinValueExpr n, VisitorContext arg) {
		arg.put(TYPE_KEY, new SymbolType("long"));
	}

	@Override
	public void visit(MethodCallExpr n, VisitorContext arg) {
		try {
			SymbolType scope;

			MethodCallExpr antRequiredMethod = (MethodCallExpr) arg
					.remove(ExpressionTypeAnalyzer.REQUIRED_METHOD);

			FieldAccessExpr faccess = (FieldAccessExpr) arg
					.remove(REQUIRED_ATTRIBUTE);

			if (n.getScope() != null) {

				arg.put(ExpressionTypeAnalyzer.REQUIRED_METHOD, n);

				n.getScope().accept(this, arg);

				arg.remove(ExpressionTypeAnalyzer.REQUIRED_METHOD);

				scope = (SymbolType) arg.remove(TYPE_KEY);

				LOG.debug("scope: (" + n.getScope().toString() + ")"
						+ scope.getName() + " method " + n.toString());

			} else {
				scope = symbolTable.getType("this");
				LOG.debug("scope (this): " + scope.getName() + " method "
						+ n.toString());
			}

			Class<?>[] typeArgs = null;
			if (n.getArgs() != null) {
				typeArgs = new Class[n.getArgs().size()];
				int i = 0;
				for (Expression e : n.getArgs()) {
					e.accept(this, arg);
					SymbolType argType = (SymbolType) arg.remove(TYPE_KEY);
					typeArgs[i] = typeTable.loadClass(argType);
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

			Map<String, SymbolType> typeMapping = new HashMap<String, SymbolType>();

			Class<?> clazz = typeTable.loadClass(scope);

			TypeVariable<?>[] typeParams = clazz.getTypeParameters();

			if (typeParams != null) {

				for (int i = 0; i < typeParams.length; i++) {
					if (scope != null && scope.getParameterizedTypes() != null) {
						typeMapping.put(typeParams[i].getName(), scope
								.getParameterizedTypes().get(i));
					} else {
						typeMapping.put(typeParams[i].getName(),
								new SymbolType("java.lang.Object"));
					}
				}

			}

			Method method = getMethod(scope, n.getName(), typeArgs,
					n.getArgs(), requiredMethod, requiredField, arg,
					typeMapping);

			Type[] types = method.getGenericParameterTypes();
			int pos = 0;
			boolean hasGenerics = false;

			for (Type type : types) {
				if (type instanceof ParameterizedType) {
					if (!hasGenerics) {
						LOG.debug(n + " is a call with generics ");
						hasGenerics = true;
					}
					Type aux = ((ParameterizedType) type).getRawType();
					if (aux instanceof Class) {
						if (((Class) aux).getName().equals("java.lang.Class")) {
							Type[] targs = ((ParameterizedType) type)
									.getActualTypeArguments();
							for (Type targ : targs) {
								String letter = targ.toString();
								if (!"?".equals(letter)
										&& !typeMapping.containsKey(letter)) {
									Expression e = (Expression) n.getArgs()
											.get(pos);
									String className = "";
									if (e instanceof ClassExpr) {
										className = ((ClassExpr) e).getType()
												.toString();
										Class<?> tclazz = typeTable
												.loadClass(className);
										typeMapping.put(letter, new SymbolType(
												tclazz.getName()));
									}
								}
							}
						}
					}
				}
				pos++;
			}

			SymbolType st = getMethodType(method, typeMapping);

			// Generics exception it.next() -> returns Object instead of the it
			// parametrized type
			if (st.getName().equals("java.lang.Object")
					&& scope.getParameterizedTypes() != null) {
				if (!scope.getParameterizedTypes().isEmpty()) {
					st.setName(scope.getParameterizedTypes().get(0).getName());
				}
			}

			arg.put(TYPE_KEY, st);

		} catch (ClassNotFoundException e) {
			throw new WalkModException(e);

		} catch (Exception e) {
			throw new WalkModException(e);
		}

	}

	public Map<String, SymbolType> getSymbolsOfGenericParameterTypes(
			Method method, List<Expression> argumentValues) {
		Map<String, SymbolType> symbols = new HashMap<String, SymbolType>();

		TypeVariable<?>[] typeVariables = method.getTypeParameters();

		if (typeVariables != null) {

			for (int i = 0; i < typeVariables.length; i++) {

				Type[] parameterTypes = method.getGenericParameterTypes();

				if (parameterTypes != null && argumentValues != null) {

					for (int j = 0; j < parameterTypes.length
							&& j < argumentValues.size(); j++) {

						if (parameterTypes[j] instanceof ParameterizedType) {

							String variableName = ((ParameterizedType) parameterTypes[j])
									.getActualTypeArguments()[0].toString();

							if (variableName.length() == 1) {
								if (argumentValues.get(j) instanceof ClassExpr) {
									Class<?> paramClass;
									try {
										paramClass = typeTable
												.loadClass(((ClassExpr) argumentValues
														.get(j)).getType());
									} catch (ClassNotFoundException e) {
										throw new WalkModException(
												"Invalid class into the generics resolution",
												e);
									}

									SymbolType auxType = new SymbolType();
									auxType.setName(paramClass.getName());

									symbols.put(variableName, auxType);
								}
							}
						}
					}
				}
			}
		}
		return symbols;
	}

	public Method findMethod(Method[] methods, String methodName,
			Class<?>[] typeArgs, List<Expression> argumentValues,
			Map<String, SymbolType> typeMapping, MethodCallExpr requiredMethod,
			FieldAccessExpr requiredField, VisitorContext arg)
			throws NoSuchMethodException, ClassNotFoundException,
			InvalidTypeException {

		Method result = null;
		if (methods == null) {
			throw new NoSuchMethodException("There are not methods to select");
		}

		for (int i = 0; i < methods.length && result == null; i++) {

			Method method = methods[i];
			if (!method.isBridge() && !method.isSynthetic()) {
				if (method.getName().equals(methodName)) {
					LOG.debug("Method " + method.getDeclaringClass().getName()
							+ "#" + methodName + ":"
							+ method.getReturnType().getName() + " found");
					Map<String, SymbolType> symbols = getSymbolsOfGenericParameterTypes(
							method, argumentValues);

					symbols.putAll(typeMapping);
					SymbolType returnType = getMethodType(method, symbols);

					if (isCompatible(method, methodName, typeArgs,
							typeTable.loadClass(returnType), requiredMethod,
							requiredField, arg)) {
						arg.put(TYPE_KEY, returnType);
						typeMapping.putAll(typeMapping);
						result = method;
						LOG.debug("compatible?  [OK] - result: "
								+ returnType.getName());
					} else {
						LOG.debug("compatible?  [NO]");
					}
				}
			}
		}

		return result;
	}

	public Method getMethod(Class<?> clazz, String methodName,
			Class<?>[] typeArgs, List<Expression> argumentValues,
			MethodCallExpr requiredMethod, FieldAccessExpr requiredField,
			VisitorContext arg, Map<String, SymbolType> typeMapping,
			boolean throwException) throws SecurityException,
			NoSuchMethodException, ClassNotFoundException, InvalidTypeException {

		LOG.debug("Looking for " + clazz.getName() + "#" + methodName);
		Method result = findMethod(clazz.getDeclaredMethods(), methodName,
				typeArgs, argumentValues, typeMapping, null, null, arg);

		if (result == null) {

			if (clazz.isMemberClass()) {

				result = getMethod(clazz.getDeclaringClass(), methodName,
						typeArgs, argumentValues, null, null, arg, typeMapping,
						false);

			} else if (clazz.isAnonymousClass()) {

				result = getMethod(clazz.getEnclosingClass(), methodName,
						typeArgs, argumentValues, null, null, arg, typeMapping,
						false);
			}
			if (result == null) {
				Class<?> superClass = clazz.getSuperclass();
				if (superClass != null) {
					result = getMethod(superClass, methodName, typeArgs,
							argumentValues, null, null, arg, typeMapping, false);
				}

				if (result == null) {
					Type[] types = clazz.getGenericInterfaces();
					if (types.length > 0) {

						for (int i = 0; i < types.length && result == null; i++) {

							Class<?> type = typeTable.loadClass(valueOf(
									types[i], typeMapping));

							result = getMethod(type, methodName, typeArgs,
									argumentValues, null, null, arg,
									typeMapping, false);
						}

					}
					if (result == null && clazz.isInterface()) {
						result = getMethod(Object.class, methodName, typeArgs,
								argumentValues, null, null, arg, typeMapping,
								false);
					}
				}
			}

		}
		if (result == null && throwException) {
			throw new NoSuchMethodException("The method " + clazz.getName()
					+ "#" + methodName + " cannot be found");
		}
		return result;
	}

	// TODO: Test
	public Method getMethod(SymbolType scope, // scope
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
			Map<String, SymbolType> typeMapping // mapping for Java Generics
												// applied
	// into the scope
	) throws ClassNotFoundException, NoSuchMethodException, SecurityException,
			InvalidTypeException {

		Class<?> clazz = typeTable.loadClass(scope);

		return getMethod(clazz, methodName, typeArgs, argumentValues,
				requiredMethod, requiredField, arg, typeMapping, true);

	}

	private SymbolType getMethodType(Method method,
			Map<String, SymbolType> typeMapping) throws ClassNotFoundException,
			InvalidTypeException {

		// mirem el tipus de resultat
		java.lang.reflect.Type type = method.getGenericReturnType();

		return valueOf(type, typeMapping);
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
				LOG.debug("The method [" + name
						+ "] is found with the same number of params: "
						+ numParams);
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
				int i = 0;
				for (i = 0; i < numParams && isCompatible; i++) {

					isCompatible = Types.isCompatible(typeArgs[i],
							methodParameterTypes[i]);
				}
				if (!isCompatible && numParams > 0) {
					LOG.debug("The parameter of " + (i - 1) + " is an "
							+ methodParameterTypes[i - 1].getName()
							+ ", but expected " + typeArgs[i - 1].getName());
				}

				if (isCompatible && lastVariableTypeArg != null) {
					int j = numParams;
					for (; j < typeArgs.length && isCompatible; j++) {
						isCompatible = Types.isCompatible(typeArgs[j],
								lastVariableTypeArg);

					}
					if (!isCompatible && numParams > 0) {
						LOG.debug("The parameter of " + (j - 1) + " is an "
								+ lastVariableTypeArg.getName()
								+ ", but expected " + typeArgs[j - 1].getName());
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
										SymbolType typeArg = (SymbolType) arg
												.remove(TYPE_KEY);
										if (!Types.isCompatible(
												typeTable.loadClass(typeArg),
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
							i = 0;
							for (i = 0; i < fields.length && !isCompatible; i++) {
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
		SymbolType type = symbolTable.getType(n.getName());

		if (type == null) {
			try {
				Class<?> clazz = typeTable.loadClass(n.getName());
				if (clazz != null) {
					String className = clazz.getName();
					type = new SymbolType();
					type.setName(className);
				}
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
		SymbolType type = new SymbolType(n.getName());
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
		arg.put(TYPE_KEY, new SymbolType("java.lang.String"));
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
