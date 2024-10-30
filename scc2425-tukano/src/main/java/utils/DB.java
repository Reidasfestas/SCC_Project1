package utils;

import tukano.api.Result;
import org.hibernate.Session;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class DB {

	public DB() {

	}

	private Database database;

	public void configure(Database db) {
		database = db;
	}

	public void configureHibernateDB() {
		database = new HibernateDatabase();
	}

	public void configureCosmosDB() {
		database = new CosmosDatabase();
	}

	public void changeContainerName(String containerName) {
		database.changeContainer(containerName);
	}

	public <T> List<T> sql(String query, Class<T> clazz) {
		return database.sql(query, clazz);
	}

	public <T> List<T> sql(Class<T> clazz, String fmt, Object... args) {
		return database.sql(clazz, fmt, args);
	}

	public <T> Result<T> getOne(String id, Class<T> clazz) {
		return database.getOne(id, clazz);
	}

	public <T> Result<T> deleteOne(T obj) {
		return database.deleteOne(obj);
	}

	public <T> Result<T> updateOne(T obj) {
		return database.updateOne(obj);
	}

	public <T> Result<T> insertOne(T obj) {
		return database.insertOne(obj);
	}

	public <T> Result<T> transaction(Consumer<Session> c) {
		return database.transaction(c);
	}

	public <T> Result<T> transaction(Function<Session, Result<T>> func) {
		return database.transaction(func);
	}
}
