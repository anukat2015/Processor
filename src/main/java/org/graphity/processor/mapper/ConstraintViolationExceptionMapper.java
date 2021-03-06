/*
 * Copyright 2013 Martynas Jusevičius <martynas@graphity.org>.
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

import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.reasoner.Reasoner;
import com.hp.hpl.jena.reasoner.rulesys.GenericRuleReasoner;
import com.hp.hpl.jena.reasoner.rulesys.Rule;
import com.hp.hpl.jena.vocabulary.RDF;
import java.net.URI;
import java.util.List;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.ext.ExceptionMapper;
import org.graphity.processor.exception.ConstraintViolationException;
import org.graphity.core.util.Link;
import org.graphity.core.vocabulary.G;
import org.graphity.processor.util.RulePrinter;
import org.graphity.processor.vocabulary.GP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Martynas Jusevičius <martynas@graphity.org>
 */
public class ConstraintViolationExceptionMapper extends ExceptionMapperBase implements ExceptionMapper<ConstraintViolationException>
{
    private static final Logger log = LoggerFactory.getLogger(ConstraintViolationExceptionMapper.class);
        
    @Override
    public Response toResponse(ConstraintViolationException cve)
    {
        Resource exception = toResource(cve, Response.Status.BAD_REQUEST,
            ResourceFactory.createResource("http://www.w3.org/2011/http-statusCodes#BadRequest"));
        cve.getModel().add(exception.getModel());
        
        Link classLink = new Link(URI.create(getMatchedOntClass().getURI()), RDF.type.getLocalName(), null);
        Link ontologyLink = new Link(URI.create(getOntology().getURI()), GP.ontology.getURI(), null);
        Link baseUriLink = new Link(getUriInfo().getBaseUri(), G.baseUri.getURI(), null);
        
        ResponseBuilder builder = org.graphity.core.model.impl.Response.fromRequest(getRequest()).
            getResponseBuilder(cve.getModel(), getVariants()).
                status(Response.Status.BAD_REQUEST).
                header("Link", classLink.toString()).
                header("Link", ontologyLink.toString()).
                header("Link", baseUriLink.toString());

        Reasoner reasoner = getOntology().getOntModel().getSpecification().getReasoner();
        if (reasoner instanceof GenericRuleReasoner)
        {
            List<Rule> rules = ((GenericRuleReasoner)reasoner).getRules();
            builder.header("Rules", RulePrinter.print(rules));
        }
        
        return builder.build();
    }
    
}
