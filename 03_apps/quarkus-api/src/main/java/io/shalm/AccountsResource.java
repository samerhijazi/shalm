package io.shalm;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
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
}
