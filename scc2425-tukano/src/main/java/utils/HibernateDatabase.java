package utils;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import tukano.api.Result;
import org.hibernate.Session;

public class HibernateDatabase implements Database {

    private static HibernateDatabase instance;
    //private static Hibernate hibernateDB;

    synchronized public static HibernateDatabase getInstance() {
        if (instance == null) {
            instance = new HibernateDatabase();
            //hibernateDB = Hibernate.getInstance();
        }
        return instance;
    }

    @Override
    public DbTypes getDbType() {
        return DbTypes.LOCAL;
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
        return Hibernate.getInstance().sql(query, clazz);
    }

    @Override
    public <T> List<T> sql(Class<T> clazz, String fmt, Object... args) {
        return Hibernate.getInstance().sql(String.format(fmt, args), clazz);
    }

    @Override
    public <T> Result<T> getOne(String id, Class<T> clazz) {
        return Hibernate.getInstance().getOne(id, clazz);
    }

    @Override
    public <T> Result<T> deleteOne(T obj) {
        return Hibernate.getInstance().deleteOne(obj);
    }

    @Override
    public <T> Result<T> updateOne(T obj) {
        return Hibernate.getInstance().updateOne(obj);
    }

    @Override
    public <T> Result<T> insertOne(T obj) {
        return Result.errorOrValue(Hibernate.getInstance().persistOne(obj), obj);
    }

    @Override
    public <T> Result<T> transaction(Consumer<Session> c) {
        return Hibernate.getInstance().execute(c::accept);
    }

    @Override
    public <T> Result<T> transaction(Function<Session, Result<T>> func) {
        return Hibernate.getInstance().execute(func);
    }
}
