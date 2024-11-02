package utils;

import com.azure.cosmos.*;
import com.azure.cosmos.models.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import com.azure.cosmos.util.CosmosPagedIterable;
import com.fasterxml.jackson.databind.ObjectMapper;
import tukano.api.Result;
import tukano.api.Short;
import tukano.api.User;
import tukano.impl.JavaUsers;
import org.hibernate.Session;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;

public class CosmosDatabase implements Database {

    private static Logger Log = Logger.getLogger(CosmosDatabase.class.getName());

    private static final String CONNECTION_URL = "https://cosmos60019.documents.azure.com:443/"; // replace with your own
    private static final String DB_KEY = "93uOJS9hdqnvdRHVJA4yyXEBY3SUv2LUSRBCyldRcQeQRrME1ECv0BQ7EWtkhv4RAgH1Tx8LpD7cACDboOyh4w==";
    private static final String DB_NAME = "cosmosdb60019";

    private Map<Class<?>, CosmosContainer> containerMap = new HashMap<>();

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

    public CosmosDatabase(CosmosClient client) {
        this.client = client;
        init();
    }

    public void changeContainer(String containerName) {
        CONTAINER = containerName;
    }

    private synchronized void init() {
        if( db != null)
            return;
        db = client.getDatabase(DB_NAME);
        containerMap.put(User.class, db.getContainer("users"));
        containerMap.put(Short.class, db.getContainer("shorts"));
        containerMap.put(Likes.class, db.getContainer("likes"));
        containerMap.put(Following.class, db.getContainer("followings"));
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
            container = containerMap.get(clazz);
            if (container == null) {
                Log.info("No container found for class: " + clazz);
            }
            var res = container.queryItems(query, new CosmosQueryRequestOptions(), clazz);
            return res.stream().toList();
        });
    }

    @Override
    public <T> List<T> sql(Class<T> clazz, String fmt, Object... args) {
        container = containerMap.get(clazz);
        if (container == null) {
            Log.info("No container found for class: " + clazz);
        }
        var res = container.queryItems(String.format(fmt, args), new CosmosQueryRequestOptions(),clazz);
        return res.stream().toList();
    }

    @Override
    public <T> Result<T> getOne(String id, Class<T> clazz) {
        container = containerMap.get(clazz);
        if (container == null) {
            Log.info("No container found for class: " + clazz);
        }
        return tryCatch( () -> container.readItem(id, new PartitionKey(id), clazz).getItem());
    }

    @Override
    public <T> Result<T> deleteOne(T obj) {
        return tryCatch(() -> {
            CosmosContainerName annotation = obj.getClass().getAnnotation(CosmosContainerName.class);
            // Check if the annotation exists
            if (annotation == null) {
                Log.info("No CosmosContainerName annotation found for class: " + obj.getClass());
            }

            // Get the container name from the annotation value
            String containerName = annotation.value();

            // Retrieve the Cosmos container by name
            container = db.getContainer(containerName);

            // Perform the delete operation on the Cosmos DB container
            container.deleteItem(obj, new CosmosItemRequestOptions());
            // Return the deleted object as part of the Result
            return obj;
        });
    }

    @Override
    public <T> Result<T> updateOne(T obj) {
        CosmosContainerName annotation = obj.getClass().getAnnotation(CosmosContainerName.class);
        // Check if the annotation exists
        if (annotation == null) {
            Log.info("No CosmosContainerName annotation found for class: " + obj.getClass());
        }

        // Get the container name from the annotation value
        String containerName = annotation.value();

        // Retrieve the Cosmos container by name
        container = db.getContainer(containerName);
        return tryCatch( () -> container.upsertItem(obj).getItem());
    }

    @Override
    public <T> Result<T> insertOne(T obj) {
        Log.info(("Trying to create item: " + obj));
        CosmosContainerName annotation = obj.getClass().getAnnotation(CosmosContainerName.class);
        // Check if the annotation exists
        if (annotation == null) {
            Log.info("No CosmosContainerName annotation found for class: " + obj.getClass());
        }

        // Get the container name from the annotation value
        String containerName = annotation.value();

        // Retrieve the Cosmos container by name
        container = db.getContainer(containerName);

        // Insert the item into the determined container
        return tryCatch(() -> container.createItem(obj).getItem());
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
