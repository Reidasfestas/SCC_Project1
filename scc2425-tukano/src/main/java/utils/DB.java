package utils;

import tukano.api.Result;
import org.hibernate.Session;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class DB {

	private static Database database;

	public static void configure(Database db) {
		database = db;
	}

	public static void configureHibernateDB() {
		database = new HibernateDatabase();
	}

	public static void configureCosmosDB() {
		database = CosmosDatabase.getInstance();
	}

	public static <T> List<T> sql(String query, Class<T> clazz) {
		return database.sql(query, clazz);
	}

	public static <T> List<T> sql(Class<T> clazz, String fmt, Object... args) {
		return database.sql(clazz, fmt, args);
	}

	public static <T> Result<T> getOne(String id, Class<T> clazz) {
		return database.getOne(id, clazz);
	}

	public static <T> Result<T> deleteOne(T obj, Class<T> clazz) {
		return database.deleteOne(obj, clazz);
	}

	public static <T> Result<T> updateOne(T obj, Class<T> clazz) {
		return database.updateOne(obj, clazz);
	}

	public static <T> Result<T> insertOne(T obj, Class<T> clazz) {
		return database.insertOne(obj, clazz);
	}

	public static <T> Result<T> transaction(Consumer<Session> c) {
		return database.transaction(c);
	}

	public static <T> Result<T> transaction(Function<Session, Result<T>> func) {
		return database.transaction(func);
	}
}
