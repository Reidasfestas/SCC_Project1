package utils;

import com.azure.cosmos.*;
import com.azure.cosmos.models.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import com.azure.cosmos.util.CosmosPagedIterable;
import tukano.api.Result;
import tukano.impl.JavaUsers;
import org.hibernate.Session;

public class CosmosDatabase implements Database {

    private static Logger Log = Logger.getLogger(CosmosDatabase.class.getName());

    private static final String CONNECTION_URL = "https://scc60019.documents.azure.com:443/"; // replace with your own
    private static final String DB_KEY = "cIP35jV6oqj6SxjGtgVnGKEconfd2gO21sMBP6kpeZSmTPDXKObB3G4w7M2yHQlw6GApFcYBMFlzACDbmep53Q==";
    private static final String DB_NAME = "scc60019";
    private String CONTAINER = "users";

    private static CosmosDatabase instance;

    private CosmosClient client;
    private com.azure.cosmos.CosmosDatabase db;
    private CosmosContainer container;

    public static synchronized CosmosDatabase getInstance() {
        if( instance != null)
            return instance;

        CosmosClient client = new CosmosClientBuilder()
                .endpoint(CONNECTION_URL)
                .key(DB_KEY)
                //.directMode()
                .gatewayMode()
                // replace by .directMode() for better performance
                .consistencyLevel(ConsistencyLevel.SESSION)
                .connectionSharingAcrossClientsEnabled(true)
                .contentResponseOnWriteEnabled(true)
                .buildClient();
        instance = new CosmosDatabase(client);
        return instance;

    }

//    public CosmosDatabase(String endpoint, String key, String databaseName, String containerName) {
//        this.client = new CosmosClientBuilder()
//                .endpoint(endpoint)
//                .key(key)
//                .consistencyLevel(ConsistencyLevel.EVENTUAL)
//                .buildClient();
//        this.container = client.getDatabase(databaseName).getContainer(containerName);
//    }

    public CosmosDatabase(CosmosClient client) {
        this.client = client;
    }

    public void changeContainer(String containerName) {
        CONTAINER = containerName;
    }

    private synchronized void init() {
        if( db != null)
            return;
        db = client.getDatabase(DB_NAME);
        container = db.getContainer(CONTAINER);
    }

    public void close() {
        client.close();
    }

    @Override
    public DbTypes getDbType() {
        return DbTypes.COSMOS;
    }

    @Override
    public void configure(Database db) {

    }

    @Override
    public void configureHibernateDB() {

    }

    @Override
    public void configureCosmosDB() {

    }

    @Override
    public <T> List<T> sql(String query, Class<T> clazz) {
        return tryCatchList(() -> {
            var res = container.queryItems(query, new CosmosQueryRequestOptions(), clazz);
            return res.stream().toList();
        });
    }

    @Override
    public <T> List<T> sql(Class<T> clazz, String fmt, Object... args) {
        var res = container.queryItems(String.format(fmt, args), new CosmosQueryRequestOptions(),clazz);
        return res.stream().toList();
    }

    @Override
    public <T> Result<T> getOne(String id, Class<T> clazz) {
        return tryCatch( () -> container.readItem(id, new PartitionKey(id), clazz).getItem());
    }

    @Override
    public <T> Result<T> deleteOne(T obj) {
        return tryCatch(() -> {
            // Perform the delete operation on the Cosmos DB container
            container.deleteItem(obj, new CosmosItemRequestOptions());
            // Return the deleted object as part of the Result
            return obj;
        });
    }

    @Override
    public <T> Result<T> updateOne(T obj) {
        return tryCatch( () -> container.upsertItem(obj).getItem());
    }

    @Override
    public <T> Result<T> insertOne(T obj) {
        //Log.info(("Trying to create item: " + obj));
        return tryCatch( () -> container.createItem(obj).getItem());
    }

    @Override
    public <T> Result<T> transaction(Consumer<Session> c) {
        // Cosmos DB doesn't have session transactions in the same sense as SQL
        return Result.error(Result.ErrorCode.INTERNAL_ERROR);
    }

    @Override
    public <T> Result<T> transaction(Function<Session, Result<T>> func) {
        return Result.error(Result.ErrorCode.INTERNAL_ERROR);
    }


    <T> Result<T> tryCatch( Supplier<T> supplierFunc) {
        try {
            init();
            return Result.ok(supplierFunc.get());
        } catch( CosmosException ce ) {
            ce.printStackTrace();
            return Result.error ( errorCodeFromStatus(ce.getStatusCode() ));
        } catch( Exception x ) {
            x.printStackTrace();
            return Result.error( Result.ErrorCode.INTERNAL_ERROR);
        }
    }

    private <T> List<T> tryCatchList(Supplier<List<T>> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            // Log or handle error appropriately
            return new ArrayList<>();  // Return an empty list if an error occurs
        }
    }

    static Result.ErrorCode errorCodeFromStatus( int status ) {
        return switch( status ) {
            case 200 -> Result.ErrorCode.OK;
            case 404 -> Result.ErrorCode.NOT_FOUND;
            case 409 -> Result.ErrorCode.CONFLICT;
            default -> Result.ErrorCode.INTERNAL_ERROR;
        };
    }
}
