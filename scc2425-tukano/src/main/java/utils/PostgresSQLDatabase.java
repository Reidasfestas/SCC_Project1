package utils;

import com.azure.cosmos.*;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import jakarta.transaction.Transaction;
import org.hibernate.Session;
import org.hibernate.engine.spi.SessionDelegatorBaseImpl;
import tukano.api.Result;
import tukano.api.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

//TODO: need a schema.sql???

public class PostgresSQLDatabase implements Database {

    private static Logger Log = Logger.getLogger(PostgresSQLDatabase.class.getName());

    private static final String DB_USERNAME = "db.username";
    private static final String DB_PASSWORD = "db.password";
    private static final String DB_URL = "db.url";

    private static PostgresSQLDatabase instance;
    private Connection connection;

    private CosmosClient client;
    private CosmosDatabase db;
    private CosmosContainer container;

    public static synchronized PostgresSQLDatabase getInstance() {
        if (instance == null) {
            instance = new PostgresSQLDatabase();
        }
        return instance;
    }

    private synchronized void init() {
        if( db != null)
            return;
    }

    private PostgresSQLDatabase() {
        try {
            connection = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void close() throws SQLException {
        connection.close();
    }

    @Override
    public DbTypes getDbType() {
        return null;
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
    public void changeContainer(String containerName) {

    }

    @Override
    public <T> List<T> sql(String query, Class<T> clazz) {
        List<T> results = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                // Assume clazz has a constructor that matches the SQL result columns
                T obj = clazz.getDeclaredConstructor(ResultSet.class).newInstance(rs);
                results.add(obj);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
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

        if (obj.getClass() == User.class)
            return (Result<T>) tryCatch( () -> insertUser((User) obj));

        return Result.error(Result.ErrorCode.INTERNAL_ERROR);
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

    private <T> Result<T> insertUser(User user) {
        PreparedStatement insertStatement = null;
        try {
            insertStatement = connection
                    .prepareStatement("INSERT INTO user (user_id,pwd,email,displayName)  VALUES (?, ?, ?, ?);");

            insertStatement.setString(1, user.getUserId());
            insertStatement.setString(2, user.getPwd());
            insertStatement.setString(3, user.getEmail());
            insertStatement.setString(4, user.getDisplayName());

            insertStatement.executeUpdate();
        }
        catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return (Result<T>) Result.ok(user);
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
