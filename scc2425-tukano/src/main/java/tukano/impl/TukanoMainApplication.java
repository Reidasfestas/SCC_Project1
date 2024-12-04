package tukano.impl;

import jakarta.ws.rs.core.Application;
import tukano.impl.auth.RequestCookies;
import tukano.impl.auth.RequestCookiesCleanupFilter;
import tukano.impl.rest.RestBlobsResource;
import tukano.impl.rest.RestShortsResource;
import tukano.impl.rest.RestUsersResource;
import utils.Authentication;

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

		resources.add(RequestCookies.class);
		resources.add(RequestCookiesCleanupFilter.class);
		resources.add(Authentication.class);

		Token.setSecret("token");

		serverURI = "https://scc-tukano-60019-backend.azurewebsites.net";

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
