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

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import redis.clients.jedis.Jedis;
import tukano.api.Blobs;
import tukano.api.Result;
import tukano.api.Short;
import tukano.api.Shorts;
import tukano.api.User;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoRestServer;
import utils.DB;
import utils.JSON;
import utils.RedisCache;

public class JavaShorts implements Shorts {

	private static Logger Log = Logger.getLogger(JavaShorts.class.getName());

	private static Shorts instance;

	private static final boolean COSMOS_DB = false;
	private static final String CONTAINER_NAME = "shorts";

	private static final boolean REDISCACHE = false;
	private static Jedis jedis;
	private static final String MOST_RECENT_SHRTS_LIST = "MostRecentShrts";
	private static final int MAX_CACHED_SHRTS = 10;

	synchronized public static Shorts getInstance() {
		if( instance == null )
			instance = new JavaShorts();
		return instance;
	}

	private JavaShorts() {
		if(COSMOS_DB) {
			DB.configureCosmosDB();
			DB.changeContainerName(CONTAINER_NAME);
		}
		else {
			DB.configureHibernateDB();
		}
		if (REDISCACHE)
			jedis = RedisCache.getCachePool().getResource();
	}


	@Override
	public Result<Short> createShort(String userId, String password) {
		Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

		return errorOrResult( okUser(userId, password), user -> {

			var shortId = format("%s+%s", userId, UUID.randomUUID());
			var blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId);
			var shrt = new Short(shortId, userId, blobUrl);

			var res = DB.insertOne(shrt);
			if (REDISCACHE) {
				if (res.isOK())
					cacheShrt(res.value().getShortId(), JSON.encode(res.value()));
			}

			return errorOrValue(res, s -> s.copyWithLikes_And_Token(0));
		});
	}

	@Override
	public Result<Short> getShort(String shortId) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		if( shortId == null )
			return error(BAD_REQUEST);

		//TODO: handle LikeCount with cache
		var query = format("SELECT count(*) FROM Likes l WHERE l.shortId = '%s'", shortId);
		var likes = DB.sql(query, Long.class);

		if(REDISCACHE) {
			var shrtRes = JSON.decode( jedis.get("shrt:" + shortId), Short.class);
			if (shrtRes != null)
				return  errorOrValue( Result.ok(shrtRes), shrt -> shrt.copyWithLikes_And_Token( likes.get(0)));

			var res = DB.getOne(shortId, Short.class);
			if (res.isOK()) {
				cacheShrt(res.value().getShortId(), JSON.encode(res.value()));
			}
			return errorOrValue(res, shrt -> shrt.copyWithLikes_And_Token( likes.get(0)));
		}

		return errorOrValue( getOne(shortId, Short.class), shrt -> shrt.copyWithLikes_And_Token( likes.get(0)));
	}


	@Override
	public Result<Void> deleteShort(String shortId, String password) {
		Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult( getShort(shortId), shrt -> {

			return errorOrResult( okUser( shrt.getOwnerId(), password), user -> {
				return DB.transaction( hibernate -> {

					hibernate.remove( shrt);

					var query = format("DELETE Likes l WHERE l.shortId = '%s'", shortId);
					hibernate.createNativeQuery( query, Likes.class).executeUpdate();

					var res = JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get() );
					if (REDISCACHE && res.isOK())
						deleteCachedShrt(shortId, JSON.encode(res.value()));
				});
			});
		});
	}

	@Override
	public Result<List<String>> getShorts(String userId) {
		Log.info(() -> format("getShorts : userId = %s\n", userId));

		if (REDISCACHE) {
			var cachedHits = jedis.get("getShorts:" + userId);
			if (cachedHits != null) {
				List<String> res = JSON.decode(cachedHits, new TypeReference<>() {});
				return ok(res);
			}
		}

		var query = format("SELECT s.shortId FROM Short s WHERE s.ownerId = '%s'", userId);
		var hits = errorOrValue( okUser(userId), DB.sql( query, String.class));
		if (REDISCACHE)
			jedis.setex("getShorts:" + userId, 60, JSON.encode(hits));
		return hits;
	}

	@Override
	public Result<Void> follow(String userId1, String userId2, boolean isFollowing, String password) {
		Log.info(() -> format("follow : userId1 = %s, userId2 = %s, isFollowing = %s, pwd = %s\n", userId1, userId2, isFollowing, password));


		return errorOrResult( okUser(userId1, password), user -> {
			var f = new Following(userId1, userId2);
			return errorOrVoid( okUser( userId2), isFollowing ? DB.insertOne( f ) : DB.deleteOne( f ));
		});
	}

	@Override
	public Result<List<String>> followers(String userId, String password) {
		Log.info(() -> format("followers : userId = %s, pwd = %s\n", userId, password));

		var query = format("SELECT f.follower FROM Following f WHERE f.followee = '%s'", userId);
		return errorOrValue( okUser(userId, password), DB.sql(query, String.class));
	}

	@Override
	public Result<Void> like(String shortId, String userId, boolean isLiked, String password) {
		Log.info(() -> format("like : shortId = %s, userId = %s, isLiked = %s, pwd = %s\n", shortId, userId, isLiked, password));


		return errorOrResult( getShort(shortId), shrt -> {
			var l = new Likes(userId, shortId, shrt.getOwnerId());
			return errorOrVoid( okUser( userId, password), isLiked ? DB.insertOne( l ) : DB.deleteOne( l ));
		});
	}

	@Override
	public Result<List<String>> likes(String shortId, String password) {
		Log.info(() -> format("likes : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult( getShort(shortId), shrt -> {

			var query = format("SELECT l.userId FROM Likes l WHERE l.shortId = '%s'", shortId);

			return errorOrValue( okUser( shrt.getOwnerId(), password ), DB.sql(query, String.class));
		});
	}

	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

		final var QUERY_FMT = """
				SELECT s.shortId, s.timestamp FROM Short s WHERE	s.ownerId = '%s'				
				UNION			
				SELECT s.shortId, s.timestamp FROM Short s, Following f 
					WHERE 
						f.followee = s.ownerId AND f.follower = '%s' 
				ORDER BY s.timestamp DESC""";

		return errorOrValue( okUser( userId, password), DB.sql( format(QUERY_FMT, userId, userId), String.class));
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

	@Override
	public Result<Void> deleteAllShorts(String userId, String password, String token) {
		Log.info(() -> format("deleteAllShorts : userId = %s, password = %s, token = %s\n", userId, password, token));

		if( ! Token.isValid( token, userId ) )
			return error(FORBIDDEN);

		return DB.transaction( (hibernate) -> {

			//delete shorts
			var query1 = format("DELETE Short s WHERE s.ownerId = '%s'", userId);
			hibernate.createQuery(query1, Short.class).executeUpdate();

			//delete follows
			var query2 = format("DELETE Following f WHERE f.follower = '%s' OR f.followee = '%s'", userId, userId);
			hibernate.createQuery(query2, Following.class).executeUpdate();

			//delete likes
			var query3 = format("DELETE Likes l WHERE l.ownerId = '%s' OR l.userId = '%s'", userId, userId);
			hibernate.createQuery(query3, Likes.class).executeUpdate();

		});
	}

	private void cacheShrt(String shortId, String value) {
		jedis.set("shrt:" + shortId, value);

		jedis.lpush(MOST_RECENT_SHRTS_LIST, value );

		var toTrim = jedis.lrange(MOST_RECENT_SHRTS_LIST, MAX_CACHED_SHRTS, -1);
		if (!toTrim.isEmpty()) {
			jedis.ltrim(MOST_RECENT_SHRTS_LIST, 0, MAX_CACHED_SHRTS - 1);

			jedis.del("shrt:" + JSON.decode(toTrim.get(0), User.class).getUserId());
		}
	}

	private void deleteCachedShrt(String shortId, String value) {
		jedis.del("shrt:" + shortId);

		jedis.lrem(MOST_RECENT_SHRTS_LIST, 0, value);
	}

}