/*
 * Copyright 2015 Martynas Jusevičius <martynas@graphity.org>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.graphity.processor.provider;

import com.hp.hpl.jena.ontology.Ontology;
import com.hp.hpl.jena.rdf.model.InfModel;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Providers;
import org.graphity.core.provider.ModelProvider;
import org.graphity.processor.exception.ConstraintViolationException;
import org.graphity.processor.util.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.topbraid.spin.constraints.ConstraintViolation;

/**
 *
 * @author Martynas Jusevičius <martynas@graphity.org>
 */
public class ValidatingModelProvider extends ModelProvider
{
    private static final Logger log = LoggerFactory.getLogger(ValidatingModelProvider.class);
    
    @Context private Providers providers;    
    
    @Override
    public Model readFrom(Class<Model> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException
    {
        return process(super.readFrom(type, genericType, annotations, mediaType, httpHeaders, entityStream));
    }

    public Model process(Model model)
    {
        return validate(model);
    }
    
    public Model validate(Model model)
    {
        // annotation inheritance requires an inferencing model (mostly for rdfs:subClassOf)
        InfModel infModel = ModelFactory.createInfModel(getOntology().getOntModel().getReasoner(), getOntology().getOntModel(), model);
        List<ConstraintViolation> cvs = new Validator(getOntology().getOntModel()).validate(infModel);
        
	if (!cvs.isEmpty())
        {
            if (log.isDebugEnabled()) log.debug("SPIN constraint violations: {}", cvs);
            throw new ConstraintViolationException(cvs, model);
        }
        
        return model;
    }
        
    public Ontology getOntology()
    {
	ContextResolver<Ontology> cr = getProviders().getContextResolver(Ontology.class, null);
	return cr.getContext(Ontology.class);
    }

    public Providers getProviders()
    {
        return providers;
    }

}
