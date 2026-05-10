package io.shalm.ui;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
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
}
