package io.shalm;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Collection;
import java.util.Map;

@Path("/accounts")
@Produces(MediaType.APPLICATION_JSON)
public class AccountsResource {

    @Inject
    AccountService accountService;

    @GET
    public Collection<Account> getAllAccounts() {
        return accountService.getAllAccounts();
    }

    @GET
    @Path("/{id}")
    public Response getAccount(@PathParam("id") String id) {
        Account account = accountService.getAccount(id);
        if (account == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Account not found: " + id))
                    .build();
        }
        return Response.ok(account).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createAccount(CreateAccountRequest req) {
        if (req == null || req.id == null || req.owner == null || req.bank == null || req.initialBalance < 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "id, owner, bank, and initialBalance >= 0 are required"))
                    .build();
        }
        try {
            Account account = accountService.createAccount(req.id.trim(), req.owner.trim(), req.bank.trim(), req.initialBalance);
            return Response.status(Response.Status.CREATED).entity(account).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response deleteAccount(@PathParam("id") String id) {
        boolean deleted = accountService.deleteAccount(id);
        if (!deleted) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Account not found: " + id))
                    .build();
        }
        return Response.noContent().build();
    }
}
