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

import org.walkmod.exceptions.WalkModException;
import org.walkmod.javalang.ASTManager;
import org.walkmod.javalang.ParseException;
import org.walkmod.javalang.ast.expr.Expression;
import org.walkmod.javalang.ast.expr.FieldAccessExpr;
import org.walkmod.javalang.ast.expr.NameExpr;

public class ConstantTransformationRule {

	private String sourceConstantExpr;

	private String targetConstantExpr;

	private boolean isEnum = false;

	public String getSourceConstantExpr() {
		return sourceConstantExpr;
	}

	public void setSourceConstantExpr(String sourceConstantExpr) {
		this.sourceConstantExpr = sourceConstantExpr;
	}

	public String getTargetConstantExpr() {
		return targetConstantExpr;
	}

	public Expression getTargetASTExpr() {
		try {
			return (Expression) ASTManager.parse(Expression.class,
					targetConstantExpr);

		} catch (ParseException e) {
			throw new WalkModException(e);
		}
	}

	public void setTargetConstantExpr(String targetConstantExpr) {
		try {
			if (targetConstantExpr.contains("enum ")) {
				isEnum = true;
				targetConstantExpr = targetConstantExpr.replace("enum ", "");
			}
			Expression targetASTExpr = (Expression) ASTManager.parse(
					Expression.class, targetConstantExpr);
			if (!(targetASTExpr instanceof FieldAccessExpr)
					&& !(targetASTExpr instanceof NameExpr)) {

				throw new WalkModException(
						"The target rule expression is invalid "
								+ targetConstantExpr);
			}
			this.targetConstantExpr = targetConstantExpr;
		} catch (ParseException e) {

			throw new WalkModException(e);
		}
	}

	public boolean matchOriginalValue(String originalValue) {
		return sourceConstantExpr.equals(originalValue);
	}

	public boolean match(String originalValue, String targetType) {

		int index = targetConstantExpr.lastIndexOf(".");
		String aux = targetConstantExpr;
		if (index != -1) {
			aux = aux.substring(0, index);
		}
		return originalValue.equals(sourceConstantExpr)
				&& targetType.equals(aux);
	}

	public boolean isEnum() {
		return isEnum;
	}

}
