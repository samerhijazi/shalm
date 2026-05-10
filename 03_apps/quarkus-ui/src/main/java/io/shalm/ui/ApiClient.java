package io.shalm.ui;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@RegisterRestClient(configKey = "api")
@Path("/")
public interface ApiClient {

    @GET
    @Path("/accounts")
    @Produces(MediaType.APPLICATION_JSON)
    List<AccountInfo> getAllAccounts();

    @GET
    @Path("/balance/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    BalanceResponse getBalance(@PathParam("id") String id);

    @POST
    @Path("/transfer")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    TransferResponse transfer(TransferRequest req);

    @POST
    @Path("/accounts")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    AccountInfo createAccount(CreateAccountRequest req);

    @DELETE
    @Path("/accounts/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    Response deleteAccount(@PathParam("id") String id);
}
