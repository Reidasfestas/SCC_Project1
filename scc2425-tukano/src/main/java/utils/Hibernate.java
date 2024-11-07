package utils;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.exception.ConstraintViolationException;

import tukano.api.Result;
import tukano.api.Result.ErrorCode;

/**
 * A helper class to perform POJO (Plain Old Java Objects) persistence, using
 * Hibernate and a backing relational database.
 * 
 * @param <Session>
 */
public class Hibernate {
	private static final String HIBERNATE_CFG_FILE = "hibernate.cfg.xml";
//	private static Logger Log = Logger.getLogger(Hibernate.class.getName());

	private SessionFactory sessionFactory;
	private static Hibernate instance;

	Hibernate() {
		try {
			sessionFactory = new Configuration().configure().buildSessionFactory();
			//sessionFactory = new Configuration().configure(new File(HIBERNATE_CFG_FILE)).buildSessionFactory();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Returns the Hibernate instance, initializing if necessary. Requires a
	 * configuration file (hibernate.cfg.xml)
	 * 
	 * @return
	 */
	synchronized public static Hibernate getInstance() {
		if (instance == null)
			instance = new Hibernate();
		return instance;
	}

	public Result<Void> persistOne(Object  obj) {
		init();
		return execute( (instance) -> {
			instance.persist( obj );
		});
	}

	public <T> Result<T> updateOne(T obj) {
		init();
		return execute( instance -> {
			var res = instance.merge( obj );
			if( res == null)
				return Result.error( ErrorCode.NOT_FOUND );
			
			return Result.ok( res );
		});
	}

	public <T> Result<T> deleteOne(T obj) {
		init();
		return execute( instance -> {
			instance.remove( obj );
			return Result.ok( obj );
		});
	}

	public <T> Result<T> getOne(Object id, Class<T> clazz) {
		init();
		try (var session = sessionFactory.openSession()) {
			var res = session.find(clazz, id);
			if (res == null)
				return Result.error(ErrorCode.NOT_FOUND);
			else
				return Result.ok(res);
		} catch (Exception e) {
			throw e;
		}
	}

	public <T> List<T> sql(String sqlStatement, Class<T> clazz) {
		init();
		try (var session = sessionFactory.openSession()) {
			var query = session.createNativeQuery(sqlStatement, clazz);
			return query.list();
		} catch (Exception e) {
			throw e;
		}
	}

	public <T> Result<T> execute(Consumer<Session> proc) {
		init();
		return execute( (instance) -> {
			proc.accept( instance);
			return Result.ok();
		});
	}

	public <T> Result<T> execute(Function<Session, Result<T>> func) {
		init();
		Transaction tx = null;
		try (var session = sessionFactory.openSession()) {
			tx = session.beginTransaction();
			var res = func.apply( session );
			session.flush();
			tx.commit();
			return res;
		}
		catch (ConstraintViolationException __) {	
			return Result.error(ErrorCode.CONFLICT);
		}  
		catch (Exception e) {
			if( tx != null )
				tx.rollback();
			
			e.printStackTrace();
			throw e;
		}
	}

	private void init() {
		instance = getInstance();
		if(sessionFactory == null) sessionFactory = new Configuration().configure().buildSessionFactory();
	}
}