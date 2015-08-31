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

package org.walkmod.refactor.config;

import java.util.LinkedList;
import java.util.List;

import org.walkmod.javalang.ASTManager;
import org.walkmod.javalang.ParseException;
import org.walkmod.javalang.ast.body.Parameter;
import org.walkmod.javalang.ast.expr.Expression;
import org.walkmod.javalang.ast.type.ClassOrInterfaceType;
import org.walkmod.javalang.ast.type.ReferenceType;
import org.walkmod.javalang.ast.type.VoidType;
import org.walkmod.javalang.compiler.symbols.SymbolType;
import org.walkmod.refactor.visitors.ASTTypeNameResolver;

public class MethodRefactoringRule {

	private MethodHeaderDeclaration sourceMethod;

	private MethodHeaderDeclaration targetMethod;

	private List<String> variables = new LinkedList<String>();
	
	private List<String> expressions = new LinkedList<String>();

	private Boolean isConstant = true;

	private String resultExpression;

	private String resultVariable = "result";

	private String implicitVaribale = "this";

	private String implicitExpression;


	public MethodRefactoringRule(ClassLoader classLoader) {
		sourceMethod = new MethodHeaderDeclaration();
		targetMethod = new MethodHeaderDeclaration();
	}

	public String getScope() {
		return targetMethod.getScope();
	}

	public void setScope(String scope) {
		this.targetMethod.setScope(scope);
	}

	/**
	 * Ha de crear una instancia nueva de expresion por cada string que describe
	 * la expr
	 * 
	 * @return
	 * @throws ParseException
	 */
	public List<Expression> getExpressionTreeArgs() throws ParseException {
		List<Expression> res = new LinkedList<Expression>();
		for (String expression : expressions) {
			res.add((Expression) ASTManager.parse(Expression.class, expression));
		}
		return res;
	}

	public Expression getResultTreeExpression() throws ParseException {
		return (Expression) ASTManager.parse(Expression.class, resultExpression);
	}

	public Expression getImplicitTreeExpression() throws ParseException {
		return (Expression) ASTManager.parse(Expression.class, implicitExpression);
	}

	public void setResultExpression(String resultExpression) {
		if ("void".equals(resultExpression)) {
			targetMethod.setResult(new VoidType());
		} 
		else if(resultExpression != null && resultExpression.startsWith("void")){
			this.resultExpression = resultExpression.substring("void".length());
			targetMethod.setResult(new VoidType());
		}
		else {
			this.resultExpression = resultExpression;
		}
	}

	public boolean isVoidResult() {
		return targetMethod.getResult() != null
				&& targetMethod.getResult().toString().equals("void");
	}

	public String getResultVariable() {
		return resultVariable;
	}

	public void setResultVariable(String resultVariable) {
		this.resultVariable = resultVariable;
	}

	public void setExpressions(List<String> expressions) {
		this.expressions = expressions;
	}

	public Boolean getIsConstant() {
		return isConstant;
	}

	public void setIsConstant(Boolean isConstant) {
		this.isConstant = isConstant;
	}

	public String getMethodName() {
		return targetMethod.getName();
	}

	public void setMethodName(String methodName) {
		this.targetMethod.setName(methodName);
	}

	public List<String> getVariables() {
		return variables;
	}

	public void setVariables(List<String> variables) {
		this.variables = variables;
	}

	public boolean hasResultExpression() {
		return resultExpression != null;
	}

	public boolean hasImplicitExpression() {
		return implicitExpression != null;
	}

	public SymbolType[] getArgTypes() {
		List<Parameter> params = sourceMethod.getArgs();
		SymbolType[] result = new SymbolType[params.size()];
		int i = 0;
		for (Parameter tp : params) {
			result[i]= ASTTypeNameResolver.getInstance().valueOf(tp.getType());
			i++;
		}
		return result;
	}

	public void setArgTypes(List<String> argTypes) {
		List<Parameter> result = new LinkedList<Parameter>();
		for (String arg : argTypes) {
			Parameter tp = new Parameter();
			ReferenceType rt = new ReferenceType();
			ClassOrInterfaceType cit = new ClassOrInterfaceType(arg);
			rt.setType(cit);
			tp.setType(rt);
			result.add(tp);
		}
		this.sourceMethod.setArgs(result);
	}

	public String getSourceScope() {
		return sourceMethod.getScope();
	}

	public void setSourceScope(String sourceScope) {
		this.sourceMethod.setScope(sourceScope);
	}

	public String getSourceMethodName() {
		return sourceMethod.getName();
	}

	public void setSourceMethodName(String sourceMethodName) {
		this.sourceMethod.setName(sourceMethodName);
	}

	public SymbolType[] getArgTypeClasses() throws ClassNotFoundException {
		return sourceMethod.getArgTypeClasses();
	}

	

	public String getImplicitVaribale() {
		return implicitVaribale;
	}

	public void setImplicitVaribale(String implicitVaribale) {
		this.implicitVaribale = implicitVaribale;
	}

	public String getImplicitExpression() {
		return implicitExpression;
	}

	public void setImplicitExpression(String implicitExpression) {
		this.implicitExpression = implicitExpression;
	}


}
