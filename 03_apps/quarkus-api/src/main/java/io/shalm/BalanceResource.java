package io.shalm;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/balance")
@Produces(MediaType.APPLICATION_JSON)
public class BalanceResource {

    @Inject
    AccountService accountService;

    @GET
    @Path("/{id}")
    public Response getBalance(@PathParam("id") String id) {
        Integer balance = accountService.getBalance(id);
        if (balance == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Account not found: " + id))
                    .build();
        }
        return Response.ok(Map.of("id", id, "balance", balance)).build();
    }
}
