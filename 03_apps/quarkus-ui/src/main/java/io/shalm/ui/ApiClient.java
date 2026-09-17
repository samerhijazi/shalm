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

    @GET
    @Path("/tests")
    @Produces(MediaType.APPLICATION_JSON)
    List<TestSummary> getApiTestResults();

    @GET
    @Path("/fabric/blocks")
    @Produces(MediaType.APPLICATION_JSON)
    BlocksResponse getBlocks(@QueryParam("limit") int limit);

    @GET
    @Path("/fabric/blocks/{number}")
    @Produces(MediaType.APPLICATION_JSON)
    BlockDetail getBlock(@PathParam("number") long number);

    @GET
    @Path("/fabric/network-status")
    @Produces(MediaType.APPLICATION_JSON)
    NetworkStatus getNetworkStatus();

    @GET
    @Path("/consistency")
    @Produces(MediaType.APPLICATION_JSON)
    ConsistencyReport getConsistency();
}
