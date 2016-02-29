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

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.walkmod.javalang.ast.MethodSymbolData;
import org.walkmod.javalang.ast.SymbolData;
import org.walkmod.javalang.ast.body.MethodDeclaration;
import org.walkmod.javalang.ast.body.Parameter;
import org.walkmod.javalang.compiler.reflection.MethodInspector;

public class RefactoringUtils {

   /**
    * Evaluates if the method declaration overrides a more generic definition.
    * @param md The method declaration to analyze.
    * @return is the method declaration overrides a more generic definition.
    */
   public static boolean overrides(MethodDeclaration md) {
      MethodSymbolData msd = md.getSymbolData();

      if (msd != null) {
         Method method = msd.getMethod();
         Class<?> declaringClass = method.getDeclaringClass();
         Class<?> parentClass = declaringClass.getSuperclass();

         if (parentClass != null) {

            // it should be initialized after resolving the method

            List<Parameter> params = md.getParameters();
            SymbolData[] args = null;
            if (params != null) {
               args = new SymbolData[params.size()];
               int i = 0;
               for (Parameter param : params) {
                  args[i] = param.getType().getSymbolData();
                  i++;
               }
            } else {
               args = new SymbolData[0];
            }

            List<Class<?>> scopesToCheck = new LinkedList<Class<?>>();
            scopesToCheck.add(parentClass);
            Class<?>[] interfaces = declaringClass.getInterfaces();
            for (int i = 0; i < interfaces.length; i++) {
               scopesToCheck.add(interfaces[i]);
            }
            Iterator<Class<?>> it = scopesToCheck.iterator();
            boolean found = false;
            while (it.hasNext() && !found) {
               found = (MethodInspector.findMethod(it.next(), args, md.getName()) != null);
            }
            return found;
         }
         return false;
      } else {
         throw new UnsupportedOperationException("This operation cannot be used with a method without symbol data");
      }
   }
}
