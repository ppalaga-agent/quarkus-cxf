package io.quarkiverse.cxf.client.it;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import org.jboss.eap.quickstarts.wscalculator.calculator.CalculatorService;

import io.quarkiverse.cxf.annotation.CXFClient;

@Path("/cxf/url-annotation-client")
public class UrlAnnotationClientRestResource {

    @CXFClient(value = "urlAnnotationCalculator", url = "${cxf.it.calculator.baseUri}/calculator-ws/CalculatorService") // <1>
    CalculatorService urlAnnotationCalculator;

    @GET
    @Path("/add")
    @Produces(MediaType.TEXT_PLAIN)
    public int add(@QueryParam("a") int a, @QueryParam("b") int b) {
        return urlAnnotationCalculator.add(a, b);
    }

}
