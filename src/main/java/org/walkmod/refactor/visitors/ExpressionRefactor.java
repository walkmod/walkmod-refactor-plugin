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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.walkmod.javalang.ASTManager;
import org.walkmod.javalang.ParseException;
import org.walkmod.javalang.ast.SymbolData;
import org.walkmod.javalang.ast.body.BodyDeclaration;
import org.walkmod.javalang.ast.expr.CastExpr;
import org.walkmod.javalang.ast.expr.Expression;
import org.walkmod.javalang.ast.expr.FieldAccessExpr;
import org.walkmod.javalang.ast.expr.MethodCallExpr;
import org.walkmod.javalang.ast.expr.NameExpr;
import org.walkmod.javalang.ast.expr.ObjectCreationExpr;
import org.walkmod.javalang.ast.expr.UnaryExpr;
import org.walkmod.javalang.ast.type.Type;
import org.walkmod.javalang.compiler.types.Types;
import org.walkmod.javalang.visitors.VoidVisitorAdapter;
import org.walkmod.walkers.VisitorContext;

/**
 * Changes an AST expression applying a variable substitution.
 * 
 * @author rpau
 * 
 */
public class ExpressionRefactor extends VoidVisitorAdapter<VisitorContext> {

	private Map<String, Expression> variables;

	private Map<String, SymbolData> variableTypes;

	private Set<String> refactoredVariables = new HashSet<String>();

	

	private static Logger log = Logger.getLogger(ExpressionRefactor.class);

	public Set<String> getRefactoredVariables() {
		return refactoredVariables;
	}

	public ExpressionRefactor(){
		this(new HashMap<String, Expression>());
	}
	
	public ExpressionRefactor(Map<String, Expression> variable) {
		this.variables = variable;
	}

	public Map<String, Expression> getVariable() {
		return variables;
	}

	public void setVariable(Map<String, Expression> variable) {
		this.variables = variable;
		refactoredVariables.clear();
	}

	@Override
	public void visit(NameExpr n, VisitorContext arg) {
		if (variables.containsKey(n.getName())) {
			n.setData(variables.get(n.getName()));
			refactoredVariables.add(n.getName());
		}
	}

	@Override
	public void visit(UnaryExpr n, VisitorContext arg) {
		if (n.getExpr() instanceof NameExpr) {

			NameExpr ne = ((NameExpr) n.getExpr());

			if (variables.containsKey(ne.getName())) {
				n.setExpr(variables.get(ne.getName()));
				refactoredVariables.add(ne.getName());
			}

		} else {
			n.getExpr().accept(this, arg);
		}
	}

	public void visit(CastExpr n, VisitorContext arg) {
		if (n.getExpr() instanceof NameExpr) {

			NameExpr ne = ((NameExpr) n.getExpr());

			if (variables.containsKey(ne.getName())) {

				Expression updatedExpr = variables.get(ne.getName());
				refactoredVariables.add(ne.getName());

				Class<?> classExpr = variableTypes.get(ne.getName()).getClazz();

				Class<?> castClass = n.getType().getSymbolData().getClazz();
				
				if ( Types.isCompatible(classExpr, castClass)) {

					n.setData(updatedExpr);
				} else {

					if (castClass.isPrimitive() && !classExpr.isPrimitive()
							&& Types.getWrapperClasses().containsKey(classExpr)) {

						updatedExpr = getPrimitiveMethodCallExpr(classExpr,
								updatedExpr);

					}

					n.setExpr(updatedExpr);
				}

			}
		} else {
			n.getExpr().accept(this, arg);
		}

	}

	private MethodCallExpr getPrimitiveMethodCallExpr(Class<?> clazz,
			Expression scope) {
		Map<String, String> wrapperClasses = Types.getWrapperClasses();
		if (wrapperClasses.containsKey(clazz.getName())) {
			String basicType = wrapperClasses.get(clazz.getName());
			String methodCallExpr = scope.toString() + "." + basicType
					+ "Value()";

			try {
				return (MethodCallExpr) ASTManager.parse(MethodCallExpr.class,
						methodCallExpr);

			} catch (ParseException e) {
				throw new RuntimeException(e.getCause());
			}

		}
		throw new RuntimeException("The clazz " + clazz.getName()
				+ " is not a basic type");
	}

	@Override
	public void visit(MethodCallExpr n, VisitorContext arg) {
		if (n.getScope() != null) {

			if (n.getScope() instanceof NameExpr) {

				NameExpr ne = ((NameExpr) n.getScope());

				if (variables.containsKey(ne.getName())) {
					n.setScope(variables.get(ne.getName()));
					refactoredVariables.add(ne.getName());
				}
			} else {
				n.getScope().accept(this, arg);
			}
		}

		if (n.getTypeArgs() != null) {
			for (Type t : n.getTypeArgs()) {
				t.accept(this, arg);
			}
		}
		if (n.getArgs() != null) {
			List<Expression> newExpr = new LinkedList<Expression>();
			for (Expression e : n.getArgs()) {
				e.accept(this, arg);
				if (e.getData() != null) {
					newExpr.add((Expression) e.getData());
				} else {
					newExpr.add(e);
				}
				n.setArgs(newExpr);

			}
		}
	}

	@Override
	public void visit(FieldAccessExpr n, VisitorContext arg) {

		if (n.getScope() != null) {

			if (n.getScope() instanceof NameExpr) {

				NameExpr ne = ((NameExpr) n.getScope());

				if (variables.containsKey(ne.getName())) {
					n.setScope(variables.get(ne.getName()));
					refactoredVariables.add(ne.getName());
				}
			} else {
				n.getScope().accept(this, arg);
			}

		}

	}

	@Override
	public void visit(ObjectCreationExpr n, VisitorContext arg) {

		if (n.getScope() != null) {

			if (n.getScope() instanceof NameExpr) {

				NameExpr ne = ((NameExpr) n.getScope());

				if (variables.containsKey(ne.getName())) {
					n.setScope(variables.get(ne.getName()));
					refactoredVariables.add(ne.getName());
				}
			} else {
				n.getScope().accept(this, arg);
			}
		}
		if (n.getTypeArgs() != null) {
			for (Type t : n.getTypeArgs()) {
				t.accept(this, arg);
			}
		}
		n.getType().accept(this, arg);
		if (n.getArgs() != null) {
			for (Expression e : n.getArgs()) {
				e.accept(this, arg);
			}
		}
		if (n.getAnonymousClassBody() != null) {
			for (BodyDeclaration member : n.getAnonymousClassBody()) {
				member.accept(this, arg);
			}
		}
	}

	public Map<String, SymbolData> getVariableTypes() {
		return variableTypes;
	}

	public void setVariableTypes(Map<String, SymbolData> variableTypes) {
		this.variableTypes = variableTypes;
	}

}
