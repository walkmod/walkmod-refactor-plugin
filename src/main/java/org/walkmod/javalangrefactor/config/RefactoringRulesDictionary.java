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

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.walkmod.exceptions.WalkModException;
import org.walkmod.javalang.compiler.Types;
import org.walkmod.javalang.compiler.TypeTable;
import org.walkmod.javalangrefactor.exceptions.InvalidRefactoringRuleException;


public class RefactoringRulesDictionary {

	private Collection<MethodRefactoringRule> refactoringRules;

	private TypeTable typeTable;
	
	private static Logger log = Logger.getLogger(RefactoringRulesDictionary.class);

	public RefactoringRulesDictionary(TypeTable typeTable) {
		refactoringRules = new LinkedList<MethodRefactoringRule>();

		this.typeTable = typeTable;
	}

	public MethodRefactoringRule getRefactoringRule(String scopeType,
			String method, String[] args) throws ClassNotFoundException {

		if (scopeType == null) {
			throw new IllegalArgumentException("scopeType is null");
		}
		if (method == null) {
			throw new IllegalArgumentException("method is null");
		}

		Iterator<MethodRefactoringRule> it = refactoringRules.iterator();

		while (it.hasNext()) {

			MethodRefactoringRule current = it.next();

			if (current.getSourceScope().equals(scopeType)
					|| Types.isCompatible(
							typeTable.loadClass(scopeType),
							typeTable.loadClass(current.getSourceScope()))) {

				if (current.getSourceMethodName().equals(method)) {

					List<String> argTypes = current.getArgTypes();

					boolean compatible = (args == null && (argTypes.size() == 0 || argTypes == null))
							|| 
							(args!= null && argTypes!= null && args.length == argTypes.size());

					Iterator<String> jt = argTypes.iterator();
					if (args != null) {

						for (int i = 0; i < args.length && compatible
								&& jt.hasNext(); i++) {

							String superClazz = jt.next();

							if (!args[i].equals(superClazz)
									&& !Types.isCompatible(
											typeTable.loadClass(args[i]),
											typeTable.loadClass(superClazz))) {

								compatible = false;
							}

						}
					}
					if (compatible) {

						return current;
					}
				}

			}
		}
		return null;
	}

	public void putRules(Map<String, String> refactoringRules)
			throws InvalidRefactoringRuleException {

		String key, value;
		MethodRefactoringRule rule;

		int ruleNumber = 0;
		for (Entry<String, String> entry : refactoringRules.entrySet()) {

			log.info(ruleNumber+": for "+entry.getKey());
			ruleNumber++;
			key = entry.getKey();
			value = entry.getValue();

			rule = new MethodRefactoringRule();
			rule.setTypeTable(typeTable);

			// source scope
			int parentScopeIndex = key.indexOf(':');
			if (parentScopeIndex == -1) {
				throw new InvalidRefactoringRuleException(
						"Invalid rule format. "
								+ "The scope cannot be parsed for the rule:<"
								+ key + " => " + value
								+ ">. Please verify the documentation");
			}
			String scope = key.substring(0, parentScopeIndex);

			rule.setSourceScope(scope);

			int scopeIndex = value.indexOf(':');
			if (scopeIndex == -1) {
				throw new InvalidRefactoringRuleException(
						"Invalid rule format. "
								+ "The scope cannot be parsed for the rule:<"
								+ key + " => " + value
								+ ">. Please verify the documentation");
			}
			scope = value.substring(0, scopeIndex);
			rule.setScope(scope);

			// scope
			int implicitIndex = scope.indexOf('[');
			if (implicitIndex != -1) {

				String implicitExpression = scope.substring(implicitIndex + 1,
						scope.indexOf(']'));

				rule.setImplicitExpression(implicitExpression);
				scope = value.substring(0, implicitIndex);
				rule.setScope(scope);
			}

			int methodIndex = key.indexOf('(', parentScopeIndex);
			if (methodIndex == -1) {

				throw new InvalidRefactoringRuleException(
						"Invalid rule format. "
								+ "The method cannot be parsed for the rule: <"
								+ key + " => " + value
								+ ">. Please verify the documentation");
			}
			String method = key.substring(parentScopeIndex + 1, methodIndex);
			rule.setSourceMethodName(method);

			// method
			int parentIndex = value.indexOf('(', scopeIndex);
			if (parentIndex == -1) {

				throw new InvalidRefactoringRuleException(
						"Invalid rule format. "
								+ "The method cannot be parsed for the rule: <"
								+ key + " => " + value
								+ ">. Please verify the documentation");
			}
			method = value.substring(scopeIndex + 1, parentIndex);
			rule.setMethodName(method);

			// result
			int resultIndex = value.indexOf(':', parentIndex);
			if (resultIndex > parentIndex) {
				String resultExpr = value.substring(resultIndex + 1);
				rule.setResultExpression(resultExpr);
			} else {
				resultIndex = value.length();
			}

			// param expressions
			String args = value.substring(parentIndex + 1, resultIndex - 1);
			if (args.trim().length() > 0) {
				String[] expressions = args.split(";");
				rule.setExpressions(Arrays.asList(expressions));
			}

			scopeIndex = key.indexOf(':');

			if (scopeIndex == -1) {
				throw new InvalidRefactoringRuleException(
						"Invalid rule format. "
								+ "The scope cannot be parsed for the rule:<"
								+ key + " => " + value
								+ ">. Please verify the documentation");
			}

			// variables
			int startIndex = key.indexOf('(', scopeIndex);

			if (startIndex == -1) {
				throw new InvalidRefactoringRuleException(
						"Invalid rule format. "
								+ "The args cannot be parsed for the rule:<"
								+ key + " => " + value
								+ ">. Please verify the documentation");
			}

			String argExpressions = key.substring(startIndex + 1,
					key.length() - 1);
			if (!argExpressions.isEmpty()) {
				String[] argExpr = argExpressions.split(";");
				String[] variables = new String[argExpr.length];
				List<String> argTypes = new LinkedList<String>();
				for (int i = 0; i < variables.length; i++) {
					int varIndex = argExpr[i].lastIndexOf(" ");
					if (varIndex == -1) {
						throw new InvalidRefactoringRuleException(
								"Invalid rule format. "
										+ "The variables cannot be parsed for the rule:<"
										+ key + " => " + value
										+ ">. Please verify the documentation");
					}
					String type = argExpr[i].substring(0, varIndex).trim();
					argTypes.add(type);
					variables[i] = argExpr[i].substring(varIndex + 1).trim();
				}
				rule.setArgTypes(argTypes);
				rule.setVariables(Arrays.asList(variables));
			}

			Class<?>[] argTypeClasses;
			try {
				argTypeClasses = rule.getArgTypeClasses();

			} catch (ClassNotFoundException e) {
				throw new WalkModException(
						"Invalid arg types in the rule number " + ruleNumber, e);
			}

			Class<?> scopeClass;
			try {
				scopeClass = Class.forName(rule.getSourceScope(), false, Thread
						.currentThread().getContextClassLoader());
			} catch (ClassNotFoundException e) {
				throw new WalkModException("Invalid source class ["
						+ rule.getSourceScope() + "] in the rule number "
						+ ruleNumber, e);
			}

			if (!scopeClass.getSimpleName().equals(rule.getSourceMethodName())) {

				java.lang.reflect.Type returnType = null;
				try {
					returnType = scopeClass.getMethod(
							rule.getSourceMethodName(), argTypeClasses)
							.getGenericReturnType();
				} catch (Exception e) {
					throw new WalkModException("Invalid method ["
							+ rule.getSourceScope() + "#"
							+ rule.getSourceMethodName()
							+ " in the rule number " + ruleNumber, e);
				}
				if (returnType != null) {
					rule.setResultType(returnType);
				}
			} else {
				// the constructor
				try {
					scopeClass.getConstructor(argTypeClasses);
				} catch (Exception e) {

					throw new WalkModException(
							"Invalid args for the constructor ["
									+ rule.getSourceMethodName() + "]", e);

				}
				rule.setResultType(scopeClass);
			}

			rule.setTypeTable(typeTable);
			this.refactoringRules.add(rule);
		}
	}
}
