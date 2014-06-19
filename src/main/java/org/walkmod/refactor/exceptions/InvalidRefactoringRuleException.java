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
package org.walkmod.refactor.exceptions;

import org.walkmod.exceptions.WalkModException;

public class InvalidRefactoringRuleException extends WalkModException {

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = 9001388591915734947L;

	public InvalidRefactoringRuleException() {
		super();
	}

	public InvalidRefactoringRuleException(String msg, Throwable cause) {
		super(msg, cause);
	}

	public InvalidRefactoringRuleException(String msg) {
		super(msg);
	}

	public InvalidRefactoringRuleException(Throwable cause) {
		super(cause);
	}
}
