package org.walkmod.refactor.visitors;

import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

import org.junit.Test;
import org.walkmod.javalang.ast.CompilationUnit;
import org.walkmod.javalang.ast.body.MethodDeclaration;
import org.walkmod.javalang.ast.expr.MethodCallExpr;
import org.walkmod.javalang.ast.expr.UnaryExpr;
import org.walkmod.javalang.ast.stmt.IfStmt;
import org.walkmod.javalang.test.SemanticTest;
import org.walkmod.walkers.VisitorContext;

public class MethodRefactorTest extends SemanticTest {

	public CompilationUnit getRefactoredSource(Map<String, String> methodRules,
			String... code) throws Exception {

		CompilationUnit cu = compile(code);

		MethodRefactor coi = new MethodRefactor();

		coi.setClassLoader(getClassLoader());

		coi.setRefactoringRules(methodRules);

		cu.accept(coi, new VisitorContext());

		return cu;

	}

	@Test
	public void testSimpleMethodCall() throws Exception {
		String code = "public class Foo { public void hi() { System.out.print(\"hello\");}}";

		Map<String, String> rules = new HashMap<String, String>();
		rules.put("java.io.PrintStream:print(java.lang.String text)",
				"java.io.PrintStream:println(text)");

		CompilationUnit cu = getRefactoredSource(rules, code);

		MethodDeclaration md = (MethodDeclaration) cu.getTypes().get(0)
				.getMembers().get(0);
		String stmt = md.getBody().getStmts().get(0).toString();
		Assert.assertEquals("System.out.println(\"hello\");", stmt);

	}

	@Test
	public void testMultipleMethodCall() throws Exception {
		String code = "public class Foo { public void hi() { \"hello\".substring(0).substring(3);}}";

		Map<String, String> rules = new HashMap<String, String>();
		rules.put("java.lang.String:substring(int pos)",
				"java.lang.String:concat(\"a\")");

		CompilationUnit cu = getRefactoredSource(rules, code);

		MethodDeclaration md = (MethodDeclaration) cu.getTypes().get(0)
				.getMembers().get(0);
		String stmt = md.getBody().getStmts().get(0).toString();
		Assert.assertEquals("\"hello\".concat(\"a\").concat(\"a\");", stmt);

	}

	@Test
	public void testSymbols() throws Exception {
		String code = "public class Foo { public void hi(String bar) { bar.substring(0).substring(3);}}";

		Map<String, String> rules = new HashMap<String, String>();
		rules.put("java.lang.String:substring(int pos)",
				"java.lang.String:concat(\"a\")");

		CompilationUnit cu = getRefactoredSource(rules, code);

		MethodDeclaration md = (MethodDeclaration) cu.getTypes().get(0)
				.getMembers().get(0);
		String stmt = md.getBody().getStmts().get(0).toString();
		Assert.assertEquals("bar.concat(\"a\").concat(\"a\");", stmt);

	}

	@Test
	public void testResultTransformations() throws Exception {
		String code = "import java.io.File; public class A { public void hello(File file){ "
				+ "Bar bar = new Bar(); "
				+ "bar.open(file); "
				+ "if(bar.isOpen()){}" + " }}";

		String barCode = "import java.io.File; public class Bar { public void open(File file){} public boolean isOpen() {return false;}}";
		Map<String, String> rules = new HashMap<String, String>();
		rules.put("Bar:isOpen()", "Bar:isClosed():!result");
		CompilationUnit cu = getRefactoredSource(rules, code, barCode);

		MethodDeclaration md = (MethodDeclaration) cu.getTypes().get(0)
				.getMembers().get(0);
		IfStmt ifStmt = (IfStmt)md.getBody().getStmts().get(2);
		
		Assert.assertTrue((ifStmt.getCondition() instanceof UnaryExpr));
	}
	
	@Test
	public void testResultTransformationsWithExistingParams() throws Exception {
		String code = "import java.io.File; public class A { public void hello(File file){ "
				+ "Bar bar = new Bar(); "
				+ "if(bar.isOpen(file)){}" + " }}";

		String barCode = "import java.io.File; public class Bar { public boolean isOpen(File file){return false;}}";
		Map<String, String> rules = new HashMap<String, String>();
		rules.put("Bar:isOpen(java.io.File file)", "[file]:isOpen()");
		CompilationUnit cu = getRefactoredSource(rules, code, barCode);

		MethodDeclaration md = (MethodDeclaration) cu.getTypes().get(0)
				.getMembers().get(0);
		IfStmt ifStmt = (IfStmt)md.getBody().getStmts().get(1);
		
		Assert.assertTrue((ifStmt.getCondition() instanceof MethodCallExpr));
		
		MethodCallExpr call = (MethodCallExpr)ifStmt.getCondition();
		Assert.assertEquals("file", call.getScope().toString());
	}
	
	@Test
	public void testResultTransformationsWithExistingParamsAndResult() throws Exception {
		String code = "import java.io.File; public class A { public void hello(File file){ "
				+ "Bar bar = new Bar(); "
				+ "if(bar.isOpen(file)){}" + " }}";

		String barCode = "import java.io.File; public class Bar { public boolean isOpen(File file){return false;}}";
		Map<String, String> rules = new HashMap<String, String>();
		rules.put("Bar:isOpen(java.io.File file)", "Bar:prepare():file.isOpen()");
		CompilationUnit cu = getRefactoredSource(rules, code, barCode);

		MethodDeclaration md = (MethodDeclaration) cu.getTypes().get(0)
				.getMembers().get(0);
		Assert.assertEquals(3, md.getBody().getStmts().size());
		
		IfStmt ifStmt = (IfStmt)md.getBody().getStmts().get(2);
		
		Assert.assertTrue((ifStmt.getCondition() instanceof MethodCallExpr));
		
		MethodCallExpr call = (MethodCallExpr)ifStmt.getCondition();
		Assert.assertEquals("file", call.getScope().toString());
	}

	@Test
	public void testParsingConfigFile() throws Exception {

		MethodRefactor coi = new MethodRefactor();
		coi.setRefactoringConfigFile("src/test/resources/refactoring-methods-config.json");

		Assert.assertEquals(2, coi.getRefactoringRules().size());

	}

}
