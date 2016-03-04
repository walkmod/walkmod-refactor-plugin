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
import org.walkmod.refactor.visitors.MethodRefactor;
import org.walkmod.walkers.VisitorContext;

public class RefactorConfigurationController {

   public Map<String, String> getMethodRefactorRules(VisitorContext ctx) {
      ChainConfig currentCC = ctx.getArchitectureConfig();
      Map<String, String> refactoringRules = null;
      if (currentCC.getName() == null) {
         currentCC.setName("default");
      }
      String chainName = currentCC.getName() + "_refactor_gen";

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
      if (cc == null) {
         cc = new ChainConfigImpl();
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
         refactoringRules = new HashMap<String, String>();
         parameters.put("refactoringRules", refactoringRules);
         tc.setParameters(parameters);
         transformations.add(tc);
         wc.setTransformations(transformations);
         cc.setWalkerConfig(wc);
         cc.setWriterConfig(currentCC.getWriterConfig());
         LinkedList<ChainConfig> newChains = new LinkedList<ChainConfig>(chains);
         newChains.add(cc);
         conf.setChainConfigs(newChains);
      }
      else{
         List<TransformationConfig> transformations = cc.getWalkerConfig().getTransformations();
         Iterator<TransformationConfig> itTransf = transformations.iterator();
         TransformationConfig tc = null;
         
         while(itTransf.hasNext() && tc == null){
            TransformationConfig next = itTransf.next();
            if("refactor:methods".equals(next.getType())){
               tc = next;
            }
         }
         if(tc != null){
            Map<String, Object> parameters = tc.getParameters();
            refactoringRules = (Map<String, String>) parameters.get("refactoringRules");
         }
         else{
            tc = new TransformationConfigImpl();
            tc.setType("refactor:methods");
            Map<String, Object> parameters = new HashMap<String, Object>();
            refactoringRules = new HashMap<String, String>();
            parameters.put("refactoringRules", refactoringRules);
            tc.setParameters(parameters);
            transformations.add(tc);
         }
         
      }
      return refactoringRules;
   }
}
