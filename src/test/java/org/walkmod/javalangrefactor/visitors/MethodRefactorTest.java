package org.walkmod.javalangrefactor.visitors;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import junit.framework.Assert;

import org.junit.Test;
import org.walkmod.javalang.ASTManager;
import org.walkmod.javalang.ast.CompilationUnit;
import org.walkmod.javalang.ast.body.MethodDeclaration;
import org.walkmod.javalangrefactor.visitors.MethodRefactor;
import org.walkmod.walkers.VisitorContext;

public class MethodRefactorTest {

	private static String TEST_DIR = "./src/test/resources/tmp/";

	private static String COMPILATION_DIR = "./src/test/resources/tmp/classes";

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void compile(File... files) throws IOException {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		List<String> optionList = new ArrayList<String>();
		File tmp = new File(COMPILATION_DIR);
		if (tmp.mkdirs() || tmp.exists()) {
			optionList.addAll(Arrays.asList("-d", tmp.getAbsolutePath()));
			StandardJavaFileManager sjfm = compiler.getStandardFileManager(
					null, null, null);
			Iterable fileObjects = sjfm.getJavaFileObjects(files);
			JavaCompiler.CompilationTask task = compiler.getTask(null, null,
					null, optionList, null, fileObjects);
			task.call();
			sjfm.close();
		}
	}

	public CompilationUnit getRefactoredSource(String code,
			Map<String, String> methodRules) throws Exception {

		CompilationUnit cu = ASTManager.parse(code);
		File srcDir = new File(TEST_DIR, "src");

		if (srcDir.mkdirs() || srcDir.exists()) {

			File tmpClass = new File(TEST_DIR, "Foo.java");
			FileWriter fw = new FileWriter(tmpClass);
			fw.write(code);
			fw.flush();
			fw.close();

			compile(tmpClass);

			File aux = new File(COMPILATION_DIR);

			ClassLoader cl = new URLClassLoader(
					new URL[] { aux.toURI().toURL() });

			MethodRefactor coi = new MethodRefactor();

			coi.setClassLoader(cl);

			coi.setRefactoringRules(methodRules);

			cu.accept(coi, new VisitorContext());
			tmpClass.delete();
			return cu;
		}
		return null;

	}

	@Test
	public void testSimpleMethodCall() throws Exception {
		String code = "public class Foo { public void hi() { System.out.print(\"hello\");}}";

		Map<String, String> rules = new HashMap<String, String>();
		rules.put("java.io.PrintStream:print(java.lang.String text)",
				"java.io.PrintStream:println(text)");

		CompilationUnit cu = getRefactoredSource(code, rules);

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

		CompilationUnit cu = getRefactoredSource(code, rules);

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

		CompilationUnit cu = getRefactoredSource(code, rules);

		MethodDeclaration md = (MethodDeclaration) cu.getTypes().get(0)
				.getMembers().get(0);
		String stmt = md.getBody().getStmts().get(0).toString();
		Assert.assertEquals("bar.concat(\"a\").concat(\"a\");", stmt);
	}

	@Test
	public void testParsingConfigFile() throws Exception {

		MethodRefactor coi = new MethodRefactor();
		coi.setRefactoringConfigFile("src/test/resources/refactoring-methods-config.json");

		Assert.assertEquals(2, coi.getRefactoringRules().size());
	}

}
