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

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import org.apache.log4j.Logger;
import org.walkmod.exceptions.InvalidTransformationRuleException;
import org.walkmod.exceptions.WalkModException;
import org.walkmod.javalang.ast.CompilationUnit;
import org.walkmod.javalang.ast.MethodSymbolData;
import org.walkmod.javalang.ast.SymbolData;
import org.walkmod.javalang.ast.body.VariableDeclarator;
import org.walkmod.javalang.ast.expr.AssignExpr;
import org.walkmod.javalang.ast.expr.BinaryExpr;
import org.walkmod.javalang.ast.expr.Expression;
import org.walkmod.javalang.ast.expr.FieldAccessExpr;
import org.walkmod.javalang.ast.expr.MethodCallExpr;
import org.walkmod.javalang.ast.expr.ObjectCreationExpr;
import org.walkmod.javalang.ast.expr.UnaryExpr;
import org.walkmod.javalang.ast.stmt.BlockStmt;
import org.walkmod.javalang.ast.stmt.ExpressionStmt;
import org.walkmod.javalang.ast.stmt.IfStmt;
import org.walkmod.javalang.ast.stmt.Statement;
import org.walkmod.javalang.ast.stmt.SwitchEntryStmt;
import org.walkmod.javalang.ast.type.Type;
import org.walkmod.javalang.compiler.symbols.RequiresSemanticAnalysis;
import org.walkmod.javalang.compiler.symbols.SymbolType;
import org.walkmod.javalang.visitors.VoidVisitorAdapter;
import org.walkmod.refactor.config.MethodRefactoringRule;
import org.walkmod.refactor.config.RefactoringRulesDictionary;
import org.walkmod.walkers.VisitorContext;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

@RequiresSemanticAnalysis
public class MethodRefactor extends VoidVisitorAdapter<VisitorContext> {

	private RefactoringRulesDictionary refactoringRules;

	private Map<String, String> inputRules;

	private ExpressionRefactor exprRefactor;

	private static final String UPDATED_STATEMENT_KEY = "updated_statement_key";

	private static final String UPDATED_EXPRESSION_KEY = "updated_expression_key";

	public static final String PREVIOUS_REQUIRED_STATEMENTS_KEY = "previous_required_statements";

	public static final String FORWARD_REQUIRED_STATEMENTS_KEY = "forward_required_statements";

	private static Logger LOG = Logger.getLogger(MethodRefactor.class);

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

			this.refactoringRules = new RefactoringRulesDictionary(classLoader);
			exprRefactor = new ExpressionRefactor();

			refactoringRules.putRules(inputRules);
			setUp = true;
		}

	
		super.visit(unit, arg);

	}

	public void visit(AssignExpr n, VisitorContext arg) {
		n.getTarget().accept(this, arg);

		n.getValue().accept(this, arg);

		if (arg.containsKey(UPDATED_EXPRESSION_KEY)) {
			n.setValue((Expression) arg.remove(UPDATED_EXPRESSION_KEY));
		}

	}

	public void visit(VariableDeclarator n, VisitorContext arg) {
		n.getId().accept(this, arg);

		if (n.getInit() != null) {

			n.getInit().accept(this, arg);

			if (arg.containsKey(UPDATED_EXPRESSION_KEY)) {
				n.setInit((Expression) arg.remove(UPDATED_EXPRESSION_KEY));
			}

		}
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

		MethodSymbolData resultType = n.getSymbolData();

		Class<?> scopeClass = resultType.getMethod().getDeclaringClass();
		SymbolType scopeST = new SymbolType(scopeClass);
		try {

			if (n.getScope() != null) {

				// resolving the scope
				n.getScope().accept(this, arg);

				// updating the scope if some refactor is applied
				if (arg.containsKey(UPDATED_EXPRESSION_KEY)) {
					n.setScope((Expression) arg.remove(UPDATED_EXPRESSION_KEY));
				}

			}
			// searching the replacing method
			List<Expression> args = n.getArgs();

			SymbolType[] argClazzes = null;
			List<Expression> refactoredArgs = new LinkedList<Expression>();

			if (args != null) {
				int i = 0;
				argClazzes = new SymbolType[args.size()];

				// guardem els tipus dels parametres
				for (Expression e : args) {

					SymbolData aux = e.getSymbolData();

					if (aux != null) {

						argClazzes[i] = (SymbolType) aux;
					} else {
						// e is a nullLiteralExpr
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
					i++;

				}

				args = refactoredArgs;
			}
			else{
				argClazzes = new SymbolType[0];
			}

			MethodRefactoringRule mrr = refactoringRules.getRefactoringRule(
					scopeST, n.getName(), argClazzes);

			// does exists some refactoring rule?
			if (mrr != null) {

				LOG.debug("refactoring [ " + n.toString() + " ]");
				// changing the method's name
				n.setName(mrr.getMethodName());

				Map<String, Expression> variableMap = new HashMap<String, Expression>();
				Map<String, SymbolData> variableTypes = new HashMap<String, SymbolData>();

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

					variableMap.put(mrr.getImplicitVaribale(), n.getScope());
					variableTypes.put(mrr.getImplicitVaribale(),scopeST);

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

				if (mrr.hasResultExpression()) {

					resultExpression = mrr.getResultTreeExpression();

					Map<String, Expression> map = exprRefactor.getVariable();
					map.put(mrr.getResultVariable(), n);
					exprRefactor.setVariable(map);
					resultExpression.accept(exprRefactor, arg);

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
								.get(PREVIOUS_REQUIRED_STATEMENTS_KEY);

						if (reqStmts == null) {
							reqStmts = new LinkedList<Statement>();
							arg.put(PREVIOUS_REQUIRED_STATEMENTS_KEY, reqStmts);
						}
						ExpressionStmt stmt = new ExpressionStmt(n);
						reqStmts.add(stmt);
					}
					arg.put(UPDATED_EXPRESSION_KEY, resultExpression);
				}

			}

		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	@Override
	public void visit(FieldAccessExpr n, VisitorContext arg) {

		Expression scope = n.getScope();

		if (scope != null) {
			n.getScope().accept(this, arg);
			if (arg.containsKey(UPDATED_EXPRESSION_KEY)) {
				n.setScope((Expression) arg.remove(UPDATED_EXPRESSION_KEY));

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

		if (n.getStmts() != null) {

			List<Statement> stmts = new LinkedList<Statement>();

			for (Statement s : n.getStmts()) {

				s.accept(this, arg);

				@SuppressWarnings("unchecked")
				Collection<Statement> reqStmts = (Collection<Statement>) arg
						.get(PREVIOUS_REQUIRED_STATEMENTS_KEY);
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
						
						arg.remove(UPDATED_STATEMENT_KEY);
					}
				}

				@SuppressWarnings("unchecked")
				Collection<Statement> forStmts = (Collection<Statement>) arg
						.remove(FORWARD_REQUIRED_STATEMENTS_KEY);
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

	}

	public void setRefactoringConfigFile(String refactoringConfigFile)
			throws Exception {
		File file = new File(refactoringConfigFile);
		if (!file.exists()) {
			file = new File("src/main/walkmod/refactor/refactoring-methods.json");
		}
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
				LOG.error("The refactoring config file ["
						+ refactoringConfigFile + "] cannot be read");
			}
		} else {
			LOG.error("The refactoring config file [" + refactoringConfigFile
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

	@Override
	public void visit(BlockStmt n, VisitorContext arg) {

		if (n.getStmts() != null) {
			List<Statement> stmts = new LinkedList<Statement>();
			for (Statement s : n.getStmts()) {

				s.accept(this, arg);

				@SuppressWarnings("unchecked")
				Collection<Statement> reqStmts = (Collection<Statement>) arg
						.get(PREVIOUS_REQUIRED_STATEMENTS_KEY);
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
						
						arg.remove(UPDATED_STATEMENT_KEY);
					}
				}

				@SuppressWarnings("unchecked")
				Collection<Statement> forStmts = (Collection<Statement>) arg
						.remove(FORWARD_REQUIRED_STATEMENTS_KEY);
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

	@Override
	public void visit(ObjectCreationExpr n, VisitorContext arg) {

		SymbolData objectScope = n.getSymbolData();

		if (n.getTypeArgs() != null) {
			for (Type t : n.getTypeArgs()) {
				t.accept(this, arg);
			}
		}
		n.getType().accept(this, arg);

		SymbolType[] argStr = null;

		List<Expression> args = n.getArgs();
		List<Expression> refactoredArgs = new LinkedList<Expression>();
		if (args != null) {

			argStr = new SymbolType[n.getArgs().size()];
			int i = 0;
			for (Expression e : args) {

				SymbolData eType = e.getSymbolData();

				if (eType != null) {
					argStr[i] = (SymbolType)eType;
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

				i++;
			}

			n.setArgs(refactoredArgs);
		}
		try {
			MethodRefactoringRule mrr = refactoringRules.getRefactoringRule(
					(SymbolType)objectScope, n.getType().getName(), argStr);

			if (mrr != null) {
				LOG.debug("refactoring [" + n.toString() + "]");
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

		super.visit(n, arg);
	}

	@Override
	public void visit(ExpressionStmt n, VisitorContext arg) {

		n.getExpression().accept(this, arg);
		
		if (arg.containsKey(UPDATED_EXPRESSION_KEY)
				&& arg.get(UPDATED_EXPRESSION_KEY) == null) {
			
			arg.put(UPDATED_STATEMENT_KEY, null);
			arg.remove(UPDATED_EXPRESSION_KEY);

		} else {
			if (arg.containsKey(UPDATED_EXPRESSION_KEY)) {
				n.setExpression((Expression) arg.remove(UPDATED_EXPRESSION_KEY));
			}
		}

	}

}
