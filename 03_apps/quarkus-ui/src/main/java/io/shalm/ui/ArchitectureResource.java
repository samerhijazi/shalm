package io.shalm.ui;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/architecture")
public class ArchitectureResource {

    @Inject
    Template architecture;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance index() {
        return architecture.instance();
    }
}
