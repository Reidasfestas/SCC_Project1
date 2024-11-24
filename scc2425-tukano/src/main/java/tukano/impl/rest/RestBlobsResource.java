package tukano.impl.rest;

import jakarta.inject.Singleton;
import jakarta.ws.rs.core.Cookie;
import tukano.api.Blobs;
import tukano.api.rest.RestBlobs;
import tukano.impl.JavaBlobs;
import utils.Authentication;

@Singleton
public class RestBlobsResource extends RestResource implements RestBlobs {

	final Blobs impl;

	final static String ADMIN = "admin";
	final static String USER = "user";

	public RestBlobsResource() {
		this.impl = JavaBlobs.getInstance();
	}
	
	@Override
	public void upload(Cookie cookie, String blobId, byte[] bytes, String token) {
		Authentication.validateSession(cookie, USER);

		super.resultOrThrow( impl.upload(blobId, bytes, token));
	}

	@Override
	public byte[] download(Cookie cookie, String blobId, String token) {
		Authentication.validateSession(cookie, USER);

		return super.resultOrThrow( impl.download( blobId, token ));
	}

	@Override
	public void delete(Cookie cookie, String blobId, String token) {
		Authentication.validateSession(cookie, ADMIN);

		super.resultOrThrow( impl.delete( blobId, token ));
	}
	
	@Override
	public void deleteAllBlobs(Cookie cookie, String userId, String password) {
		Authentication.validateSession(cookie, ADMIN);

		super.resultOrThrow( impl.deleteAllBlobs( userId, password ));
	}
}
