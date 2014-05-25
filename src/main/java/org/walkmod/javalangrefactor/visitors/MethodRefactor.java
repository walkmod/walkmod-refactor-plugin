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

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.log4j.Logger;
import org.walkmod.exceptions.InvalidTransformationRuleException;
import org.walkmod.exceptions.WalkModException;
import org.walkmod.javalang.ASTManager;
import org.walkmod.javalang.ParseException;
import org.walkmod.javalang.ast.CompilationUnit;
import org.walkmod.javalang.ast.ImportDeclaration;
import org.walkmod.javalang.ast.Node;
import org.walkmod.javalang.ast.TypeParameter;
import org.walkmod.javalang.ast.body.BodyDeclaration;
import org.walkmod.javalang.ast.body.ClassOrInterfaceDeclaration;
import org.walkmod.javalang.ast.body.ConstructorDeclaration;
import org.walkmod.javalang.ast.body.FieldDeclaration;
import org.walkmod.javalang.ast.body.MethodDeclaration;
import org.walkmod.javalang.ast.body.MultiTypeParameter;
import org.walkmod.javalang.ast.body.Parameter;
import org.walkmod.javalang.ast.body.VariableDeclarator;
import org.walkmod.javalang.ast.body.VariableDeclaratorId;
import org.walkmod.javalang.ast.expr.AnnotationExpr;
import org.walkmod.javalang.ast.expr.AssignExpr;
import org.walkmod.javalang.ast.expr.BinaryExpr;
import org.walkmod.javalang.ast.expr.Expression;
import org.walkmod.javalang.ast.expr.FieldAccessExpr;
import org.walkmod.javalang.ast.expr.MarkerAnnotationExpr;
import org.walkmod.javalang.ast.expr.MethodCallExpr;
import org.walkmod.javalang.ast.expr.NameExpr;
import org.walkmod.javalang.ast.expr.ObjectCreationExpr;
import org.walkmod.javalang.ast.expr.UnaryExpr;
import org.walkmod.javalang.ast.expr.VariableDeclarationExpr;
import org.walkmod.javalang.ast.stmt.BlockStmt;
import org.walkmod.javalang.ast.stmt.CatchClause;
import org.walkmod.javalang.ast.stmt.ExpressionStmt;
import org.walkmod.javalang.ast.stmt.IfStmt;
import org.walkmod.javalang.ast.stmt.Statement;
import org.walkmod.javalang.ast.stmt.SwitchEntryStmt;
import org.walkmod.javalang.ast.stmt.SwitchStmt;
import org.walkmod.javalang.ast.type.ClassOrInterfaceType;
import org.walkmod.javalang.ast.type.PrimitiveType;
import org.walkmod.javalang.ast.type.PrimitiveType.Primitive;
import org.walkmod.javalang.ast.type.ReferenceType;
import org.walkmod.javalang.ast.type.Type;
import org.walkmod.javalang.compiler.Symbol;
import org.walkmod.javalang.compiler.SymbolTable;
import org.walkmod.javalang.compiler.TypeTable;
import org.walkmod.javalang.visitors.VoidVisitorAdapter;
import org.walkmod.javalangrefactor.config.ConstantTransformationDictionary;
import org.walkmod.javalangrefactor.config.MethodHeaderDeclaration;
import org.walkmod.javalangrefactor.config.MethodHeaderDeclarationDictionary;
import org.walkmod.javalangrefactor.config.MethodRefactoringRule;
import org.walkmod.javalangrefactor.config.RefactoringRulesDictionary;
import org.walkmod.walkers.VisitorContext;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

public class MethodRefactor extends VoidVisitorAdapter<VisitorContext> {

	private RefactoringRulesDictionary refactoringRules;

	private Map<String, String> inputRules;

	private MethodHeaderDeclarationDictionary removedMethods = new MethodHeaderDeclarationDictionary();

	private MethodHeaderDeclarationDictionary createdMethods = new MethodHeaderDeclarationDictionary();

	private SymbolTable symbolTable;

	private TypeTable typeTable;

	private ExpressionTypeAnalyzer expressionTypeAnalyzer;

	private ExpressionRefactor exprRefactor;

	private String packageName = null;

	private static final MarkerAnnotationExpr OVERRIDE_ANNOTATION = new MarkerAnnotationExpr(
			new NameExpr("Override"));

	private ConstantTransformationDictionary constantDictionary;

	private static final String UPDATED_STATEMENT_KEY = "updated_statement_key";

	private static final String UPDATED_EXPRESSION_KEY = "updated_expression_key";

	private static final String APPLIED_REFACTORING_RULE_KEY = "applied_refactoring_rule_key";

	private static final String APPLIED_CONSTANT_TRANSFORMATION_TYPE = "applied_constant_transf_type";

	private int innerAnonymousClassCounter = 1;

	private VariableTypeRefactor variableTypeRefactor;

	private static Logger log = Logger.getLogger(MethodRefactor.class);

	private ClassLoader classLoader = null;

	private boolean setUp = false;

	public MethodRefactor() {
	}

	public void setClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	@Override
	public void visit(CompilationUnit unit, VisitorContext arg) {
		if (!setUp) {
			symbolTable = new SymbolTable();
			typeTable = new TypeTable(classLoader);
			this.refactoringRules = new RefactoringRulesDictionary(typeTable);
			createdMethods.setTypeTable(typeTable);
			removedMethods.setTypeTable(typeTable);
			exprRefactor = new ExpressionRefactor(symbolTable);
			constantDictionary = new ConstantTransformationDictionary();
			expressionTypeAnalyzer = new ExpressionTypeAnalyzer(typeTable,
					symbolTable);
			exprRefactor.setTypeTable(typeTable);
			exprRefactor.setExpressionTypeAnalyzer(expressionTypeAnalyzer);
			variableTypeRefactor = new VariableTypeRefactor();
			refactoringRules.putRules(inputRules);
			setUp = true;
		}

		innerAnonymousClassCounter = 1;

		if (unit.getPackage() != null) {
			this.packageName = unit.getPackage().getName().toString();
		} else {
			packageName = null;
		}
		typeTable.setCurrentPackage(packageName);
		super.visit(unit, arg);

		typeTable.clear();

	}

	public void visit(AssignExpr n, VisitorContext arg) {
		n.getTarget().accept(this, arg);

		// setting the possible I/O variable
		arg.put(ExpressionRefactor.CURRENT_ASSIGN_NODE_KEY, n.getTarget());
		n.getValue().accept(this, arg);

		// checking if an inner expression has an applied refactoring rule
		MethodRefactoringRule mrr = (MethodRefactoringRule) arg
				.remove(APPLIED_REFACTORING_RULE_KEY);

		// si pasamos de funcion a void
		if (mrr != null && mrr.isVoidResult()) {

			if (!arg.containsKey(UPDATED_STATEMENT_KEY)) {
				// changing the assignstmt for an expressionstmt
				ExpressionStmt stmt = new ExpressionStmt();

				if (arg.containsKey(UPDATED_EXPRESSION_KEY)) {
					stmt.setExpression(((Expression) arg
							.remove(UPDATED_EXPRESSION_KEY)));
				} else {
					stmt.setExpression(n.getValue());
				}
				arg.put(UPDATED_STATEMENT_KEY, stmt);
			}
		} else {
			if (arg.containsKey(UPDATED_EXPRESSION_KEY)) {
				n.setValue((Expression) arg.remove(UPDATED_EXPRESSION_KEY));
			}

		}

		// removing the paramter from the context
		arg.remove(ExpressionRefactor.CURRENT_ASSIGN_NODE_KEY);
	}

	public void visit(VariableDeclarator n, VisitorContext arg) {
		n.getId().accept(this, arg);

		if (n.getInit() != null) {

			arg.put(ExpressionRefactor.CURRENT_ASSIGN_NODE_KEY, new NameExpr(n
					.getId().getName()));

			arg.remove(ExpressionRefactor.CONSTRUCTOR_IO_VARIABLE);

			n.getInit().accept(this, arg);

			if (arg.containsKey(UPDATED_EXPRESSION_KEY)) {
				n.setInit((Expression) arg.remove(UPDATED_EXPRESSION_KEY));
			}

			// checking if exists in an inner expression an applied refactoring
			// rule
			MethodRefactoringRule mrr = (MethodRefactoringRule) arg
					.remove(APPLIED_REFACTORING_RULE_KEY);

			if (mrr != null) {

				// checking if the function becomes a void
				if (mrr.isVoidResult()) {

					// the constructor call is applied on the variable
					// declaration
					ExpressionStmt cexpr = (ExpressionStmt) arg
							.remove(ExpressionRefactor.CONSTRUCTOR_IO_VARIABLE);
					Expression initExpr = n.getInit();

					if (cexpr != null) {
						if (arg.containsKey(ExpressionRefactor.PREVIOUS_REQUIRED_STATEMENTS_KEY)) {
							@SuppressWarnings("unchecked")
							List<Statement> stmts = (List<Statement>) arg
									.remove(ExpressionRefactor.PREVIOUS_REQUIRED_STATEMENTS_KEY);

							org.walkmod.javalang.compiler.Type typeName = symbolTable
									.getType(n.getId().getName());
							ExpressionStmt constructorASTExpr = null;

							try {
								List<Statement> updatedStatements = new LinkedList<Statement>();

								Iterator<Statement> it = stmts.iterator();

								while (it.hasNext()) {
									Statement stmt = it.next();
									if (stmt.equals(cexpr)) {

										constructorASTExpr = (ExpressionStmt) ASTManager
												.parse(ExpressionStmt.class,
														typeName.getName()
																+ " "
																+ cexpr.toString());
										updatedStatements
												.add(constructorASTExpr);
									} else if (!it.hasNext()) {
										arg.put(UPDATED_STATEMENT_KEY, stmt);
									} else {
										updatedStatements.add(stmt);
									}
								}
								arg.put(ExpressionRefactor.PREVIOUS_REQUIRED_STATEMENTS_KEY,
										updatedStatements);

							} catch (ParseException e) {
								throw new WalkModException(e);
							}

						}

					} else if (!mrr.hasResultExpression()) {
						// hemos de actualizar la expresion de inicializacion.
						// Susbtituimos todo
						ExpressionStmt stmt = new ExpressionStmt();

						if (arg.containsKey(UPDATED_EXPRESSION_KEY)) {
							stmt.setExpression(((Expression) arg
									.remove(UPDATED_EXPRESSION_KEY)));
						} else {
							stmt.setExpression(initExpr);
						}
						arg.put(UPDATED_STATEMENT_KEY, stmt);
					}
				}
			}

			arg.remove(ExpressionRefactor.CURRENT_ASSIGN_NODE_KEY);
		}
	}

	@Override
	public void visit(ClassOrInterfaceDeclaration declaration,
			VisitorContext arg) {

		org.walkmod.javalang.compiler.Type lastScope = symbolTable
				.getType("this");

		symbolTable.pushScope();

		try {

			String className = getFullName(declaration);

			if (lastScope == null) {
				symbolTable
						.insertSymbol("this",
								new org.walkmod.javalang.compiler.Type(
										className), null);
				typeTable.setCurrentClassSimpleName(declaration.getName());
			} else {
				org.walkmod.javalang.compiler.Type type = new org.walkmod.javalang.compiler.Type(
						lastScope.getName() + "$" + declaration.getName());

				symbolTable.insertSymbol("this", type, null);

				String parentName = lastScope.getName().substring(
						lastScope.getName().lastIndexOf(".") + 1);

				typeTable.setCurrentClassSimpleName(parentName + "$"
						+ declaration.getName());

				className = typeTable.getCurrentClassSimpleName();
			}

			// adding all parent and accessible fields from superclasses
			Class<?> clazz = typeTable.getJavaClass(className);

			Class<?> parentClazz = clazz.getSuperclass();
			if (parentClazz != null) {
				symbolTable.insertSymbol(
						"super",
						new org.walkmod.javalang.compiler.Type(parentClazz
								.getName()), null);
			}

			while (parentClazz != null) {
				Field[] fields = parentClazz.getDeclaredFields();
				if (fields != null) {
					for (Field field : fields) {
						if (!Modifier.isPrivate(field.getModifiers())) {
							// if the symbol already exists, it has been
							// declared in a more closed superclass
							if (!symbolTable.containsSymbol(field.getName())) {

								symbolTable.insertSymbol(field.getName(),
										new org.walkmod.javalang.compiler.Type(
												field.getType().getName()),
										null);
							}
						}
					}
				}
				parentClazz = parentClazz.getSuperclass();
			}

			// adding all parent and accessible fields from interfaces
			Queue<java.lang.Class<?>> interfacesQueue = new ConcurrentLinkedQueue<java.lang.Class<?>>();

			Class<?>[] interfaces = clazz.getInterfaces();

			if (interfaces != null) {
				for (Class<?> inter : interfaces) {
					interfacesQueue.add(inter);
				}
			}

			for (Class<?> inter : interfacesQueue) {
				Field[] fields = inter.getDeclaredFields();
				if (fields != null) {
					for (Field field : fields) {
						if (!Modifier.isPrivate(field.getModifiers())) {
							// if the symbol already exists, it has been
							// declared in a more closed superclass
							if (!symbolTable.containsSymbol(field.getName())) {
								symbolTable.insertSymbol(field.getName(),
										new org.walkmod.javalang.compiler.Type(
												field.getType().getName()),
										null);
							}
						}
					}
				}
				Class<?> superClass = inter.getSuperclass();
				if (superClass != null) {
					if (!interfacesQueue.contains(superClass)) {
						interfacesQueue.add(superClass);
					}
				}
			}

			if (!createdMethods.isEmpty()) {

				Collection<MethodHeaderDeclaration> matchingMethods = createdMethods
						.getAllMethods(symbolTable.getType("this").getName());

				if (!matchingMethods.isEmpty()) {
					Class<?> c = typeTable.getJavaClass(symbolTable
							.getType("this"));
					Collection<MethodHeaderDeclaration> methodToadd = new LinkedList<MethodHeaderDeclaration>();
					for (MethodHeaderDeclaration mhm : matchingMethods) {

						try {
							c.getMethod(mhm.getName(), mhm.getArgTypeClasses());

						} catch (SecurityException e) {

						} catch (NoSuchMethodException e) {
							methodToadd.add(mhm);
						}

					}
					List<BodyDeclaration> members = declaration.getMembers();

					if (members == null) {
						members = new LinkedList<BodyDeclaration>();
					}

					for (MethodHeaderDeclaration mhm : methodToadd) {

						String body = "";

						if (mhm.getResult() instanceof PrimitiveType) {
							PrimitiveType pt = (PrimitiveType) mhm.getResult();

							if (pt.getType().equals(Primitive.Boolean)) {
								body = "return false;";
							} else if (pt.getType().equals(Primitive.Char)) {
								body = "return '';";
							} else {
								body = "return 0;";
							}
						} else if (mhm.getResult() instanceof ReferenceType) {
							body = "return null;";
						}
						body = "{" + body + "}";

						List<Parameter> parameters = new LinkedList<Parameter>();
						int i = 0;
						for (Parameter tp : mhm.getArgs()) {
							Parameter p = new Parameter();
							p.setId(new VariableDeclaratorId("p"
									+ Integer.toString(i)));
							p.setType(tp.getType());
							i++;
						}

						BlockStmt stmt = (BlockStmt) ASTManager.parse(
								BlockStmt.class, body);

						MethodDeclaration md = new MethodDeclaration(null,
								mhm.getModifiers(), null, null,
								mhm.getResult(), mhm.getName(), parameters, 0,
								mhm.getExceptions(), stmt);

						members.add(md);

					}
				}
			}

		} catch (ClassNotFoundException e) {

			new WalkModException(e);
		} catch (ParseException e) {
			new WalkModException(e);
		}

		// hacemos los accepts de toda la estructura
		if (declaration.getJavaDoc() != null) {
			declaration.getJavaDoc().accept(this, arg);
		}
		if (declaration.getAnnotations() != null) {
			for (AnnotationExpr a : declaration.getAnnotations()) {
				a.accept(this, arg);
			}
		}
		if (declaration.getTypeParameters() != null) {
			for (TypeParameter t : declaration.getTypeParameters()) {
				t.accept(this, arg);
			}
		}
		if (declaration.getExtends() != null) {
			for (ClassOrInterfaceType c : declaration.getExtends()) {
				c.accept(this, arg);
			}
		}

		if (declaration.getImplements() != null) {
			for (ClassOrInterfaceType c : declaration.getImplements()) {
				c.accept(this, arg);
			}
		}

		if (declaration.getMembers() != null) {
			// Field declarations are prioritary to insert them into the symbol
			// table
			for (BodyDeclaration member : declaration.getMembers()) {
				if (member instanceof FieldDeclaration) {
					member.accept(this, arg);
				}
			}

			for (BodyDeclaration member : declaration.getMembers()) {
				if (!(member instanceof FieldDeclaration)) {
					member.accept(this, arg);
				}
			}
		}

		symbolTable.popScope();
	}

	@Override
	public void visit(BinaryExpr n, VisitorContext arg) {

		Expression right = n.getRight();
		right.accept(this, arg);

		if (arg.containsKey(UPDATED_EXPRESSION_KEY)) {

			n.setRight((Expression) arg.remove(UPDATED_EXPRESSION_KEY));

		}

		Expression left = n.getLeft();
		left.accept(this, arg);
		if (arg.containsKey(UPDATED_EXPRESSION_KEY)) {
			n.setLeft((Expression) arg.remove(UPDATED_EXPRESSION_KEY));

		}

		if (n.getOperator().equals(BinaryExpr.Operator.equals)
				|| n.getOperator().equals(BinaryExpr.Operator.notEquals)) {

			if (n.getLeft() instanceof NameExpr) {
				// it is a variable
				if (arg.containsKey(APPLIED_CONSTANT_TRANSFORMATION_TYPE)) {

					ClassOrInterfaceType refType = (ClassOrInterfaceType) arg
							.remove(APPLIED_CONSTANT_TRANSFORMATION_TYPE);

					NameExpr var = (NameExpr) n.getLeft();
					if (symbolTable.containsSymbol(var.getName())) {
						Symbol s = symbolTable.getSymbol(var.getName());
						Node node = s.getInitNode();
						if (refType != null && node != null) {

							variableTypeRefactor.setType(refType);
							node.accept(variableTypeRefactor, arg);
						}
					}
				}

			}
		}

	}

	@Override
	public void visit(UnaryExpr n, VisitorContext arg) {
		Expression e = n.getExpr();
		e.accept(this, arg);

		if (arg.containsKey(UPDATED_EXPRESSION_KEY)) {

			n.setExpr((Expression) arg.get(UPDATED_EXPRESSION_KEY));

			arg.remove(UPDATED_EXPRESSION_KEY);
		}
	}

	@Override
	public void visit(MethodCallExpr n, VisitorContext arg) {

		arg.remove(APPLIED_CONSTANT_TRANSFORMATION_TYPE);
		arg.remove(APPLIED_REFACTORING_RULE_KEY);

		// the expression type must be calculted previously to any possible
		// change
		n.accept(expressionTypeAnalyzer, arg);

		org.walkmod.javalang.compiler.Type resultType = (org.walkmod.javalang.compiler.Type) arg
				.remove(ExpressionTypeAnalyzer.TYPE_KEY);

		try {
			org.walkmod.javalang.compiler.Type scopeType = null;
			// updating the scope
			if (n.getScope() == null) {
				scopeType = symbolTable.getType("this");
			} else {
				// store the previous context
				MethodCallExpr prev = (MethodCallExpr) arg
						.get(ExpressionTypeAnalyzer.REQUIRED_METHOD);

				// update context
				arg.put(ExpressionTypeAnalyzer.REQUIRED_METHOD, n);

				// Recursive call
				n.getScope().accept(expressionTypeAnalyzer, arg);

				// retrieving the scope type
				scopeType = (org.walkmod.javalang.compiler.Type) arg
						.remove(ExpressionTypeAnalyzer.TYPE_KEY);

				// removing scope context
				arg.remove(ExpressionTypeAnalyzer.REQUIRED_METHOD);

				if (prev != null) {
					// updating the previpus context
					arg.put(ExpressionTypeAnalyzer.REQUIRED_METHOD, prev);
				}

				/**
				 * creating a valid context. We cannot use assign variables as
				 * for inner operations as an IO parameter. That is beacuse it
				 * corresponds to the result of the most external operation call
				 */
				Object currentAssignNode = arg
						.remove(ExpressionRefactor.CURRENT_ASSIGN_NODE_KEY);

				// resolving the scope
				n.getScope().accept(this, arg);

				// updating the scope if some refactor is applied
				if (arg.containsKey(UPDATED_EXPRESSION_KEY)) {
					n.setScope((Expression) arg.remove(UPDATED_EXPRESSION_KEY));
				}
				// restoring context
				if (currentAssignNode != null) {
					arg.put(ExpressionRefactor.CURRENT_ASSIGN_NODE_KEY,
							currentAssignNode);

				}
				arg.remove(APPLIED_REFACTORING_RULE_KEY);
			}

			// searching the replacing method
			List<Expression> args = n.getArgs();
			String[] argStr = null;
			Class<?>[] argClazzes = null;
			List<Expression> refactoredArgs = new LinkedList<Expression>();

			if (args != null) {
				int i = 0;
				argStr = new String[args.size()];
				argClazzes = new Class[args.size()];

				Object currentAssignNode = arg
						.remove(ExpressionRefactor.CURRENT_ASSIGN_NODE_KEY);
				// guardem els tipus dels parametres
				for (Expression e : args) {

					e.accept(expressionTypeAnalyzer, arg);
					org.walkmod.javalang.compiler.Type aux = (org.walkmod.javalang.compiler.Type) arg
							.remove(ExpressionTypeAnalyzer.TYPE_KEY);
					if (aux != null) {
						argStr[i] = aux.getName();
						argClazzes[i] = typeTable.getJavaClass(argStr[i]);
					} else {
						// e is a nullLiteralExpr
						argStr[i] = null;
						argClazzes[i] = null;
					}
					// the systems applies the method refactoring in all its
					// args once the type is known

					e.accept(this, arg);

					if (arg.containsKey(UPDATED_EXPRESSION_KEY)) {
						refactoredArgs.add((Expression) arg
								.remove(UPDATED_EXPRESSION_KEY));
					} else {
						refactoredArgs.add(e);
					}
					arg.remove(APPLIED_REFACTORING_RULE_KEY);
					arg.remove(APPLIED_CONSTANT_TRANSFORMATION_TYPE);
					i++;

				}
				// restoring context
				if (currentAssignNode != null) {
					arg.put(ExpressionRefactor.CURRENT_ASSIGN_NODE_KEY,
							currentAssignNode);

				}
				args = refactoredArgs;
			}

			// treatment for deleted methods
			if (removedMethods.contains(scopeType.getName(), n.getName(),
					argStr)) {

				if (resultType.getName().equals("void")) {
					// its a void method invocation. we can drop the method call
					arg.put(UPDATED_EXPRESSION_KEY, null);

				} else {
					Class<?> returnT = typeTable.getJavaClass(resultType);
					TypeTable.getDefaultValue(returnT);
				}

			} else {

				MethodRefactoringRule mrr = refactoringRules
						.getRefactoringRule(scopeType.getName(), n.getName(),
								argStr);

				// does exists some refactoring rule?
				if (mrr != null) {

					// changing the method's name
					n.setName(mrr.getMethodName());

					Map<String, Expression> variableMap = new HashMap<String, Expression>();
					Map<String, Class<?>> variableTypes = new HashMap<String, Class<?>>();

					exprRefactor.setVariable(variableMap);
					exprRefactor.setVariableTypes(variableTypes);

					// updating args
					if (args != null && !args.isEmpty()) {

						List<Expression> argExpr = mrr.getExpressionTreeArgs();

						Iterator<Expression> it = args.iterator();
						int i = 0;
						for (String variable : mrr.getVariables()) {
							variableMap.put(variable, it.next());
							variableTypes.put(variable, argClazzes[i]);
							i++;
						}

						variableMap
								.put(mrr.getImplicitVaribale(), n.getScope());
						variableTypes.put(mrr.getImplicitVaribale(),
								typeTable.getJavaClass(scopeType));

						List<Expression> argExprRefactored = new LinkedList<Expression>();

						for (Expression e : argExpr) {

							// changing the argument expression once it its
							// refactored

							e.accept(exprRefactor, arg);

							if (e.getData() != null) {
								Expression aux = (Expression) e.getData();
								argExprRefactored.add(aux);

							} else {
								argExprRefactored.add(e);
							}

						}

						n.setArgs(argExprRefactored);

						arg.put(APPLIED_REFACTORING_RULE_KEY, mrr);

					}

					if (mrr.hasImplicitExpression()) {

						Expression implicitExpression = mrr
								.getImplicitTreeExpression();

						implicitExpression.accept(exprRefactor, arg);

						if (implicitExpression.getData() != null) {
							implicitExpression = (Expression) implicitExpression
									.getData();
						}

						n.setScope(implicitExpression);

					}
					Expression resultExpression = null;

					// changing functions to actions (void methods) with a new
					// I/O variable

					if (mrr.isVoidResult()) {

						if (!resultType.equals("void")) {

							// changing the assignstnt to an expressionstmt
							if (!mrr.hasResultExpression()) {
								ExpressionStmt stmt = new ExpressionStmt();
								stmt.setExpression(n);

								@SuppressWarnings("unchecked")
								Collection<Statement> stmts = (Collection<Statement>) arg
										.remove(ExpressionRefactor.PREVIOUS_REQUIRED_STATEMENTS_KEY);
								if (stmts == null) {
									stmts = new LinkedList<Statement>();
								}
								stmts.add(stmt);
								arg.put(ExpressionRefactor.PREVIOUS_REQUIRED_STATEMENTS_KEY,
										stmts);

								if (arg.containsKey(ExpressionRefactor.CURRENT_ASSIGN_NODE_KEY)) {

									arg.put(UPDATED_EXPRESSION_KEY,
											arg.remove(ExpressionRefactor.CURRENT_ASSIGN_NODE_KEY));

								} else if (arg
										.containsKey(ExpressionRefactor.CREATED_IO_VARIABLE)) {

									NameExpr variable = (NameExpr) arg
											.remove(ExpressionRefactor.CREATED_IO_VARIABLE);
									arg.put(UPDATED_EXPRESSION_KEY, variable);
									// the new variable has the method type in
									// the
									// symbol table
									symbolTable.insertSymbol(
											variable.getName(), resultType,
											null);
								}
							} else if (mrr.hasResultExpression()) {

								@SuppressWarnings("unchecked")
								Collection<Statement> reqStmts = (Collection<Statement>) arg
										.get(ExpressionRefactor.PREVIOUS_REQUIRED_STATEMENTS_KEY);

								if (reqStmts == null) {
									reqStmts = new LinkedList<Statement>();
									arg.put(ExpressionRefactor.PREVIOUS_REQUIRED_STATEMENTS_KEY,
											reqStmts);
								}

								ExpressionStmt stmt = new ExpressionStmt();
								stmt.setExpression(n);
								Symbol variableName = null;

								if (arg.containsKey(ExpressionRefactor.CURRENT_ASSIGN_NODE_KEY)) {

									Expression name = (Expression) arg
											.remove(ExpressionRefactor.CURRENT_ASSIGN_NODE_KEY);
									// name could be a FieldAccessExpression or
									// a NameExpr

									if (name instanceof NameExpr) {
										variableName = symbolTable
												.getSymbol(name.toString());
									}

								}
								boolean requiresNewVariable = (variableName == null);

								if (variableName == null) {

									variableName = symbolTable
											.createSymbol(resultType);

									// A new variable declaration is inserted
									// before
									// the result expression
									VariableDeclarationExpr vde = new VariableDeclarationExpr();

									vde.setType((Type) ASTManager.parse(
											ClassOrInterfaceType.class,
											resultType.getName()));

									List<VariableDeclarator> vars = new LinkedList<VariableDeclarator>();

									// the variable is initialized with the
									// current
									// method call expression once it is
									// refactored
									VariableDeclarator vd = new VariableDeclarator(
											new VariableDeclaratorId(
													variableName.getName()
															.getName()), n);
									vars.add(vd);
									vde.setVars(vars);

									stmt.setExpression(vde);
									reqStmts.add(stmt);

									// the method call expression in the code
									// will
									// be replaced for the result expression
									arg.put(UPDATED_EXPRESSION_KEY,
											variableName.getName());
								} else {
									arg.put(UPDATED_EXPRESSION_KEY, n);
								}

								// resultExpression is refactored changing
								// 'result' for the variable name instead the
								// method call
								resultExpression = mrr
										.getResultTreeExpression();

								Map<String, Expression> map = exprRefactor
										.getVariable();

								map.put(mrr.getResultVariable(),
										variableName.getName());

								exprRefactor.setVariable(map);

								resultExpression.accept(exprRefactor, arg);

								stmt = new ExpressionStmt();
								stmt.setExpression(resultExpression);

								if (requiresNewVariable) {
									reqStmts.add(stmt);

									// if requires new variable is because is a
									// method call argument.
									// The updated expression is the new
									// variable
									// result expression must be inserted before
									// passing the new variable
								} else {
									// the result statement must be a forward
									// expression of the variable initialization
									@SuppressWarnings("unchecked")
									Collection<Statement> forwardStmts = (Collection<Statement>) arg
											.get(ExpressionRefactor.FORWARD_REQUIRED_STATEMENTS_KEY);

									if (forwardStmts == null) {
										forwardStmts = new LinkedList<Statement>();
										arg.put(ExpressionRefactor.FORWARD_REQUIRED_STATEMENTS_KEY,
												forwardStmts);
									}

									forwardStmts.add(stmt);
								}

							}

						}
					} else if (mrr.hasResultExpression()) {

						resultExpression = mrr.getResultTreeExpression();

						Map<String, Expression> map = exprRefactor
								.getVariable();
						map.put(mrr.getResultVariable(), n);
						exprRefactor.setVariable(map);
						resultExpression.accept(exprRefactor, arg);
						// dejamos la nueva expresion en data para actualizar la
						// expresion que la tenga como hija
						if (resultExpression.getData() != null) {
							resultExpression = (Expression) resultExpression
									.getData();
						}
						if (!exprRefactor.getRefactoredVariables().contains(
								mrr.getResultVariable())) {

							// it is necessary to apply the method refactor as
							// an statement and the result expression

							@SuppressWarnings("unchecked")
							Collection<Statement> reqStmts = (Collection<Statement>) arg
									.get(ExpressionRefactor.PREVIOUS_REQUIRED_STATEMENTS_KEY);

							if (reqStmts == null) {
								reqStmts = new LinkedList<Statement>();
								arg.put(ExpressionRefactor.PREVIOUS_REQUIRED_STATEMENTS_KEY,
										reqStmts);
							}
							ExpressionStmt stmt = new ExpressionStmt(n);
							reqStmts.add(stmt);
						}
						arg.put(UPDATED_EXPRESSION_KEY, resultExpression);
					}

				}

				if (resultType != null) {
					scopeType = resultType;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

	}

	@Override
	public void visit(FieldAccessExpr n, VisitorContext arg) {

		arg.remove(APPLIED_CONSTANT_TRANSFORMATION_TYPE);
		// has scope
		if (n.getScope() != null) {

			n.getScope().accept(expressionTypeAnalyzer, arg);
			String aux = ((org.walkmod.javalang.compiler.Type) arg
					.remove(ExpressionTypeAnalyzer.TYPE_KEY)).getName();

			aux = aux + "." + n.getField();
			Collection<Expression> targetExpression = constantDictionary
					.get(aux);

			if (!targetExpression.isEmpty()) {

				Expression appliedExpression = targetExpression.iterator()
						.next();

				arg.put(UPDATED_EXPRESSION_KEY, appliedExpression);

				if (constantDictionary.hasEnumTransformation(aux)) {

					if (appliedExpression instanceof FieldAccessExpr) {

						try {
							ClassOrInterfaceType type = (ClassOrInterfaceType) ASTManager
									.parse(ClassOrInterfaceType.class,
											((FieldAccessExpr) appliedExpression)
													.getScope().toString());

							arg.put(APPLIED_CONSTANT_TRANSFORMATION_TYPE, type);

						} catch (ParseException e) {
							throw new WalkModException(e);
						}

					}
				}

			} else {

				n.getScope().accept(this, arg);
				if (arg.containsKey(UPDATED_EXPRESSION_KEY)) {
					n.setScope((Expression) arg.remove(UPDATED_EXPRESSION_KEY));
				}
			}
		}

	}

	public void visit(SwitchStmt n, VisitorContext arg) {
		n.getSelector().accept(this, arg);
		ClassOrInterfaceType refType = null;
		if (n.getEntries() != null) {
			for (SwitchEntryStmt e : n.getEntries()) {
				arg.remove(APPLIED_CONSTANT_TRANSFORMATION_TYPE);
				e.accept(this, arg);
				if (arg.containsKey(APPLIED_CONSTANT_TRANSFORMATION_TYPE)) {
					refType = (ClassOrInterfaceType) arg
							.remove(APPLIED_CONSTANT_TRANSFORMATION_TYPE);
				}
			}
		}

		if (n.getSelector() instanceof NameExpr) {
			// it is a variable
			NameExpr var = (NameExpr) n.getSelector();
			if (symbolTable.containsSymbol(var.getName())) {
				Symbol s = symbolTable.getSymbol(var.getName());
				Node node = s.getInitNode();
				if (refType != null && node != null) {

					variableTypeRefactor.setType(refType);
					node.accept(variableTypeRefactor, arg);
				}
			}

		}
	}

	public void visit(SwitchEntryStmt n, VisitorContext arg) {

		if (n.getLabel() != null) {
			n.getLabel().accept(this, arg);
			if (arg.containsKey(UPDATED_EXPRESSION_KEY)) {
				n.setLabel((Expression) arg.remove(UPDATED_EXPRESSION_KEY));
			}
		}

		ClassOrInterfaceType refactoredLabel = (ClassOrInterfaceType) arg
				.remove(APPLIED_CONSTANT_TRANSFORMATION_TYPE);
		if (n.getStmts() != null) {

			symbolTable.pushScope();

			List<Statement> stmts = new LinkedList<Statement>();

			for (Statement s : n.getStmts()) {

				s.accept(this, arg);

				@SuppressWarnings("unchecked")
				Collection<Statement> reqStmts = (Collection<Statement>) arg
						.get(ExpressionRefactor.PREVIOUS_REQUIRED_STATEMENTS_KEY);
				if (reqStmts != null) {
					for (Statement ns : reqStmts) {
						stmts.add(ns);
					}
					reqStmts.clear();
				}

				if (!arg.containsKey(UPDATED_STATEMENT_KEY)) {
					// is not an empty statement
					stmts.add(s);
				} else {
					if (arg.get(UPDATED_STATEMENT_KEY) != null) {
						stmts.add((Statement) arg.remove(UPDATED_STATEMENT_KEY));
					} else {
						log.debug("The method " + s.toString() + " is removed");
						arg.remove(UPDATED_STATEMENT_KEY);
					}
				}

				@SuppressWarnings("unchecked")
				Collection<Statement> forStmts = (Collection<Statement>) arg
						.remove(ExpressionRefactor.FORWARD_REQUIRED_STATEMENTS_KEY);
				if (forStmts != null) {
					for (Statement ns : forStmts) {
						stmts.add(ns);
					}
					forStmts.clear();
				}
			}
			n.setStmts(stmts);
			if (stmts.isEmpty()) {
				arg.put(UPDATED_STATEMENT_KEY, null);
			}
			symbolTable.popScope();
		}

		if (refactoredLabel != null) {
			arg.put(APPLIED_CONSTANT_TRANSFORMATION_TYPE, refactoredLabel);
		}
	}

	public void setRefactoringConfigFile(String refactoringConfigFile)
			throws Exception {
		File file = new File(refactoringConfigFile);

		if (file.exists()) {

			if (file.canRead()) {

				String text = new Scanner(file).useDelimiter("\\A").next();

				JSONObject o = JSON.parseObject(text);

				Map<String, String> aux = new HashMap<String, String>();

				Set<Map.Entry<String, Object>> entries = o.entrySet();

				Iterator<Map.Entry<String, Object>> it = entries.iterator();

				while (it.hasNext()) {
					Map.Entry<String, Object> entry = it.next();
					aux.put(entry.getKey(), entry.getValue().toString());
					it.remove();
				}
				setRefactoringRules(aux);
			} else {
				log.error("The refactoring config file ["
						+ refactoringConfigFile + "] cannot be read");
			}
		} else {
			log.error("The refactoring config file [" + refactoringConfigFile
					+ "] does not exist");
		}
	}

	public void setRefactoringRules(Map<String, String> inputRules)
			throws InvalidTransformationRuleException {
		this.inputRules = inputRules;
		
	}

	public Map<String, String> getRefactoringRules() {
		return inputRules;
	}

	public void setRemovedMethods(Collection<String> deletedMethods)
			throws ParseException {

		for (String deletedMethod : deletedMethods) {
			int index = deletedMethod.indexOf("#");
			String methodPart = deletedMethod.substring(index + 1);
			methodPart = "public void " + methodPart;
			String finalName = deletedMethod.substring(0, index) + "#"
					+ methodPart;

			removedMethods.add(finalName);
		}

	}

	public void setCreatedMethods(Collection<String> createdMethods)
			throws ParseException {

		for (String createdMethod : createdMethods) {
			int indexScope = createdMethod.indexOf("#");
			String scopeStr = createdMethod.substring(0, indexScope);
			int indexType = scopeStr.lastIndexOf(" ");
			String modifiers = scopeStr.substring(0, indexType);
			scopeStr = scopeStr.substring(indexType + 1);
			String finalName = scopeStr + "#" + modifiers + " "
					+ createdMethod.substring(indexScope + 1);
			this.createdMethods.add(finalName);
		}
	}
	
	public void setConstantsConfigFile(String constantsConfigFile)
			throws Exception {
		File file = new File(constantsConfigFile);

		if (file.exists()) {

			if (file.canRead()) {

				String text = new Scanner(file).useDelimiter("\\A").next();

				JSONObject o = JSON.parseObject(text);

				Map<String, String> aux = new HashMap<String, String>();

				Set<Map.Entry<String, Object>> entries = o.entrySet();

				Iterator<Map.Entry<String, Object>> it = entries.iterator();

				while (it.hasNext()) {
					Map.Entry<String, Object> entry = it.next();
					aux.put(entry.getKey(), entry.getValue().toString());
					it.remove();
				}
				setConstantTransformations(aux);
			} else {
				log.error("The constants config file ["
						+ constantsConfigFile + "] cannot be read");
			}
		} else {
			log.error("The constants config file [" + constantsConfigFile
					+ "] does not exist");
		}
	}

	public void setConstantTransformations(Map<String, String> transformations) {
		constantDictionary.addAll(transformations);
	}

	@Override
	public void visit(VariableDeclarationExpr n, VisitorContext arg) {
		Type type = n.getType();

		org.walkmod.javalang.compiler.Type resolvedType = typeTable
				.valueOf(type);

		for (VariableDeclarator var : n.getVars()) {

			symbolTable.insertSymbol(var.getId().getName(), resolvedType, n);
		}
		super.visit(n, arg);

		ClassOrInterfaceType constantRef = (ClassOrInterfaceType) arg
				.remove(APPLIED_CONSTANT_TRANSFORMATION_TYPE);

		if (constantRef != null) {
			variableTypeRefactor.setType(constantRef);
			n.accept(variableTypeRefactor, arg);
		}

	}

	@Override
	public void visit(MethodDeclaration n, VisitorContext arg) {
		org.walkmod.javalang.compiler.Type thisType = symbolTable
				.getType("this");
		String[] args = new String[0];
		if (n.getParameters() != null) {
			args = new String[n.getParameters().size()];
		}

		List<Parameter> parameters = n.getParameters();
		if (parameters != null) {
			int i = 0;
			for (Parameter parameter : parameters) {

				Type type = parameter.getType();
				try {
					Class<?> resolvedClass = typeTable.getJavaClass(type);
					args[i] = resolvedClass.getName();

				} catch (ClassNotFoundException e) {
					throw new WalkModException(e);
				}

				i++;
			}
		}

		// if a deteled method contains the overwrite annotation, the annotation
		// is removed
		List<AnnotationExpr> annotations = n.getAnnotations();

		if (annotations != null) {
			List<AnnotationExpr> newAnnotations = new LinkedList<AnnotationExpr>();

			for (AnnotationExpr ae : annotations) {
				if (!ae.getName().getName()
						.equals(OVERRIDE_ANNOTATION.getName().getName())) {
					newAnnotations.add(ae);
				}
			}
			n.setAnnotations(newAnnotations);

		}

		try {

			// changing the method header
			MethodRefactoringRule mrr = refactoringRules.getRefactoringRule(
					thisType.getName(), n.getName(), args);
			if (mrr != null) {
				n.setName(mrr.getMethodName());
				java.lang.reflect.Type resultType = mrr.getResultType();
				Type type;
				if (resultType != null) {
					if (resultType instanceof Class) {
						type = new ClassOrInterfaceType(
								((Class<?>) resultType).getName());
					} else {
						throw new WalkModException(
								"There is a method refactoring rule without an instantiable result type. This type is : "
										+ resultType.toString());

					}
					n.setType(type);
				}
			}
		} catch (ClassNotFoundException e) {
			throw new WalkModException(e);
		} catch (SecurityException e) {
			throw new WalkModException(e);
		}

		symbolTable.pushScope();
		if (n.getParameters() != null) {
			for (Parameter p : n.getParameters()) {
				Type type = p.getType();

				symbolTable.insertSymbol(p.getId().getName(),
						typeTable.valueOf(type), p);

			}
		}
		super.visit(n, arg);
		symbolTable.popScope();

	}

	@Override
	public void visit(ImportDeclaration n, VisitorContext arg) {
		typeTable.add(n); // para saber los imports del class or interface
	}

	@Override
	public void visit(FieldDeclaration n, VisitorContext arg) {
		Type type = n.getType();

		org.walkmod.javalang.compiler.Type resolvedType = typeTable
				.valueOf(type);

		for (VariableDeclarator var : n.getVariables()) {

			// example: int arg[] = new int[3]. Array count is setted in arg
			if (resolvedType.getArrayCount() == 0) {
				resolvedType.setArrayCount(var.getId().getArrayCount());
			}

			symbolTable.insertSymbol(var.getId().getName(), resolvedType, n);
		}

	}

	/**
	 * Este metodo lo definimos para iniciar un nuevo contexto de variables
	 */
	@Override
	public void visit(BlockStmt n, VisitorContext arg) {
		symbolTable.pushScope();
		if (n.getStmts() != null) {
			List<Statement> stmts = new LinkedList<Statement>();
			for (Statement s : n.getStmts()) {

				s.accept(this, arg);

				@SuppressWarnings("unchecked")
				Collection<Statement> reqStmts = (Collection<Statement>) arg
						.get(ExpressionRefactor.PREVIOUS_REQUIRED_STATEMENTS_KEY);
				if (reqStmts != null) {
					for (Statement ns : reqStmts) {
						stmts.add(ns);
					}
					reqStmts.clear();
				}

				if (!arg.containsKey(UPDATED_STATEMENT_KEY)) {
					// is not an empty statement
					stmts.add(s);
				} else {
					if (arg.get(UPDATED_STATEMENT_KEY) != null) {
						stmts.add((Statement) arg.remove(UPDATED_STATEMENT_KEY));
					} else {
						log.debug("The method " + s.toString() + " is removed");
						arg.remove(UPDATED_STATEMENT_KEY);
					}
				}

				@SuppressWarnings("unchecked")
				Collection<Statement> forStmts = (Collection<Statement>) arg
						.remove(ExpressionRefactor.FORWARD_REQUIRED_STATEMENTS_KEY);
				if (forStmts != null) {
					for (Statement ns : forStmts) {
						stmts.add(ns);
					}
					forStmts.clear();
				}
			}
			n.setStmts(stmts);
			if (stmts.isEmpty()) {
				arg.put(UPDATED_STATEMENT_KEY, null);
			}
		}
		symbolTable.popScope();
	}

	@Override
	public void visit(IfStmt n, VisitorContext arg) {
		n.getCondition().accept(this, arg);

		if (arg.containsKey(UPDATED_EXPRESSION_KEY)) {
			n.setCondition((Expression) arg.remove(UPDATED_EXPRESSION_KEY));
		}

		n.getThenStmt().accept(this, arg);

		if (arg.containsKey(UPDATED_STATEMENT_KEY)
				&& arg.get(UPDATED_STATEMENT_KEY) == null) {
			// when empty then statement is setted because a method is removed

			UnaryExpr notExpr = new UnaryExpr(n.getCondition(),
					UnaryExpr.Operator.not);
			n.setCondition(notExpr);
			n.setThenStmt(n.getElseStmt());
			n.setElseStmt(null);

			if (n.getThenStmt() != null) {
				n.getThenStmt().accept(this, arg);
			} else {
				arg.put(UPDATED_STATEMENT_KEY, null);
			}

		} else {
			if (n.getElseStmt() != null) {
				n.getElseStmt().accept(this, arg);
			}
		}
	}

	public String getFullName(ClassOrInterfaceDeclaration declaration) {
		if (packageName == null) {
			return declaration.getName();
		}
		return this.packageName + "." + declaration.getName();
	}

	@Override
	public void visit(ObjectCreationExpr n, VisitorContext arg) {

		arg.remove(APPLIED_REFACTORING_RULE_KEY);
		arg.remove(APPLIED_CONSTANT_TRANSFORMATION_TYPE);

		org.walkmod.javalang.compiler.Type objectScope = null;

		if (n.getScope() != null) {

			n.getScope().accept(expressionTypeAnalyzer, arg);

			objectScope = (org.walkmod.javalang.compiler.Type) arg
					.remove(ExpressionTypeAnalyzer.TYPE_KEY);

			n.getScope().accept(this, arg);

			if (arg.containsKey(UPDATED_EXPRESSION_KEY)) {
				n.setScope((Expression) arg.remove(UPDATED_EXPRESSION_KEY));
			}
		} else {
			objectScope = new org.walkmod.javalang.compiler.Type(
					typeTable.getFullName(n.getType()));
		}
		if (n.getTypeArgs() != null) {
			for (Type t : n.getTypeArgs()) {
				t.accept(this, arg);
			}
		}
		n.getType().accept(this, arg);

		String[] argStr = null;

		List<Expression> args = n.getArgs();
		List<Expression> refactoredArgs = new LinkedList<Expression>();
		if (args != null) {

			Object currentAssignNode = arg
					.remove(ExpressionRefactor.CURRENT_ASSIGN_NODE_KEY);

			argStr = new String[n.getArgs().size()];
			int i = 0;
			for (Expression e : args) {
				// Calculating the type without code changes
				e.accept(expressionTypeAnalyzer, arg);
				org.walkmod.javalang.compiler.Type eType = (org.walkmod.javalang.compiler.Type) arg
						.remove(ExpressionTypeAnalyzer.TYPE_KEY);

				if (eType != null) {
					argStr[i] = eType.getName();
				} else {
					// null literal expression
					argStr[i] = null;
				}

				// Transforming the expression
				e.accept(this, arg);
				if (arg.containsKey(UPDATED_EXPRESSION_KEY)) {
					refactoredArgs.add((Expression) arg
							.remove(UPDATED_EXPRESSION_KEY));
				} else {
					refactoredArgs.add(e);
				}
				arg.remove(APPLIED_REFACTORING_RULE_KEY);
				arg.remove(APPLIED_CONSTANT_TRANSFORMATION_TYPE);
				i++;
			}
			if (currentAssignNode != null) {
				arg.put(ExpressionRefactor.CURRENT_ASSIGN_NODE_KEY,
						currentAssignNode);
			}
			n.setArgs(refactoredArgs);
		}
		try {
			MethodRefactoringRule mrr = refactoringRules.getRefactoringRule(
					objectScope.getName(), n.getType().getName(), argStr);

			if (mrr != null) {

				// changing the constructor's name
				String newConstructorName = mrr.getMethodName();
				if (mrr.getScope() != null) {
					newConstructorName = mrr.getScope() + "."
							+ newConstructorName;
				}

				Map<String, Expression> variableMap = new HashMap<String, Expression>();

				exprRefactor.setVariable(variableMap);

				// actualizamos los args
				if (args != null && !args.isEmpty()) {

					List<Expression> argExpr = mrr.getExpressionTreeArgs();

					Iterator<Expression> it = args.iterator();

					for (String variable : mrr.getVariables()) {
						variableMap.put(variable, it.next());
					}

					variableMap.put(mrr.getImplicitVaribale(), n.getScope());

					List<Expression> argExprRefactored = new LinkedList<Expression>();

					for (Expression e : argExpr) {

						// replacing the argument expression
						e.accept(exprRefactor, arg);

						if (e.getData() != null) {
							argExprRefactored.add((Expression) e.getData());

						} else {
							argExprRefactored.add(e);
						}

					}

					n.setArgs(argExprRefactored);

				}

				if (mrr.isVoidResult()) {
					// 1. object creation call must be outside the expression
					// when it is not assigned into a variable

					NameExpr ne = (NameExpr) arg
							.remove(ExpressionRefactor.CURRENT_ASSIGN_NODE_KEY);
					/**
					 * IMPORTANT: Object creation call expr does not contains
					 * scope, but it may be the scope of another expression.
					 */
					// its is not called for a variable initialization. e.g as
					// an operation param
					if (ne == null) {

						// a new variable is created

						@SuppressWarnings("unchecked")
						Collection<Statement> stmts = (Collection<Statement>) arg
								.remove(ExpressionRefactor.PREVIOUS_REQUIRED_STATEMENTS_KEY);
						if (stmts == null) {
							stmts = new LinkedList<Statement>();
						}

						// constructor added

						Symbol variableName = symbolTable
								.createSymbol(typeTable.valueOf(n.getType()));

						ExpressionStmt stmt = new ExpressionStmt();

						VariableDeclarationExpr vde = new VariableDeclarationExpr();
						vde.setType(n.getType());
						List<VariableDeclarator> vars = new LinkedList<VariableDeclarator>();
						VariableDeclarator vd = new VariableDeclarator(
								new VariableDeclaratorId(variableName.getName()
										.getName()), n);
						vars.add(vd);
						vde.setVars(vars);

						stmt.setExpression(vde);
						stmts.add(stmt);

						Expression resultExpr = mrr.getResultTreeExpression();

						variableMap.put(mrr.getResultVariable(),
								variableName.getName());
						exprRefactor.setVariable(variableMap);

						resultExpr.accept(exprRefactor, arg);

						// updating result expression once the refactoring
						// process has finished
						if (resultExpr.getData() != null) {
							resultExpr = (Expression) resultExpr.getData();
						}

						// result expression added
						stmt = new ExpressionStmt();
						stmt.setExpression(resultExpr);
						stmts.add(stmt);

						arg.put(ExpressionRefactor.PREVIOUS_REQUIRED_STATEMENTS_KEY,
								stmts);

						// the new declared variable will be referenced from the
						// root node.
						arg.put(UPDATED_EXPRESSION_KEY, variableName.getName());

						// the new variable has the method type in the
						// symbol table
						symbolTable.insertSymbol(variableName.getName()
								.getName(), typeTable.valueOf(n.getType()), vd);

					} else {
						// the expression is the initialization of a variable
						@SuppressWarnings("unchecked")
						Collection<Statement> stmts = (Collection<Statement>) arg
								.remove(ExpressionRefactor.FORWARD_REQUIRED_STATEMENTS_KEY);
						if (stmts == null) {
							stmts = new LinkedList<Statement>();
						}
						ExpressionStmt stmt = new ExpressionStmt();

						Expression resultExpr = mrr.getResultTreeExpression();

						variableMap.put(mrr.getResultVariable(), ne);
						exprRefactor.setVariable(variableMap);

						resultExpr.accept(exprRefactor, arg);

						// dejamos la nueva expresion en data para actualizar la
						// expresion que la tenga como hija
						if (resultExpr.getData() != null) {
							resultExpr = (Expression) resultExpr.getData();
						}
						stmt.setExpression(resultExpr);
						stmts.add(stmt);

						arg.put(UPDATED_EXPRESSION_KEY, ne);
						arg.put(ExpressionRefactor.FORWARD_REQUIRED_STATEMENTS_KEY,
								stmts);

					}

					arg.put(APPLIED_REFACTORING_RULE_KEY, mrr);

				}

				if (mrr.hasImplicitExpression()) {

					Expression implicitExpression = mrr
							.getImplicitTreeExpression();

					implicitExpression.accept(exprRefactor, arg);

					if (implicitExpression.getData() != null) {
						implicitExpression = (Expression) implicitExpression
								.getData();
					}

					MethodCallExpr aux = new MethodCallExpr();
					aux.setScope(implicitExpression);
					aux.setTypeArgs(n.getTypeArgs());
					aux.setName(mrr.getMethodName());
					aux.setArgs(n.getArgs());
					arg.put(UPDATED_EXPRESSION_KEY, aux);

				}

				n.getType().setName(newConstructorName);
			}

		} catch (Exception e) {
			throw new WalkModException(e);
		}

		if (n.getAnonymousClassBody() != null) {
			symbolTable.pushScope();

			org.walkmod.javalang.compiler.Type anonymousType = new org.walkmod.javalang.compiler.Type();

			Class<?> anonymousClazz = null;
			Class<?> clazz = null;

			try {

				clazz = typeTable.getJavaClass(symbolTable.getType("this"));

				/**
				 * Anonymous name clases <fully qualified top level class name>(
				 * ( $<enclosing member simple name> ) | ($N<)*$<member class>
				 */
				anonymousType.setName(clazz.getName() + "$"
						+ innerAnonymousClassCounter);

				symbolTable.insertSymbol("this", anonymousType, null);

				// TODO: put the type variables

				org.walkmod.javalang.compiler.Type parentScope = new org.walkmod.javalang.compiler.Type();

				anonymousClazz = typeTable.getJavaClass(anonymousType);

				Class<?> superClazz = anonymousClazz.getSuperclass();

				if (superClazz != null) {
					parentScope.setName(superClazz.getName());
					// TODO: put the type variables
					symbolTable.insertSymbol("super", parentScope, null);
				}

			} catch (ClassNotFoundException e) {
				throw new WalkModException(e);
			}

			String previousType = typeTable.getCurrentClassSimpleName();

			String previousPackage = typeTable.getCurrentPackage();

			typeTable.setCurrentClassSimpleName(clazz.getSimpleName() + "$"
					+ innerAnonymousClassCounter);

			typeTable.setCurrentPackage(clazz.getPackage().getName());

			for (BodyDeclaration member : n.getAnonymousClassBody()) {
				member.accept(this, arg);
			}

			symbolTable.popScope();
			typeTable.setCurrentClassSimpleName(previousType);
			typeTable.setCurrentPackage(previousPackage);
			innerAnonymousClassCounter++;
		}
	}

	@Override
	public void visit(ExpressionStmt n, VisitorContext arg) {

		n.getExpression().accept(this, arg);

		// tratamiento en caso de tener un removed method

		if (arg.containsKey(UPDATED_EXPRESSION_KEY)
				&& arg.get(UPDATED_EXPRESSION_KEY) == null) {
			// hay que borrar la expresion y por tanto, dicho statement
			arg.put(UPDATED_STATEMENT_KEY, null);
			arg.remove(UPDATED_EXPRESSION_KEY);

		} else {
			if (arg.containsKey(UPDATED_EXPRESSION_KEY)) {
				// hay que substituir la expresion
				n.setExpression((Expression) arg.remove(UPDATED_EXPRESSION_KEY));
			}
		}

	}

	public void visit(ConstructorDeclaration n, VisitorContext arg) {
		symbolTable.pushScope();
		if (n.getParameters() != null) {
			for (Parameter p : n.getParameters()) {
				Type type = p.getType();

				symbolTable.insertSymbol(p.getId().getName(),
						typeTable.valueOf(type), p);
			}
		}
		super.visit(n, arg);
		symbolTable.popScope();

	}

	public void visit(CatchClause n, VisitorContext arg) {
		symbolTable.pushScope();
		MultiTypeParameter exceptionParam = n.getExcept();
		// TODO: Multiple types
		Type type = exceptionParam.getTypes().get(0);
		symbolTable.insertSymbol(exceptionParam.getId().getName(),
				typeTable.valueOf(type), exceptionParam);
		n.getCatchBlock().accept(this, arg);
		symbolTable.popScope();
	}

}
