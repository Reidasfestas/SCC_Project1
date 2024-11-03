package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.*;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

import tukano.api.Blobs;
import tukano.api.Result;
import tukano.api.Short;
import tukano.api.User;
import tukano.impl.rest.TukanoRestServer;
import tukano.impl.storage.AzureBlobStorage;
import tukano.impl.storage.BlobStorage;
import tukano.impl.storage.FilesystemStorage;
import utils.DB;
import utils.Hash;
import utils.Hex;
import utils.JSON;

public class JavaBlobs implements Blobs {

	private static Blobs instance;
	private static final Logger Log = Logger.getLogger(JavaBlobs.class.getName());
	private static final boolean AZURE_STORAGE = true;

	public String baseURI;
	private BlobStorage storage;

	synchronized public static Blobs getInstance() {
		if( instance == null )
			instance = new JavaBlobs();
		return instance;
	}

	private JavaBlobs() {
		if(AZURE_STORAGE) {
			storage = new AzureBlobStorage();
			baseURI = String.format("%s/%s/", TukanoMainApplication.serverURI, Blobs.NAME);
		}
		else {
			storage = new FilesystemStorage();
			baseURI = String.format("%s/%s/", TukanoRestServer.serverURI, Blobs.NAME);
		}
	}

	@Override
	public Result<Void> upload(String blobId, byte[] bytes, String token) {
		Log.info(() -> format("upload : blobId = %s, sha256 = %s, token = %s\n", blobId, Hex.of(Hash.sha256(bytes)), token));

		if (!validBlobId(blobId, token))
			return error(FORBIDDEN);

		return storage.write( toPath( blobId ), bytes);
	}

	@Override
	public Result<byte[]> download(String blobId, String token) {
		Log.info(() -> format("download : blobId = %s, token=%s\n", blobId, token));

		if( ! validBlobId( blobId, token ) )
			return error(FORBIDDEN);

		return storage.read( toPath( blobId ) );
	}

	@Override
	public Result<Void> downloadToSink(String blobId, Consumer<byte[]> sink, String token) {
		Log.info(() -> format("downloadToSink : blobId = %s, token = %s\n", blobId, token));

		if( ! validBlobId( blobId, token ) )
			return error(FORBIDDEN);

		return storage.read( toPath(blobId), sink);
	}

	@Override
	public Result<Void> delete(String blobId, String token) {
		Log.info(() -> format("delete : blobId = %s, token=%s\n", blobId, token));

		if( ! validBlobId( blobId, token ) )
			return error(FORBIDDEN);

		return storage.delete( toPath(blobId));
	}

	@Override
	public Result<Void> deleteAllBlobs(String userId, String pwd) {
		Log.info(() -> format("deleteAllBlobs : userId = %s\n", userId));
		return errorOrResult( okUser(userId, pwd), user -> {
			return storage.deleteAll( userId );
		});
	}

	private boolean validBlobId(String blobId, String token) {
		System.out.println( toURL(blobId));
		return Token.isValid(token, toURL(blobId));
	}

	private String toPath(String blobId) {
		return blobId.replace("+", "/");
	}

	private String toURL( String blobId ) {
		return baseURI + blobId ;
	}

	protected Result<User> okUser(String userId, String pwd) {
		return JavaUsers.getInstance().getUser(userId, pwd);
	}
}
