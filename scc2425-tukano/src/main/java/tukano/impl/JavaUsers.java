package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.*;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.ok;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import redis.clients.jedis.Jedis;
import tukano.api.Result;
import tukano.api.User;
import tukano.api.Users;
import utils.DB;
import utils.JSON;
import utils.RedisCache;

public class JavaUsers implements Users {

	private static Logger Log = Logger.getLogger(JavaUsers.class.getName());

	private static Users instance;

	private static final boolean COSMOS_DB = false;
	private static final String CONTAINER_NAME = "users";

	private static final boolean REDISCACHE = false;
	private static Jedis jedis;
	private static final String MOST_RECENT_USERS_LIST = "MostRecentUsers";
	private static final int MAX_CACHED_USERS = 10;

	synchronized public static Users getInstance() {
		if( instance == null )
			instance = new JavaUsers();
		return instance;
	}

	private JavaUsers() {
		if(COSMOS_DB) {
			DB.configureNoSQLCosmosDB();
			DB.changeContainerName(CONTAINER_NAME);
		}
		else {
			DB.configureHibernateDB();
		}
		if (REDISCACHE)
			jedis = RedisCache.getCachePool().getResource();
	}

	@Override
	public Result<String> createUser(User user) {
		Log.info(() -> format("createUser : %s\n", user));

		if( badUserInfo( user ) )
			return error(BAD_REQUEST);

		var res = DB.insertOne( user );
		if (REDISCACHE) {
			if (res.isOK())
				cacheUser(res.value().getUserId(), JSON.encode(res.value()));
		}

		return errorOrValue( res, user.getUserId() );
	}

	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info( () -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null)
			return error(BAD_REQUEST);

		if(REDISCACHE) {
			var user = jedis.get("user:" + userId);
			if (user != null)
				return  validatedUserOrError( ok(JSON.decode(user, User.class)), pwd);

			var res = DB.getOne(userId, User.class);
			if (res.isOK())
				cacheUser(res.value().getUserId(), JSON.encode(res.value()));

			return validatedUserOrError(res, pwd);
		}

		return validatedUserOrError( DB.getOne( userId, User.class), pwd);
	}

	@Override
	public Result<User> updateUser(String userId, String pwd, User other) {
		Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

		if (badUpdateUserInfo(userId, pwd, other))
			return error(BAD_REQUEST);

		var res = validatedUserOrError(DB.getOne( userId, User.class), pwd);
		if (REDISCACHE) {
			if (res.isOK())
				cacheUser(res.value().getUserId(), JSON.encode(res.value()));
		}

		return errorOrResult( res, user -> DB.updateOne( user.updateFrom(other)));
	}

	@Override
	public Result<User> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null )
			return error(BAD_REQUEST);

		return errorOrResult( validatedUserOrError(DB.getOne( userId, User.class), pwd), user -> {

			// Delete user shorts and related info asynchronously in a separate thread
			Executors.defaultThreadFactory().newThread( () -> {
				JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
				JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
			}).start();

			var res = DB.deleteOne( user);
			if (REDISCACHE && res.isOK())
				deleteCachedUser(res.value().getUserId(), JSON.encode(res.value()));
			return res;
		});
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info( () -> format("searchUsers : patterns = %s\n", pattern));

		if (REDISCACHE) {
			var cachedHits = jedis.get("searchUsers:" + pattern.toUpperCase());
			if (cachedHits != null) {
				List<User> res = JSON.decode(cachedHits, new TypeReference<>() {});
				return ok(res);
			}
		}

		var query = format("SELECT * FROM User u WHERE UPPER(u.userId) LIKE '%%%s%%'", pattern.toUpperCase());
		var hits = DB.sql(query, User.class)
				.stream()
				.map(User::copyWithoutPassword)
				.toList();

		if (REDISCACHE)
			jedis.setex("searchUsers:" + pattern.toUpperCase(), 60, JSON.encode(hits));

		return ok(hits);
	}


	private Result<User> validatedUserOrError( Result<User> res, String pwd ) {
		if( res.isOK())
			return res.value().getPwd().equals( pwd ) ? res : error(FORBIDDEN);
		else
			return res;
	}

	private boolean badUserInfo( User user) {
		return (user.userId() == null || user.pwd() == null || user.displayName() == null || user.email() == null);
	}

	private boolean badUpdateUserInfo( String userId, String pwd, User info) {
		return (userId == null || pwd == null || info.getUserId() != null && ! userId.equals( info.getUserId()));
	}

	private void cacheUser(String userId, String value) {
		jedis.set("user:" + userId, value);

		jedis.lpush(MOST_RECENT_USERS_LIST, value );

		var toTrim = jedis.lrange(MOST_RECENT_USERS_LIST, MAX_CACHED_USERS, -1);
		if (!toTrim.isEmpty()) {
			jedis.ltrim(MOST_RECENT_USERS_LIST, 0, MAX_CACHED_USERS - 1);

			jedis.del("user:" + JSON.decode(toTrim.get(0), User.class).getUserId());
		}
	}

	private void deleteCachedUser(String userId, String value) {
		jedis.del("user:" + userId);

		jedis.lrem(MOST_RECENT_USERS_LIST, 0, value);
	}
}
