/**
 *  Copyright 2014 Martynas Jusevičius <martynas@graphity.org>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.graphity.processor.provider;

import com.hp.hpl.jena.ontology.DatatypeProperty;
import com.hp.hpl.jena.ontology.ObjectProperty;
import com.hp.hpl.jena.ontology.OntDocumentManager;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.ontology.OntResource;
import com.hp.hpl.jena.ontology.Ontology;
import com.hp.hpl.jena.reasoner.Reasoner;
import com.hp.hpl.jena.reasoner.rulesys.GenericRuleReasoner;
import com.hp.hpl.jena.reasoner.rulesys.Rule;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;
import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.spi.inject.Injectable;
import com.sun.jersey.spi.inject.PerRequestTypeInjectableProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletConfig;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import org.graphity.core.exception.ConfigurationException;
import org.graphity.processor.exception.SitemapException;
import org.graphity.processor.vocabulary.GP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JAX-RS provider for ontology model.
 * 
 * @author Martynas Jusevičius <martynas@graphity.org>
 * @see com.hp.hpl.jena.ontology.OntModel
 */
@Provider
public class OntologyProvider extends PerRequestTypeInjectableProvider<Context, Ontology> implements ContextResolver<Ontology>
{
    private static final Logger log = LoggerFactory.getLogger(OntologyProvider.class);

    private final ServletConfig servletConfig;
    
    public OntologyProvider(@Context ServletConfig servletConfig)
    {
        super(Ontology.class);
        this.servletConfig = servletConfig;
    }
    
    public ServletConfig getServletConfig()
    {
	return servletConfig;
    }
    
    public class ImportCycleChecker
    {
        private final Map<Ontology, Boolean> marked = new HashMap<>(), onStack = new HashMap<>();
        private Ontology cycleOntology = null;

        public void check(Ontology ontology)
        {
            marked.put(ontology, Boolean.TRUE);
            onStack.put(ontology, Boolean.TRUE);

            ExtendedIterator<OntResource> it = ontology.listImports();
            try
            {
                while (it.hasNext())
                {
                    OntResource importRes = it.next();
                    if (importRes.canAs(Ontology.class))
                    {
                        Ontology imported = importRes.asOntology();
                        if (marked.get(imported) == null)
                            check(imported);
                        else if (onStack.get(imported))
                        {
                            cycleOntology = imported;
                            return;
                        }
                    }
                }

                onStack.put(ontology, Boolean.FALSE);
            }
            finally
            {
                it.close();
            }
        }
        
        public Ontology getCycleOntology()
        {
            return cycleOntology;
        }
        
    }
    
    @Override
    public Injectable<Ontology> getInjectable(ComponentContext cc, Context context)
    {
	//if (log.isDebugEnabled()) log.debug("OntologyProvider UriInfo: {} ResourceConfig.getProperties(): {}", uriInfo, resourceConfig.getProperties());
	
	return new Injectable<Ontology>()
	{
	    @Override
	    public Ontology getValue()
	    {
                return getOntology();
	    }
	};
    }

    @Override
    public Ontology getContext(Class<?> type)
    {
        return getOntology();
    }

    public String getOntologyURI()
    {
        String ontologyURI = getOntologyURI(getServletConfig(), GP.ontology);
        
        if (ontologyURI == null)
        {
            if (log.isErrorEnabled()) log.error("Sitemap ontology URI (" + GP.ontology.getURI() + ") not configured");
            throw new ConfigurationException("Sitemap ontology URI (" + GP.ontology.getURI() + ") not configured");
        }

        return ontologyURI;
    }
    
    public Ontology getOntology()
    {
        return getOntology(getOntologyURI());
    }
    
    public Ontology getOntology(String ontologyURI)
    {
        Ontology ontology = getOntology(getOntModel(ontologyURI, getOntModelSpec(getServletConfig(), GP.sitemapRules)), ontologyURI);
        if (ontology == null)
        {
            if (log.isErrorEnabled()) log.error("Sitemap ontology resource not found; processing aborted");
            throw new ConfigurationException("Sitemap ontology resource not found; processing aborted");
        }

        ImportCycleChecker checker = new ImportCycleChecker();
        checker.check(ontology);
        if (checker.getCycleOntology() != null)
        {
            if (log.isErrorEnabled()) log.error("Sitemap contains an ontology which forms an import cycle: {}", checker.getCycleOntology());
            throw new SitemapException("Sitemap contains an ontology which forms an import cycle: " + checker.getCycleOntology().getURI());
        }
        
        return ontology;
    }
    
    public OntModelSpec getOntModelSpec(List<Rule> rules)
    {
        OntModelSpec ontModelSpec = new OntModelSpec(OntModelSpec.OWL_MEM);
        
        if (rules != null)
        {
            Reasoner reasoner = new GenericRuleReasoner(rules);
            //reasoner.setDerivationLogging(true);
            //reasoner.setParameter(ReasonerVocabulary.PROPtraceOn, Boolean.TRUE);
            ontModelSpec.setReasoner(reasoner);
        }
        
        return ontModelSpec;
    }

    public OntModelSpec getOntModelSpec(ServletConfig servletConfig, DatatypeProperty property)
    {
        return getOntModelSpec(getRules(servletConfig, property));
    }
    
    /**
     * Returns configured sitemap ontology.
     * Uses <code>gp:ontology</code> context parameter value from web.xml as dataset location.
     * 
     * @param ontModel
     * @param ontologyURI
     * @return ontology model
     */
    public Ontology getOntology(OntModel ontModel, String ontologyURI)
    {
        if (ontModel == null) throw new IllegalArgumentException("OntModel cannot be null");
        if (ontologyURI == null) throw new IllegalArgumentException("Ontology URI String cannot be null");
        
        return ontModel.getOntology(ontologyURI);
    }
    
    public String getOntologyURI(ServletConfig servletConfig, ObjectProperty property)
    {
        if (servletConfig == null) throw new IllegalArgumentException("ServletConfig cannot be null");
        if (property == null) throw new IllegalArgumentException("Property cannot be null");

        Object ontologyURI = servletConfig.getInitParameter(property.getURI());
        if (ontologyURI != null) return ontologyURI.toString();

        return null;
    }
    
    /**
     * Reads ontology model from a file.
     * 
     * @param ontologyURI ontology location
     * @param ontModelSpec ontology model specification
     * @return ontology model
     */
    public OntModel getOntModel(String ontologyURI, OntModelSpec ontModelSpec)
    {
        if (ontologyURI == null) throw new IllegalArgumentException("URI cannot be null");
        if (ontModelSpec == null) throw new IllegalArgumentException("OntModelSpec cannot be null");        
        if (log.isDebugEnabled()) log.debug("Loading sitemap ontology from URI: {}", ontologyURI);
        
        OntModel ontModel = OntDocumentManager.getInstance().getOntology(ontologyURI, ontModelSpec);
        if (log.isDebugEnabled()) log.debug("Sitemap model size: {}", ontModel.size());
        
        return ontModel;
    }

    public final List<Rule> getRules(ServletConfig servletConfig, DatatypeProperty property)
    {
        String rules = getRulesString(servletConfig, property);
        if (rules == null) return null;
        
        return Rule.parseRules(rules);
    }

    public String getRulesString(ServletConfig servletConfig, DatatypeProperty property)
    {
        if (servletConfig == null) throw new IllegalArgumentException("ServletConfig cannot be null");
        if (property == null) throw new IllegalArgumentException("Property cannot be null");

        Object rules = servletConfig.getInitParameter(property.getURI());
        if (rules != null) return rules.toString();

        return null;
    }

}