package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.errorOrVoid;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;
import static utils.DB.getOne;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import AzureSetUp.AzureProperties;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;
import tukano.api.Blobs;
import tukano.api.Result;

import java.util.stream.Collectors;

import tukano.api.Short;
import tukano.api.Shorts;
import tukano.api.User;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import utils.DB;
import utils.JSON;
import utils.RedisCache;

public class JavaShorts implements Shorts {

	private static Logger Log = Logger.getLogger(JavaShorts.class.getName());

	private static Shorts instance;

	private static final boolean COSMOS_DB = false;

	private static final boolean REDISCACHE = false;
	private static Jedis jedis;
	private static final String MOST_RECENT_SHRTS_LIST = "MostRecentShrts";
	private static final String LIKES_PREFIX = "likes:";
	private static final int MAX_CACHED_SHRTS = 10;

	synchronized public static Shorts getInstance() {
		if( instance == null )
			instance = new JavaShorts();
		return instance;
	}

	private JavaShorts() {
		if(AzureProperties.getInstance().isShortsStorageEnabled()) {
			DB.configureCosmosDB();
		}
		else {
			DB.configureHibernateDB();
		}
		if (AzureProperties.getInstance().isCacheEnabled())
			jedis = RedisCache.getCachePool().getResource();
	}

	protected Result<User> okUser( String userId, String pwd) {
		return JavaUsers.getInstance().getUser(userId, pwd);
	}

	private Result<Void> okUser( String userId ) {
		var res = okUser( userId, "");
		if( res.error() == FORBIDDEN )
			return ok();
		else
			return error( res.error() );
	}

	private void cacheShrt(String shortId, String value) {
		try {
			jedis.set("shrt:" + shortId, value);
			jedis.lpush(MOST_RECENT_SHRTS_LIST, value);

			var toTrim = jedis.lrange(MOST_RECENT_SHRTS_LIST, MAX_CACHED_SHRTS, -1);
			if (!toTrim.isEmpty()) {
				jedis.ltrim(MOST_RECENT_SHRTS_LIST, 0, MAX_CACHED_SHRTS - 1);
				jedis.del("shrt:" + JSON.decode(toTrim.get(0), User.class).getUserId());
			}
		} catch (JedisConnectionException e) {
			Log.warning("Redis cache operation failed in cacheShrt: " + e.getMessage());
		}
	}

	private int getLikeCount(String shortId) {
		try {
			String count = jedis.get(LIKES_PREFIX + shortId);
			return count != null ? Integer.parseInt(count) : 0;
		} catch (JedisConnectionException e) {
			Log.warning("Redis cache operation failed in getLikeCount: " + e.getMessage());
			return 0;
		}
	}

	private void deleteCachedShrt(String shortId, String value) {
		try {
			jedis.del("shrt:" + shortId);
			jedis.lrem(MOST_RECENT_SHRTS_LIST, 0, value);
		} catch (JedisConnectionException e) {
			Log.warning("Redis cache operation failed in deleteCachedShrt: " + e.getMessage());
		}
	}


	@Override
	public Result<Short> createShort(String userId, String password) {
		Log.info(() -> format("createShort : userId = %s, pwd = [PROTECTED]\n", userId));

		return errorOrResult(okUser(userId, password), user -> {
			var shortId = format("%s+%s", userId, UUID.randomUUID());
			var blobUrl = format("%s/%s/%s", TukanoMainApplication.serverURI, Blobs.NAME, shortId);
			var shrt = new Short(shortId, userId, blobUrl);

			var res = DB.insertOne(shrt);
			if (AzureProperties.getInstance().isCacheEnabled() && res.isOK()) {
				try {
					cacheShrt(res.value().getShortId(), JSON.encode(res.value()));
				} catch (JedisConnectionException e) {
					Log.info("Failed to cache short in Redis: " + e.getMessage());
				} catch (Exception e) {
					Log.warning("Unexpected error when caching short in Redis: " + e.getMessage());
				}
			}

			return errorOrValue(res, s -> s.copyWithLikes_And_Token(0));
		});
	}



	@Override
	public Result<Short> getShort(String shortId) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		if( shortId == null )
			return error(BAD_REQUEST);

		List<Long> likes;

		if (AzureProperties.getInstance().isCacheEnabled()) {
			try {
				var cachedShrt = jedis.get("shrt:" + shortId);
				if (cachedShrt != null) {
					Short decodedShrt = JSON.decode(cachedShrt, Short.class);
					return ok(decodedShrt.copyWithLikes_And_Token(getLikeCount(shortId)));
				}
			} catch (JedisConnectionException e) {
				Log.warning("Redis cache operation failed in getShort: " + e.getMessage());
			}

			// Fallback to database retrieval if cache is unavailable
			var res = getOne(shortId, Short.class);
			return errorOrValue(res, shrt -> shrt.copyWithLikes_And_Token(getLikeCount(shortId)));
		}



//		if (AzureProperties.getInstance().isCacheEnabled()) {
//			likes = List.of((long) getLikeCount(LIKES_PREFIX + shortId));
//
//			var shrtRes = jedis.get("shrt:" + shortId);
//			if (shrtRes != null)
//				return  errorOrValue( Result.ok(JSON.decode(shrtRes, Short.class)), shrt -> shrt.copyWithLikes_And_Token( likes.get(0)));
//
//			var res = getOne(shortId, Short.class);
//			if (res.isOK()) {
//				cacheShrt(res.value().getShortId(), JSON.encode(res.value()));
//			}
//			return errorOrValue(res, shrt -> shrt.copyWithLikes_And_Token( likes.get(0)));
//		}

		if(AzureProperties.getInstance().isShortsStorageEnabled()) {
			var query = format("SELECT l.id FROM Likes l WHERE l.shortId = '%s'", shortId);
			var likesList = DB.sql(query, Likes.class);

			long likeCount = likesList.size();

			return errorOrValue( DB.getOne(shortId, Short.class), shrt -> shrt.copyWithLikes_And_Token( likeCount ));
		}
		else {
			var query = format("SELECT count(*) FROM Likes l WHERE l.shortId = '%s'", shortId);
			var likesVar = DB.sql(query, Long.class);
			return errorOrValue( getOne(shortId, Short.class), shrt -> shrt.copyWithLikes_And_Token( likesVar.get(0)));
		}


	}


	@Override
	public Result<Void> deleteShort(String shortId, String password) {
		Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult(getShort(shortId), shrt -> {

			return errorOrResult(okUser(shrt.getOwnerId(), password), user -> {

				if (AzureProperties.getInstance().isShortsStorageEnabled()) {
					var query = format("select l.id FROM Likes l WHERE l.shortId = '%s'", shortId);
					List<Likes> likes = DB.sql(query, Likes.class);

					Log.info("Likes size: " + likes.size());

					for (Likes like : likes) {
						var res = DB.deleteOne(like);
						if (AzureProperties.getInstance().isCacheEnabled() && res.isOK()) {
							jedis.decr(LIKES_PREFIX + shortId);
						}
					}

					JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get());
					var res = DB.deleteOne(shrt);

					if (AzureProperties.getInstance().isCacheEnabled() && res.isOK()) {
						deleteCachedShrt(shortId, JSON.encode(res.value()));

						jedis.del(LIKES_PREFIX + shortId);
					}

					return Result.ok();

				} else {
					return DB.transaction(hibernate -> {
						hibernate.remove(shrt);

						var query = format("DELETE FROM Likes l WHERE l.shortId = '%s'", shortId);
						hibernate.createNativeQuery(query, Likes.class).executeUpdate();

						var res = JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get());
						if (AzureProperties.getInstance().isCacheEnabled() && res.isOK()) {
							deleteCachedShrt(shortId, JSON.encode(res.value()));

							jedis.del(LIKES_PREFIX + shortId);
						}
					});
				}
			});
		});
	}


	@Override
	public Result<List<String>> getShorts(String userId) {
		Log.info(() -> format("getShorts : userId = %s\n", userId));

		if (AzureProperties.getInstance().isCacheEnabled()) {
			try {
				var cachedHits = jedis.get("getShorts:" + userId);
				if (cachedHits != null) {
					List<String> res = JSON.decode(cachedHits, new TypeReference<>() {});
					return ok(res);
				}
			}
			catch (Exception e) {
				Log.info("Error in getShorts with message: " + e.getMessage());
			}

		}

		if(AzureProperties.isShortsStorageEnabled()) {
			var query = format("SELECT s.id FROM Short s WHERE s.ownerId = '%s'", userId);
			List<Short> shorts = DB.sql(query, Short.class);

			List<String> shortIds = shorts.stream()
					.map(Short::getShortId)
					.collect(Collectors.toList());

			if (AzureProperties.getInstance().isCacheEnabled())
				jedis.setex("getShorts:" + userId, 60, JSON.encode(shortIds));

			return errorOrValue(okUser(userId), shortIds);
		}
		else {
			var query = format("SELECT s.id FROM Short s WHERE s.ownerId = '%s'", userId);
			return errorOrValue( okUser(userId), DB.sql( query, String.class));
		}
	}

	@Override
	public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
		Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2, isFollowing, password));

		if(userId1 == null || userId2 == null || userId1.equals(userId2)) {
			return error(BAD_REQUEST);
		}

		return errorOrResult( okUser(userId1, password), user -> {
			var f = new Following(userId1, userId2);
			return errorOrVoid( okUser( userId2), isFollowing ? DB.insertOne( f ) : DB.deleteOne( f ));
		});
	}

	@Override
	public Result<List<String>> followers(String userId, String password) {
		Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

		if (AzureProperties.getInstance().isCacheEnabled()) {
			var cachedHits = jedis.get("followers:" + userId);
			if (cachedHits != null) {
				List<String> res = JSON.decode(cachedHits, new TypeReference<>() {});
				return errorOrValue( okUser(userId, password), ok(res));
			}
		}

		return errorOrResult( okUser(userId, password), user -> {

			if(AzureProperties.isShortsStorageEnabled()) {
				var query = format("SELECT f.follower FROM Following f WHERE f.followee = '%s'", userId);

				List<Following> shorts = DB.sql(query, Following.class);

				List<String> followerIds = shorts.stream()
						.map(Following::getFollower)
						.toList();

				if (AzureProperties.getInstance().isCacheEnabled()) jedis.setex("followers:" + userId, 60, JSON.encode(followerIds));

				return errorOrValue( okUser(userId, password), followerIds);
			}
			else {
				var query = format("SELECT f.follower FROM Following f WHERE f.followee = '%s'", userId);
				return errorOrValue( okUser(userId, password), DB.sql(query, String.class));
			}
		});
	}

	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked, password));

		return errorOrResult( getShort(shortId), shrt -> {
			return errorOrResult( okUser(userId, password), user -> {

				var l = new Likes(userId, shortId, shrt.getOwnerId());

				var res = errorOrVoid( okUser( userId, password), isLiked ? DB.insertOne( l ) : DB.deleteOne( l ));

				if (AzureProperties.getInstance().isCacheEnabled()) {
					try {
						if (res.isOK()) {
							if (isLiked)
								jedis.incr(LIKES_PREFIX + shortId);
							else if(jedis.get(LIKES_PREFIX + shortId) != null)
								jedis.decr(LIKES_PREFIX + shortId);
						}
					}
					catch (Exception e) {
						Log.info("Error in Like post with message:  " + e.getMessage());
					}

				}
				return res;
			});
		});
	}

	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult( getShort(shortId), shrt -> {

//			if (AzureProperties.getInstance().isCacheEnabled()) {
//				try {
//					var likes = List.of(String.valueOf(getLikeCount(LIKES_PREFIX + shortId)));
//					return  errorOrValue(okUser( shrt.getOwnerId(), password ), likes);
//				}
//				catch (Exception e) {
//					Log.info("Error in get Likes post with message:  " + e.getMessage());
//				}
//			}

			if(AzureProperties.isShortsStorageEnabled()) {
				return errorOrResult( okUser(shrt.getOwnerId(), password), user -> {

					var query = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);

					List<Likes> likes = DB.sql(query, Likes.class);

					List<String> userIds = likes.stream()
							.map(Likes::getUserId)  // Assuming Short has a getId() method
							.toList();

					return errorOrValue( okUser( shrt.getOwnerId(), password ), userIds );
				});
			}
			else {
				var query = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);
				return errorOrValue( okUser( shrt.getOwnerId(), password ), DB.sql(query, String.class));
			}


		});
	}

	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

		if (AzureProperties.getInstance().isCacheEnabled()) {
			try {
				var cachedHits = jedis.get("getFeed:" + userId);
				if (cachedHits != null) {
					List<String> res = JSON.decode(cachedHits, new TypeReference<>() {});
					return errorOrValue( okUser(userId, password), ok(res));
				}
			}
			catch (Exception e) {
				Log.info("Error in getFeed post with message:  " + e.getMessage());
			}
		}

		return errorOrResult( okUser(userId, password), user -> {
			// Need to do sequential
			// First do a query for my own shorts
			// Then do a query for the the people i follow
			// Then get the shorts of the people i follow
			// Then join my shorts with the ones from the people i follow

			final var QUERY_FMT = """
				SELECT s.id, s.timestamp FROM Short s WHERE	s.ownerId = '%s'				
				UNION			
				SELECT s.id, s.timestamp FROM Short s, Following f 
					WHERE 
						f.followee = s.ownerId AND f.follower = '%s' 
				ORDER BY s.timestamp DESC""";

			var myShortsQuery = format("SELECT * FROM Short s WHERE s.ownerId = '%s'", userId);
			List<Short> shorts = new ArrayList<>(DB.sql(myShortsQuery, Short.class));

			var myFollowingQuery = format("SELECT * FROM Following f WHERE f.follower = '%s'", userId);
			List<Following> followings = DB.sql(myFollowingQuery, Following.class);

			for(Following f : followings) {
				var myFollowingShortsQuery = format("SELECT * FROM Short s WHERE s.ownerId = '%s'", f.getFollowee());
				List<Short> followingShorts = DB.sql(myFollowingShortsQuery, Short.class);
				shorts.addAll(followingShorts);
			}

			List<String> feed = shorts.stream()
					.sorted(Comparator.comparing(Short::getTimestamp).reversed())
					.map(s -> String.format("shortId: %s; timestamp: %s", s.getShortId(), s.getTimestamp()))
					.toList();


			if (AzureProperties.getInstance().isCacheEnabled()) {
				try {
					jedis.setex("getFeed:" + userId, 60, JSON.encode(feed));
				}
				catch (Exception e) {
					Log.info("Error in getFeed post with message:  " + e.getMessage());
				}
			}

			return errorOrValue( okUser( userId, password), feed);
		});
	}

	@Override
	public Result<Void> deleteAllShorts(String userId, String password) {
		Log.info(() -> format("deleteAllShorts : userId = %s\n", userId));

		return errorOrResult( okUser(userId, password), user -> {
            if(AzureProperties.getInstance().isShortsStorageEnabled()) {
				var myFollowingsQuery = format("SELECT * FROM Following f WHERE f.follower = '%s' OR f.followee = '%s'", userId, userId);
				List<Following> followings = DB.sql(myFollowingsQuery, Following.class);
				for(Following f : followings) {
					DB.deleteOne(f);
				}

				var myLikesQuery = format("SELECT * FROM Likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);
				List<Likes> likes = DB.sql(myLikesQuery, Likes.class);
				for(Likes l : likes) {
					DB.deleteOne(l);
				}

				var myShortsQuery = format("SELECT * FROM Short s WHERE s.ownerId = '%s'", userId);
				List<Short> shorts = DB.sql(myShortsQuery, Short.class);

				for(Short s : shorts) {
					var res = DB.deleteOne(s);
					if(AzureProperties.getInstance().isCacheEnabled()) {
						try {
							deleteCachedShrt(s.getShortId(), JSON.encode(res.value()));
						}
						catch (Exception e) {
							Log.info("Error in deleteAllShorts post with message:  " + e.getMessage());
						}
					}
				}

				return Result.ok();
			}
			else {
				return DB.transaction( (hibernate) -> {

					//delete shorts
					var query1 = format("DELETE FROM Short s WHERE s.ownerId = '%s'", userId);
					hibernate.createNativeQuery(query1, Short.class).executeUpdate();

					//delete follows
					var query2 = format("DELETE FROM Following f WHERE f.follower = '%s' OR f.followee = '%s'", userId, userId);
					hibernate.createNativeQuery(query2, Following.class).executeUpdate();

					//delete likes
					var query3 = format("DELETE FROM Likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);
					hibernate.createNativeQuery(query3, Likes.class).executeUpdate();
				});
			}
		});
	}
}