package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.*;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.ok;

import java.util.List;
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

	private static final boolean REDISCACHE = false;
	private static Jedis jedis;
	private static final String MOST_RECENT_USERS_LIST = "MostRecentUsers";
	private static final int MAX_CACHED_USERS = 10;

	synchronized public static Users getInstance() {
		if( instance == null )
			instance = new JavaUsers();
		return instance;
	}

	//TODO resolve redis cache
	private JavaUsers() {
		DB.configureHibernateDB();

		if (false)
			jedis = RedisCache.getCachePool().getResource();
	}

	@Override
	public Result<String> createUser(User user) {
		Log.info(() -> format("createUser : %s\n", user));

		if( badUserInfo( user ) )
			return error(BAD_REQUEST);

		var res = DB.insertOne( user );
		if (false) {
			if (res.isOK())
				cacheUser(res.value().getUserId(), JSON.encode(res.value()));
		}

		return errorOrValue( res, user.getUserId() );
	}

	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info(() -> format("getUser : userId = %s\n", userId));

		if (userId == null) return error(BAD_REQUEST);

		if (false) {
			try {
				var userJson = jedis.get("user:" + userId);
				if (userJson != null) {
					User user = JSON.decode(userJson, User.class);
					return validatedUserOrError(ok(user), pwd);
				}
			} catch (Exception e) {
				Log.info("Redis cache lookup failed: " + e.getMessage());
				// Optionally, handle cache failure by falling back to DB
			}

			// Fallback to DB if Redis cache fails or user not in cache
			var res = DB.getOne(userId, User.class);
			if (res.isOK()) cacheUser(res.value().getUserId(), JSON.encode(res.value()));
			return validatedUserOrError(res, pwd);
		}

		return validatedUserOrError(DB.getOne(userId, User.class), pwd);
	}


	@Override
	public Result<User> updateUser(String userId, String pwd, User other) {
		Log.info(() -> format("updateUser : userId = %s, user: %s\n", userId, other));

		if (badUpdateUserInfo(userId, pwd, other)) return error(BAD_REQUEST);

		var res = validatedUserOrError(DB.getOne(userId, User.class), pwd);
		if (false) {
			try {
				if (res.isOK()) cacheUser(res.value().getUserId(), JSON.encode(other));
			} catch (Exception e) {
				Log.info("Failed to update cache for user: " + userId + ". Error: " + e.getMessage());
			}
		}

		return errorOrResult(res, user -> DB.updateOne(user.updateFrom(other)));
	}


	@Override
	public Result<User> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s\n", userId));

		if (userId == null || pwd == null) return error(BAD_REQUEST);

		return errorOrResult(validatedUserOrError(DB.getOne(userId, User.class), pwd), user -> {
			JavaBlobs.getInstance().deleteAllBlobs(userId, pwd);
			JavaShorts.getInstance().deleteAllShorts(userId, pwd);

			var res = DB.deleteOne(user);
			if (false) {
				try {
					deleteCachedUser(res.value().getUserId(), JSON.encode(res.value()));
				} catch (Exception e) {
					Log.info("Failed to delete cache for user: " + userId + ". Error: " + e.getMessage());
				}
			}
			return res;
		});
	}


	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info(() -> format("searchUsers : pattern = %s\n", pattern));

		if (false) {
			try {
				var cachedHits = jedis.get("searchUsers:" + pattern.toUpperCase());
				if (cachedHits != null) {
					List<User> res = JSON.decode(cachedHits, new TypeReference<>() {});
					return ok(res);
				}
			} catch (Exception e) {
				Log.info("Failed to retrieve search results from cache for pattern: " + pattern + ". Error: " + e.getMessage());
			}
		}

		if (pattern == null || pattern.trim().isEmpty()) {
			var query = "SELECT * FROM User";
			var hits = DB.sql(query, User.class)
					.stream()
					.map(User::copyWithoutPassword)
					.toList();
			return ok(hits);
		}

		var query = format("SELECT * FROM User u WHERE UPPER(u.id) LIKE '%%%s%%'", pattern.toUpperCase());
		var hits = DB.sql(query, User.class)
				.stream()
				.map(User::copyWithoutPassword)
				.toList();

		if (false) {
			try {
				jedis.setex("searchUsers:" + pattern.toUpperCase(), 60, JSON.encode(hits));
			} catch (Exception e) {
				Log.info("Failed to cache search results for pattern: " + pattern + ". Error: " + e.getMessage());
			}
		}

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
		try {
			jedis.set("user:" + userId, value);
			jedis.lpush(MOST_RECENT_USERS_LIST, value);
			var toTrim = jedis.lrange(MOST_RECENT_USERS_LIST, MAX_CACHED_USERS, -1);
			if (!toTrim.isEmpty()) {
				jedis.ltrim(MOST_RECENT_USERS_LIST, 0, MAX_CACHED_USERS - 1);
				jedis.del("user:" + JSON.decode(toTrim.get(0), User.class).getUserId());
			}
		} catch (Exception e) {
			Log.info("Failed to cache user data for userId: " + userId + ". Error: " + e.getMessage());
		}
	}

	private void deleteCachedUser(String userId, String value) {
		try {
			jedis.del("user:" + userId);
			jedis.lrem(MOST_RECENT_USERS_LIST, 0, value);
		} catch (Exception e) {
			Log.info("Failed to delete cache for userId: " + userId + ". Error: " + e.getMessage());
		}
	}

}
