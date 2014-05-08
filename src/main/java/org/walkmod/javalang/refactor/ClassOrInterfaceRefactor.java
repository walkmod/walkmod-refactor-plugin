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

import org.walkmod.javalang.ParseException;
import org.walkmod.javalang.ASTManager;
import org.walkmod.javalang.ast.CompilationUnit;
import org.walkmod.javalang.ast.ImportDeclaration;
import org.walkmod.javalang.ast.body.TypeDeclaration;
import org.walkmod.javalang.ast.expr.NameExpr;
import org.walkmod.javalang.ast.type.ClassOrInterfaceType;
import org.walkmod.javalang.visitors.VoidVisitorAdapter;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.walkmod.exceptions.WalkModException;
import org.walkmod.walkers.VisitorContext;

public class ClassOrInterfaceRefactor extends
		VoidVisitorAdapter<VisitorContext> {

	private Map<String, String> entryMap;

	private Collection<ImportDeclaration> imports = new LinkedList<ImportDeclaration>();

	private Collection<String> oldImports = new LinkedList<String>();

	private CompilationUnit cu;

	private static final Log LOG = LogFactory
			.getLog(ClassOrInterfaceRefactor.class);

	@Override
	public void visit(ClassOrInterfaceType n, VisitorContext arg) {

		String name = n.toString();

		ClassOrInterfaceType aux = null;

		// n has a complete class name
		if (!Character.isUpperCase(name.charAt(0)) && name.contains(".")
				&& entryMap.containsKey(name)) {

			String newFullName = entryMap.get(name);
			try {
				aux = (ClassOrInterfaceType) ASTManager.parse(
						ClassOrInterfaceType.class, newFullName);

			} catch (ParseException e) {
				throw new WalkModException(e);
			}

			n.setName(aux.getName());
			n.setScope(aux.getScope());
			n.setTypeArgs(aux.getTypeArgs());

		} else {

			// checking when a new type name must be applied

			for (Entry<String, String> entry : entryMap.entrySet()) {

				String oldName = entry.getKey();

				if (oldName.endsWith("." + name)) {

					ImportDeclaration id = getImport(entry.getValue());

					String newName = entry.getValue();

					int index = newName.lastIndexOf(".");
					String packageName = "";
					if (index != -1) {
						packageName = newName.substring(0, index - 1);
						newName = simplifyName(newName);
					}

					if (id == null) {

						if (cu.getPackage() != null
								&& !cu.getPackage().getName().getName()
										.equals(packageName)) {

							String lookUpName = oldName;

							ImportDeclaration oldImport = getImport(lookUpName);

							// the parent class alreday has been refactored or
							// it is impossible to determine because imports
							// have *
							if (oldImport == null) {
								// old name is a complete name
								lookUpName = getInnerParentClassName(oldName);
								if (lookUpName != null) {

									// the parent class alreday has been
									// refactored
									oldImport = getImport(entryMap
											.get(lookUpName));
									if (oldImport != null) {
										oldImports.add(oldName);
										id = new ImportDeclaration();
										id.setName(new NameExpr(entry
												.getValue()));
										cu.getImports().add(id);

										LOG.debug("Adding a new import. Import: "
												+ entry.getValue());
										newName = simplifyName(newName);
									}
								}

							} else {
								if (lookUpName.equals(oldImport.getName()
										.toString())) {
									oldImports.add(lookUpName);
									id = new ImportDeclaration();
									id.setName(new NameExpr(entry.getValue()));
									cu.getImports().add(id);

									LOG.debug("Adding a new import. Import: "
											+ entry.getValue());
									newName = simplifyName(newName);
								}
							}

						}
					}

					if (oldImports.contains(oldName)) {

						try {

							ClassOrInterfaceType t = (ClassOrInterfaceType) ASTManager
									.parse(ClassOrInterfaceType.class, newName);
							n.setName(t.getName());
							n.setScope(t.getScope());
							return;

						} catch (ParseException e) {
							throw new WalkModException(e);
						}

					}

				}

			}

		}

	}

	public String simplifyName(String name) {
		int index = name.lastIndexOf(".");
		String result = name;
		if (index != -1) {
			String auxName = getInnerParentClassName(name);
			if (auxName != null) {
				result = auxName.substring(auxName.lastIndexOf(".") + 1) + "."
						+ name.substring(index + 1);
			} else {
				result = name.substring(index + 1);
			}
		}
		return result;
	}

	@Override
	public void visit(ImportDeclaration n, VisitorContext arg) {
		imports.add(n); // para saber los imports del class or interface

		if (!n.isAsterisk() && entryMap.containsKey(n.getName().toString())) {

			LOG.debug("Replacing the imports. Import: " + n.getName());
			oldImports.add(n.getName().toString());

			try {
				NameExpr newName = (NameExpr) ASTManager.parse(NameExpr.class,
						entryMap.get(n.getName().toString()));
				n.setName(newName);
			} catch (ParseException e) {
				throw new WalkModException(e);
			}

		}
	}

	public void visit(TypeDeclaration n, VisitorContext arg) {
		n.accept(this, arg);
	}

	@Override
	public void visit(CompilationUnit cu, VisitorContext arg) {
		this.cu = cu;
		super.visit(cu, arg);
		oldImports.clear();
		imports.clear();
	}

	public void setNameEntryMap(Map<String, String> entryMap) {
		this.entryMap = entryMap;
	}

	private String getInnerParentClassName(String name) {

		String[] nameParts = name.split("\\.");
		String innerClassName = null;

		if (nameParts.length > 1) {

			if (Character
					.isUpperCase(nameParts[nameParts.length - 2].charAt(0))) {
				// it is an inner class.

				for (int i = 0; i < nameParts.length - 1; i++) {
					if (i != 0) {
						innerClassName = innerClassName + "." + nameParts[i];
					} else {
						innerClassName = nameParts[i];
					}
				}
			}
		}
		return innerClassName;
	}

	/**
	 * Gets the import declaration which matches with the type name (complete or
	 * incomplete name)
	 * 
	 * @param name
	 * @return
	 */
	private ImportDeclaration getImport(String name) {

		String lookUpName = name;

		String innerParentClassName = getInnerParentClassName(name);

		if (innerParentClassName != null) {
			lookUpName = innerParentClassName;
		}

		for (ImportDeclaration id : imports) {
			String importName = id.getName().toString();

			if (importName.endsWith(lookUpName)) {
				return id;
			}
		}
		return null;
	}
}
