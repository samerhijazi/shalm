package io.shalm;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Path("/tests")
@Produces(MediaType.APPLICATION_JSON)
public class TestsResource {

    @GET
    public Response getTestResults() {
        try (InputStream in = getClass().getResourceAsStream("/test-results.json")) {
            if (in == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return Response.ok(json).build();
        } catch (IOException e) {
            return Response.serverError().build();
        }
    }
}
