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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.walkmod.conf.entities.ChainConfig;
import org.walkmod.conf.entities.Configuration;
import org.walkmod.conf.entities.TransformationConfig;
import org.walkmod.conf.entities.WalkerConfig;
import org.walkmod.conf.entities.impl.ChainConfigImpl;
import org.walkmod.conf.entities.impl.TransformationConfigImpl;
import org.walkmod.conf.entities.impl.WalkerConfigImpl;
import org.walkmod.javalang.visitors.VoidVisitor;
import org.walkmod.refactor.visitors.MethodRefactor;
import org.walkmod.walkers.VisitorContext;

public class RefactorConfigurationController {

   private ChainConfigImpl createChainConfig(Configuration conf, String chainName, ChainConfig currentCC,
         Map<String, String> refactoringRules, Map<Method, VoidVisitor<?>> refactoringVisitors) {
      Collection<ChainConfig> chains = conf.getChainConfigs();
      ChainConfigImpl cc = new ChainConfigImpl();
      cc.setName(chainName);
      cc.setReaderConfig(currentCC.getReaderConfig());
      WalkerConfig wc = new WalkerConfigImpl();
      WalkerConfig currentWC = currentCC.getWalkerConfig();
      wc.setParserConfig(currentWC.getParserConfig());
      wc.setType(currentWC.getType());
      wc.setRootNamespace(currentWC.getRootNamespace());
      List<TransformationConfig> transformations = new LinkedList<TransformationConfig>();
      TransformationConfig tc = new TransformationConfigImpl();
      tc.setType(MethodRefactor.class.getName());

      Map<String, Object> parameters = new HashMap<String, Object>();
      
      parameters.put("refactoringRules", refactoringRules);
      parameters.put("refactoringVisitors", refactoringVisitors);
      tc.setParameters(parameters);
      transformations.add(tc);
      wc.setTransformations(transformations);
      cc.setWalkerConfig(wc);
      cc.setWriterConfig(currentCC.getWriterConfig());
      LinkedList<ChainConfig> newChains = new LinkedList<ChainConfig>(chains);
      newChains.add(cc);
      conf.setChainConfigs(newChains);

      return cc;
   }

   private ChainConfig findChainConfig(String chainName, VisitorContext ctx) {
      Configuration conf = ctx.getArchitectureConfig().getConfiguration();
      Collection<ChainConfig> chains = conf.getChainConfigs();

      Iterator<ChainConfig> it = chains.iterator();
      ChainConfig cc = null;
      while (it.hasNext() && cc == null) {
         ChainConfig next = it.next();
         String name = next.getName();
         if (chainName.equals(name)) {
            cc = next;
         }
      }
      return cc;
   }

   private TransformationConfig findTransformationConfig(ChainConfig cc) {
      List<TransformationConfig> transformations = cc.getWalkerConfig().getTransformations();
      Iterator<TransformationConfig> itTransf = transformations.iterator();
      TransformationConfig tc = null;

      while (itTransf.hasNext() && tc == null) {
         TransformationConfig next = itTransf.next();
         if (MethodRefactor.class.getName().equals(next.getType())) {
            tc = next;
         }
      }
      return tc;
   }

   public Map<String, String> getMethodRefactorRules(VisitorContext ctx) {
      ChainConfig currentCC = ctx.getArchitectureConfig();
      Map<String, String> refactoringRules = new HashMap<String, String>();
      if (currentCC.getName() == null) {
         currentCC.setName("default");
      }
      String chainName = currentCC.getName() + "_refactor_gen";
      Configuration conf = ctx.getArchitectureConfig().getConfiguration();

      ChainConfig cc = findChainConfig(chainName, ctx);

      if (cc == null) {
         cc = createChainConfig(conf, chainName, currentCC, refactoringRules, new HashMap<Method, VoidVisitor<?>>());

      } else {

         TransformationConfig tc = findTransformationConfig(cc);

         if (tc != null) {
            Map<String, Object> parameters = tc.getParameters();
            refactoringRules = (Map<String, String>) parameters.get("refactoringRules");
         } else {
            List<TransformationConfig> transformations = cc.getWalkerConfig().getTransformations();
            tc = new TransformationConfigImpl();
            tc.setType(MethodRefactor.class.getName());
            Map<String, Object> parameters = new HashMap<String, Object>();
            
            parameters.put("refactoringRules", refactoringRules);
            tc.setParameters(parameters);
            transformations.add(tc);
         }

      }
      return refactoringRules;
   }

   public Map<Method, VoidVisitor<?>> getRefactoringVisitors(VisitorContext ctx) {
      ChainConfig currentCC = ctx.getArchitectureConfig();
      Map<Method, VoidVisitor<?>> refactoringVisitors = new HashMap<Method, VoidVisitor<?>>();
      if (currentCC.getName() == null) {
         currentCC.setName("default");
      }
      String chainName = currentCC.getName() + "_refactor_gen";
      Configuration conf = ctx.getArchitectureConfig().getConfiguration();

      ChainConfig cc = findChainConfig(chainName, ctx);

      if (cc == null) {
         cc = createChainConfig(conf, chainName, currentCC, new HashMap<String, String>(), refactoringVisitors);

      } else {

         TransformationConfig tc = findTransformationConfig(cc);

         if (tc != null) {
            Map<String, Object> parameters = tc.getParameters();
            refactoringVisitors = (Map<Method, VoidVisitor<?>>) parameters.get("refactoringVisitors");
         } else {
            List<TransformationConfig> transformations = cc.getWalkerConfig().getTransformations();
            tc = new TransformationConfigImpl();
            tc.setType(MethodRefactor.class.getName());
            Map<String, Object> parameters = new HashMap<String, Object>();

            parameters.put("refactoringVisitors", refactoringVisitors);
            tc.setParameters(parameters);
            transformations.add(tc);
         }

      }
      return refactoringVisitors;
   }
}
