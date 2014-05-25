package org.walkmod.javalangrefactor.exceptions;

import org.walkmod.exceptions.WalkModException;

public class InvalidRefactoringRuleException extends WalkModException{
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
