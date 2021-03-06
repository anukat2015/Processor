/*
 * Copyright 2014 Martynas Jusevičius <martynas@graphity.org>.
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

package org.graphity.processor.mapper;

import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.ontology.Ontology;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.vocabulary.DCTerms;
import com.hp.hpl.jena.vocabulary.RDF;
import com.sun.jersey.api.core.ResourceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Variant;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.ext.Providers;
import org.graphity.core.MediaTypes;
import org.graphity.processor.vocabulary.HTTP;

/**
 * Abstract base class for ExceptionMappers that build responses with exceptions as RDF resources.
 * 
 * @author Martynas Jusevičius <martynas@graphity.org>
 */
@Provider
abstract public class ExceptionMapperBase
{

    @Context private Request request;
    @Context private Providers providers;
    @Context private UriInfo uriInfo;
    
    public Resource toResource(Exception ex, Response.Status status, Resource statusResource)
    {
	if (ex == null) throw new IllegalArgumentException("Exception cannot be null");
	if (status == null) throw new IllegalArgumentException("Response.Status cannot be null");
	//if (statusResource == null) throw new IllegalArgumentException("Status Resource cannot be null");

        Resource resource = ModelFactory.createDefaultModel().createResource().
                addProperty(RDF.type, HTTP.Response).
                addLiteral(HTTP.statusCodeValue, status.getStatusCode()).
                addLiteral(HTTP.reasonPhrase, status.getReasonPhrase());
                //addLiteral(ResourceFactory.createProperty("http://graphity.org/gm#message"), ex.getStackTrace());

        if (statusResource != null) resource.addProperty(HTTP.sc, statusResource);
        if (ex.getMessage() != null) resource.addLiteral(DCTerms.title, ex.getMessage());
        
        return resource;
    }
    
    public MediaTypes getMediaTypes()
    {
	ContextResolver<MediaTypes> cr = getProviders().getContextResolver(MediaTypes.class, null);
	return cr.getContext(MediaTypes.class);
    }

    public Variant getVariant()
    {
        return getRequest().selectVariant(getVariants());
    }
    
    public List<Variant> getVariants()
    {
        return getVariantListBuilder().add().build();
    }

    public Variant.VariantListBuilder getVariantListBuilder()
    {
        org.graphity.core.model.impl.Response response = org.graphity.core.model.impl.Response.fromRequest(getRequest());
        return response.getVariantListBuilder(getModelMediaTypes(), getLanguages(), getEncodings());
    }
    
    public List<MediaType> getModelMediaTypes()
    {
        return getMediaTypes().getWritable(Model.class);
    }
    
    public List<Locale> getLanguages()
    {
        return new ArrayList<>();
    }

    public List<String> getEncodings()
    {
        return new ArrayList<>();
    }
        
    public Request getRequest()
    {
        return request;
    }
    
    public Providers getProviders()
    {
        return providers;
    }
    
    public OntClass getMatchedOntClass()
    {
	ContextResolver<OntClass> cr = getProviders().getContextResolver(OntClass.class, null);
	return cr.getContext(OntClass.class);
    }

    public Ontology getOntology()
    {
	ContextResolver<Ontology> cr = getProviders().getContextResolver(Ontology.class, null);
	return cr.getContext(Ontology.class);
    }
    
    public UriInfo getUriInfo()
    {
        return uriInfo;
    }
    
}