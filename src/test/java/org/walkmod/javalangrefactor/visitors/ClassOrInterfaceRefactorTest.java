package org.walkmod.javalangrefactor.visitors;

import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

import org.junit.Test;
import org.walkmod.javalang.ASTManager;
import org.walkmod.javalang.ast.CompilationUnit;
import org.walkmod.javalang.ast.ImportDeclaration;
import org.walkmod.javalang.ast.body.BodyDeclaration;
import org.walkmod.javalang.ast.body.FieldDeclaration;
import org.walkmod.javalangrefactor.visitors.ClassOrInterfaceRefactor;
import org.walkmod.walkers.VisitorContext;

public class ClassOrInterfaceRefactorTest {

	@Test
	public void testImportsRefactor() throws Exception {
		String code = "import java.util.List; public class Foo {}";
		CompilationUnit cu = ASTManager.parse(code);

		ClassOrInterfaceRefactor coi = new ClassOrInterfaceRefactor();

		Map<String, String> refactorMap = new HashMap<String, String>();
		refactorMap.put("java.util.List", "java.util.Collection");

		coi.setRefactoringRules(refactorMap);
		cu.accept(coi, new VisitorContext());

		ImportDeclaration id = cu.getImports().get(0);
		String name = id.getName().toString();
		Assert.assertEquals("java.util.Collection", name);
		
	}

	@Test
	public void testTypes() throws Exception {

		String code = "import java.util.List; public class Foo { private List list;}";
		CompilationUnit cu = ASTManager.parse(code);

		ClassOrInterfaceRefactor coi = new ClassOrInterfaceRefactor();

		Map<String, String> refactorMap = new HashMap<String, String>();
		refactorMap.put("java.util.List", "java.util.Collection");

		coi.setRefactoringRules(refactorMap);
		cu.accept(coi, new VisitorContext());

		BodyDeclaration field = cu.getTypes().get(0).getMembers().get(0);

		FieldDeclaration fd = ((FieldDeclaration) field);

		Assert.assertEquals("Collection", fd.getType().toString());
	}

	@Test
	public void testPackageClass() throws Exception {
		String code = "package bar; public class Foo { private c elem; }";
		CompilationUnit cu = ASTManager.parse(code);

		ClassOrInterfaceRefactor coi = new ClassOrInterfaceRefactor();

		Map<String, String> refactorMap = new HashMap<String, String>();
		refactorMap.put("bar.c", "bar.C");
		
		coi.setRefactoringRules(refactorMap);
		cu.accept(coi, new VisitorContext());

		BodyDeclaration field = cu.getTypes().get(0).getMembers().get(0);

		FieldDeclaration fd = ((FieldDeclaration) field);

		Assert.assertEquals("C", fd.getType().toString());

	}
}
