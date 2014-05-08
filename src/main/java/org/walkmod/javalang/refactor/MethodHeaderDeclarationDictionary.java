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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.walkmod.javalang.ASTManager;
import org.walkmod.javalang.ParseException;
import org.walkmod.javalang.ast.body.MethodDeclaration;
import org.walkmod.javalang.ast.body.Parameter;
import org.walkmod.javalang.compiler.TypeTable;

public class MethodHeaderDeclarationDictionary implements
		Collection<MethodHeaderDeclaration> {

	private Collection<MethodHeaderDeclaration> methods = new LinkedList<MethodHeaderDeclaration>();

	private Map<String, Collection<MethodHeaderDeclaration>> dictionary = new HashMap<String, Collection<MethodHeaderDeclaration>>();

	private TypeTable typeTable;

	public MethodHeaderDeclaration parse(String methodHeaderDeclaration)
			throws ParseException {

		if (methodHeaderDeclaration != null
				&& !methodHeaderDeclaration.trim().equals("")) {

			int indexMethod = methodHeaderDeclaration.indexOf("#");

			if (indexMethod != -1) {
				indexMethod++;
			} else {
				throw new ParseException("Error Any scope has been defined in "
						+ methodHeaderDeclaration);

			}
			String scope = methodHeaderDeclaration
					.substring(0, indexMethod - 1);

			String method = methodHeaderDeclaration.substring(indexMethod)
					+ ";";

			MethodDeclaration md = (MethodDeclaration) ASTManager.parse(
					MethodDeclaration.class, method);

			MethodHeaderDeclaration mhd = new MethodHeaderDeclaration();

			mhd.setName(md.getName());
			mhd.setModifiers(md.getModifiers());
			mhd.setScope(scope);
			mhd.setArgs(md.getParameters());
			mhd.setExceptions(md.getThrows());
			mhd.setResult(md.getType());
			return mhd;

		}
		return null;
	}

	public boolean add(String methodDeclaration) throws ParseException {

		MethodHeaderDeclaration md = parse(methodDeclaration);

		String method = md.getName();
		String scope = md.getScope();
		String key = method;
		if (scope != null && !"".equals(scope.trim())) {
			key = scope + "#" + key;
		}
		Collection<MethodHeaderDeclaration> value = dictionary.get(key);
		if (value == null) {
			value = new LinkedList<MethodHeaderDeclaration>();
		}
		value.add(md);
		dictionary.remove(key);
		dictionary.put(key, value);
		return methods.add(md);

	}

	public Collection<MethodHeaderDeclaration> get(String scope) {

		Collection<MethodHeaderDeclaration> result = new LinkedList<MethodHeaderDeclaration>();

		for (MethodHeaderDeclaration mhd : methods) {

			if (mhd.getScope().equals(scope)) {
				result.add(mhd);
			}
		}

		return result;
	}

	public Collection<MethodHeaderDeclaration> getAllMethods(String scope) {
		Collection<MethodHeaderDeclaration> result = new LinkedList<MethodHeaderDeclaration>();
		Class<?> c = null;

		try {
			c = typeTable.getJavaClass(scope);

		} catch (ClassNotFoundException e) {

			return result;
		}

		for (MethodHeaderDeclaration mhd : methods) {

			try {

				if (typeTable.getJavaClass(mhd.getScope()).isAssignableFrom(c)) {
					result.add(mhd);
				}
			} catch (ClassNotFoundException e) {
				// Do nothing. Analyze the next item.
			}

		}
		return result;
	}

	public MethodHeaderDeclaration get(String scope, String method,
			String[] args) {
		if (args == null) {
			List<String> emptyList = Collections.emptyList();
			return get(scope, method, emptyList);
		}
		return get(scope, method, Arrays.asList(args));

	}

	public MethodHeaderDeclaration get(String scope, String method,
			List<String> args) {
		String key = method;
		if (scope != null && !"".equals(scope.trim())) {
			key = scope + "#" + key;
		}

		Collection<MethodHeaderDeclaration> value = dictionary.get(key);
		if (value == null) {
			return null;
		} else if (value.size() == 1) {
			return value.iterator().next();
		} else {

			for (MethodHeaderDeclaration md : value) {
				List<Parameter> margs = md.getArgs();
				Iterator<String> it = args.iterator();
				Iterator<Parameter> it2 = margs.iterator();
				boolean equals = true;
				// la comparacion se hace por igualdad porque este diccionario
				// se usa en target donde no tengo el classpath
				while (it.hasNext() && it2.hasNext() && equals) {

					Parameter tp = it2.next();
					equals = it.next().equals(typeTable.valueOf(tp.getType()));
				}
				if (equals) {
					return md;
				}
			}
			return null;
		}
	}

	public boolean contains(String scope, String method, List<String> args) {
		return get(scope, method, args) != null;
	}

	public boolean contains(String scope, String method, String[] args) {
		return get(scope, method, args) != null;
	}

	@Override
	public int size() {
		return methods.size();
	}

	@Override
	public boolean isEmpty() {
		return methods.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return methods.contains(o);
	}

	@Override
	public Iterator<MethodHeaderDeclaration> iterator() {
		return methods.iterator();
	}

	@Override
	public Object[] toArray() {
		return methods.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return methods.toArray(a);
	}

	@Override
	public boolean add(MethodHeaderDeclaration e) {
		return methods.add(e);
	}

	@Override
	public boolean remove(Object o) {
		return methods.remove(o);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return methods.containsAll(c);
	}

	@Override
	public boolean addAll(Collection<? extends MethodHeaderDeclaration> c) {

		return methods.addAll(c);
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		return methods.removeAll(c);
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		return methods.retainAll(c);
	}

	@Override
	public void clear() {
		methods.clear();
	}

	public TypeTable getTypeTable() {
		return typeTable;
	}

	public void setTypeTable(TypeTable typeTable) {
		this.typeTable = typeTable;
	}

}
