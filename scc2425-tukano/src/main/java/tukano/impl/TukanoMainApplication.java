package tukano.impl;

import jakarta.ws.rs.core.Application;
import tukano.impl.rest.RestBlobsResource;
import tukano.impl.rest.RestShortsResource;
import tukano.impl.rest.RestUsersResource;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

public class TukanoMainApplication extends Application
{
	final private static Logger Log = Logger.getLogger(TukanoMainApplication.class.getName());

	private Set<Object> singletons = new HashSet<>();

	public static String serverURI;

	public TukanoMainApplication() {
		singletons.add(new RestBlobsResource());
		singletons.add(new RestUsersResource());
		singletons.add(new RestShortsResource());

		Token.setSecret("blabla");

		Log.info(String.format("Tukano Server ready @ %s\n",  serverURI));
	}

	@Override
	public Set<Object> getSingletons() {
		return singletons;
	}
}
