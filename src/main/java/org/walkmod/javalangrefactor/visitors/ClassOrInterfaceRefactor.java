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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.walkmod.exceptions.WalkModException;
import org.walkmod.javalang.ASTManager;
import org.walkmod.javalang.ParseException;
import org.walkmod.javalang.ast.CompilationUnit;
import org.walkmod.javalang.ast.ImportDeclaration;
import org.walkmod.javalang.ast.PackageDeclaration;
import org.walkmod.javalang.ast.body.TypeDeclaration;
import org.walkmod.javalang.ast.expr.NameExpr;
import org.walkmod.javalang.ast.type.ClassOrInterfaceType;
import org.walkmod.javalang.visitors.VoidVisitorAdapter;
import org.walkmod.walkers.VisitorContext;

public class ClassOrInterfaceRefactor extends
		VoidVisitorAdapter<VisitorContext> {

	private Map<String, String> refactoringRules;

	private Map<String, String> aliasOldNamesMap;
	
	private Map<String, String> aliasNewNamesMap;

	private List<ImportDeclaration> imports = new LinkedList<ImportDeclaration>();

	private CompilationUnit cu;

	private static final Log LOG = LogFactory
			.getLog(ClassOrInterfaceRefactor.class);

	@Override
	public void visit(ClassOrInterfaceType n, VisitorContext arg) {

		String name = n.toString();

		ClassOrInterfaceType aux = null;

		String simpleName = name;
		
		if (aliasOldNamesMap.containsKey(simpleName)) {
			name = aliasOldNamesMap.get(simpleName);
		}

		// n has a complete class name
		if (refactoringRules.containsKey(name)) {

			String newFullName =  refactoringRules.get(name);
			String simplifiedName = aliasNewNamesMap.get(newFullName);
			if(simplifiedName != null){
				newFullName = simplifiedName;
			}
			try {
				aux = (ClassOrInterfaceType) ASTManager.parse(
						ClassOrInterfaceType.class, newFullName);

			} catch (ParseException e) {
				throw new WalkModException(e);
			}

			n.setName(aux.getName());
			n.setScope(aux.getScope());
			n.setTypeArgs(aux.getTypeArgs());

		}

	}

	@Override
	public void visit(ImportDeclaration n, VisitorContext arg) {
		imports.add(n);
		try {
			String selectedType = n.getName().toString();

			if (!n.isAsterisk()) {
				int index = selectedType.lastIndexOf(".");
				String value = refactoringRules.get(selectedType);
				if (value != null) {
					
					if (!n.isStatic()) {
						if (index != -1) {
							String simpleName = selectedType
									.substring(index + 1);
							aliasOldNamesMap.put(simpleName, selectedType);
							index = value.lastIndexOf(".");
							String aliasNewName = null;
							if(index != -1){
								aliasNewName = value.substring(index+1);								
							}
							else{
								aliasNewName = value;
							}
							aliasNewNamesMap.put(value, aliasNewName);
						}
					}
					LOG.debug("Replacing the imports. Import: " + n.getName());

					NameExpr newName = (NameExpr) ASTManager.parse(
							NameExpr.class,
							refactoringRules.get(n.getName().toString()));
					n.setName(newName);

				}
			} else {
				Set<String> oldClassNames = refactoringRules.keySet();
				for (String oldClassName : oldClassNames) {
					if (oldClassName.startsWith(selectedType)) {

						int index = oldClassName.indexOf(".");
						if (!n.isStatic()) {
							if (index != -1) {
								String simpleName = oldClassName
										.substring(index + 1);
								aliasOldNamesMap.put(simpleName, selectedType);
								
								String value = refactoringRules.get(oldClassName);
								index = value.lastIndexOf(".");
								String aliasNewName = null;
								if(index != -1){
									aliasNewName = value.substring(index+1);								
								}
								else{
									aliasNewName = value;
								}
								aliasNewNamesMap.put(value, aliasNewName);
							}
							ImportDeclaration id = new ImportDeclaration((NameExpr)
									ASTManager.parse(NameExpr.class,
											selectedType, true),
									n.isAsterisk(), n.isStatic());
							imports.add(id);
						}
					}
				}

			}
		} catch (Exception e) {
			throw new WalkModException(e);
		}
	}

	public void visit(TypeDeclaration n, VisitorContext arg) {
		n.accept(this, arg);
	}

	public void visit(PackageDeclaration pd, VisitorContext arg) {
		String name = pd.getName().toString();
		Set<String> keys = refactoringRules.keySet();

		for (String key : keys) {
			if (key.startsWith(name)) {
				int index = key.indexOf(".");
				if (index != -1) {
					String alias = key.substring(index + 1);
					aliasOldNamesMap.put(alias, key);
					String value = refactoringRules.get(key);
					index = value.lastIndexOf(".");
					String aliasNewName = null;
					if(index != -1){
						aliasNewName = value.substring(index+1);								
					}
					else{
						aliasNewName = value;
					}
					aliasNewNamesMap.put(value, aliasNewName);
				}
			}
		}
	}

	@Override
	public void visit(CompilationUnit n, VisitorContext arg) {
		if (refactoringRules != null && !refactoringRules.isEmpty()) {
			this.cu = n;
			aliasOldNamesMap = new HashMap<String, String>();
			aliasNewNamesMap = new HashMap<String, String>();
			
			imports = new LinkedList<ImportDeclaration>();
			if (n.getPackage() != null) {
				n.getPackage().accept(this, arg);
			}
			if (n.getImports() != null) {
				// visiting the complete names
				for (ImportDeclaration i : n.getImports()) {
					if (!i.isAsterisk()) {
						i.accept(this, arg);
					}
				}
				// visiting package imports
				for (ImportDeclaration i : n.getImports()) {
					if (i.isAsterisk()) {
						i.accept(this, arg);
					}
				}

			}
			if (n.getTypes() != null) {
				for (TypeDeclaration typeDeclaration : n.getTypes()) {
					typeDeclaration.accept(this, arg);
				}
			}
			cu.setImports(imports);
		}
	}

	public void setRefactoringRules(Map<String, String> refactoringRules) {
		this.refactoringRules = refactoringRules;
	}

}
