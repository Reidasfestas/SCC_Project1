package utils;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import tukano.api.Result;
import org.hibernate.Session;

import javax.xml.crypto.Data;

public interface Database {
    DbTypes getDbType();
    void configure(Database db);
    void configureHibernateDB();
    void configureCosmosDB();
    void changeContainer(String containerName);
    <T> List<T> sql(String query, Class<T> clazz);
    <T> List<T> sql(Class<T> clazz, String fmt, Object... args);
    <T> Result<T> getOne(String id, Class<T> clazz);
    <T> Result<T> deleteOne(T obj);
    <T> Result<T> updateOne(T obj);
    <T> Result<T> insertOne(T obj);
    <T> Result<T> transaction(Consumer<Session> c);
    <T> Result<T> transaction(Function<Session, Result<T>> func);
}
