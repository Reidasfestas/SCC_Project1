package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.errorOrVoid;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

import java.util.List;
import java.util.UUID;
import tukano.api.Blobs;
import tukano.api.Result;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import tukano.api.Short;
import tukano.api.Shorts;
import tukano.api.User;
import tukano.impl.data.Following;
import tukano.impl.data.Likes;
import tukano.impl.rest.TukanoRestServer;
import utils.DB;

public class JavaShorts implements Shorts {

	private static Logger Log = Logger.getLogger(JavaShorts.class.getName());

	private static Shorts instance;

	private static final boolean COSMOS_DB = true;

	synchronized public static Shorts getInstance() {
		if( instance == null )
			instance = new JavaShorts();
		return instance;
	}

	private JavaShorts() {
		if(COSMOS_DB) {
			DB.configureCosmosDB();
		}
		else {
			DB.configureHibernateDB();
		}
	}


	@Override
	public Result<Short> createShort(String userId, String password) {
		Log.info(() -> format("createShort : userId = %s, pwd = %s\n", userId, password));

		return errorOrResult( okUser(userId, password), user -> {

			var shortId = format("%s+%s", userId, UUID.randomUUID());
			String blobUrl;
			if(COSMOS_DB) blobUrl = format("%s/%s/%s", TukanoMainApplication.serverURI, Blobs.NAME, shortId);
			else blobUrl = format("%s/%s/%s", TukanoRestServer.serverURI, Blobs.NAME, shortId);
			var shrt = new Short(shortId, userId, blobUrl);

			return errorOrValue(DB.insertOne(shrt), s -> s.copyWithLikes_And_Token(0));
		});
	}

	@Override
	public Result<Short> getShort(String shortId) {
		Log.info(() -> format("getShort : shortId = %s\n", shortId));

		if( shortId == null )
			return error(BAD_REQUEST);

		var query = format("SELECT l.id FROM Likes l WHERE l.shortId = '%s'", shortId);
		var likes = DB.sql(query, Likes.class);

		long likeCount = (!likes.isEmpty()) ? likes.size() : 0L;

		return errorOrValue( DB.getOne(shortId, Short.class), shrt -> shrt.copyWithLikes_And_Token( likeCount ));
	}


	@Override
	public Result<Void> deleteShort(String shortId, String password) {
		Log.info(() -> format("deleteShort : shortId = %s, pwd = %s\n", shortId, password));

		return errorOrResult(getShort(shortId), shrt -> {

			return errorOrResult(okUser(shrt.getOwnerId(), password), user -> {

				if (COSMOS_DB) {
						var query = format("select l.id FROM Likes l WHERE l.shortId = '%s'", shortId);
						List<Likes> likes = DB.sql(query, Likes.class);

						Log.info("Likes size: " +likes.size());

						for (Likes like : likes) {
							DB.deleteOne(like);
						}
						JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get());
						DB.deleteOne(shrt);
						return Result.ok();

				} else {
					return DB.transaction(hibernate -> {
						hibernate.remove(shrt);

						var query = format("DELETE Likes l WHERE l.shortId = '%s'", shortId);
						hibernate.createNativeQuery(query, Likes.class).executeUpdate();

						JavaBlobs.getInstance().delete(shrt.getBlobUrl(), Token.get());
						return Result.ok(); // Add this to complete the transaction with Hibernate
					});
				}

			});

		});
	}


	@Override
	public Result<List<String>> getShorts(String userId) {
		Log.info(() -> format("getShorts : userId = %s\n", userId));

		var query = format("SELECT s.id FROM Short s WHERE s.ownerId = '%s'", userId);
//		return errorOrValue( okUser(userId), DB.sql( query, String.class));

		List<Short> shorts = DB.sql(query, Short.class);

		List<String> shortIds = shorts.stream()
				.map(Short::getShortId)
				.collect(Collectors.toList());

		return errorOrValue(okUser(userId), shortIds);
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

		List<Following> shorts = DB.sql(query, Following.class);

		List<String> followerIds = shorts.stream()
				.map(Following::getFollower)
				.toList();


		return errorOrValue( okUser(userId, password), followerIds);
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

			List<Likes> likes = DB.sql(query, Likes.class);

			List<String> userIds = likes.stream()
					.map(Likes::getUserId)  // Assuming Short has a getId() method
					.toList();

			//return errorOrValue( okUser( shrt.getOwnerId(), password ), DB.sql(query, String.class));
			return errorOrValue( okUser( shrt.getOwnerId(), password ), userIds );
		});
	}

	@Override
	public Result<List<String>> getFeed(String userId, String password) {
		Log.info(() -> format("getFeed : userId = %s, pwd = %s\n", userId, password));

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


		var myShortsQuery = format("SELECT * FROM Short s WHERE l.ownerId = '%s'", userId);
		List<Short> shorts = DB.sql(myShortsQuery, Short.class);

		var myFollowingQuery = format("SELECT * FROM Following f WHERE f.follower = '%s'", userId);
		List<Following> followings = DB.sql(myFollowingQuery, Following.class);

		for(Following f : followings) {
			var myFollowingShortsQuery = format("SELECT * FROM Short s WHERE s.ownerId = '%s'", f.getFollowee());
			List<Short> followingShorts = DB.sql(myFollowingShortsQuery, Short.class);
			shorts.addAll(followingShorts);
		}


//		List<Short> shorts = DB.sql(format(QUERY_FMT, userId, userId), Short.class);
//
		List<String> feed = shorts.stream()
				.map(s -> String.format("shortId: %s; timestamp:  %s", s.getShortId(), s.getTimestamp()))
				.toList();

		Log.info("Feed size:  " + feed.size());

		//return errorOrValue( okUser( userId, password), DB.sql( format(QUERY_FMT, userId, userId), String.class));
		return errorOrValue( okUser( userId, password), feed);
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


		// Later i need to add this back
//		if( ! Token.isValid( token, userId ) )
//			return error(FORBIDDEN);

		if(COSMOS_DB) {
			var myShortsQuery = format("SELECT * FROM Short s WHERE l.ownerId = '%s'", userId);
			List<Short> shorts = DB.sql(myShortsQuery, Short.class);
			for(Short s : shorts) {
				DB.deleteOne(s);
			}

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

			return Result.ok();
		}
		else {
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

	}

}