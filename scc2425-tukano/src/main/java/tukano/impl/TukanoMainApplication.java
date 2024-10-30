package tukano.impl;

import jakarta.ws.rs.core.Application;
import tukano.impl.rest.RestBlobsResource;
import tukano.impl.rest.RestShortsResource;
import tukano.impl.rest.RestUsersResource;
import tukano.impl.rest.TukanoRestServer;
import utils.Args;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

public class TukanoMainApplication extends Application
{
	final private static Logger Log = Logger.getLogger(TukanoMainApplication.class.getName());

	private Set<Object> singletons = new HashSet<>();
	private Set<Class<?>> resources = new HashSet<>();

	public static String serverURI;

	public TukanoMainApplication() {
		resources.add(RestBlobsResource.class);
		resources.add(RestUsersResource.class);
		resources.add(RestShortsResource.class);

		Token.setSecret("token");

		Log.info(String.format("Tukano Server ready @ %s\n",  serverURI));
	}

	@Override
	public Set<Object> getSingletons() {
		return singletons;
	}

	@Override
	public Set<Class<?>> getClasses() { return resources; }

	public static void main(String[] args) throws Exception {
		return;
	}
}
