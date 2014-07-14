package org.walkmod.refactor.visitors;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.junit.Test;
import org.walkmod.javalang.compiler.SymbolType;
import org.walkmod.javalang.compiler.TypeTable;
import org.walkmod.refactor.exceptions.InvalidTypeException;
import org.walkmod.walkers.VisitorContext;

public class ExpressionTypeAnalyzerTest {

	@Test
	public void testFindMethod() throws SecurityException,
			NoSuchMethodException, ClassNotFoundException, InvalidTypeException {

		TypeTable tt = new TypeTable();

		ExpressionTypeAnalyzer eta = new ExpressionTypeAnalyzer(tt, null);

		List<String> aux = new LinkedList<String>();

		Method m = eta.getMethod(aux.iterator().getClass(), "next", null, null,
				null, null, new VisitorContext(),
				new HashMap<String, SymbolType>(), true);

		Assert.assertNotNull(m);
	}

	@Test
	public void testResultTypeOfGenerics() throws SecurityException,
			NoSuchMethodException, ClassNotFoundException, InvalidTypeException {
		TypeTable tt = new TypeTable();

		ExpressionTypeAnalyzer eta = new ExpressionTypeAnalyzer(tt, null);

		List<String> aux = new LinkedList<String>();

		Map<String, SymbolType> tm = new HashMap<String, SymbolType>();

		tm.put("E", new SymbolType("java.lang.String"));

		Method m = eta.getMethod(aux.iterator().getClass(), "next", null, null,
				null, null, new VisitorContext(), tm, true);

		Assert.assertEquals("java.util.LinkedList$ListItr", m
				.getDeclaringClass().getName());
	}

}
