package AzureSetUp;

import utils.CosmosDatabase;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

public class AzureProperties
{
	private static Logger Log = Logger.getLogger(AzureProperties.class.getName());
	private static AzureProperties instance = null;
	private static final boolean BLOB_STORAGE_ENABLED = true;
	private static final boolean USERS_COSMOSDB_ENABLED = true;
	private static final boolean SHORTS_STORAGE_ENABLED = true;
	private static final boolean CACHE_ENABLED = true;
	private static final boolean BLOB_SECONDARY_REGION = true;
	private static final boolean EUROPE_REGION = true;

	private static String BLOB_CONNECTION_EUROPE = "DefaultEndpointsProtocol=https;AccountName=sto60019northeurope;AccountKey=A6A5VpnMLIpsgKmk0r4x7CdmFyJFh6UpBucBr1/AF5ZDQb2ympyrKw2Ycodf+Apz13mu7aN+HveF+AStWXf5/Q==;EndpointSuffix=core.windows.net";
	private static String BLOB_CONNECTION_US = "DefaultEndpointsProtocol=https;AccountName=sto60019northcentralus;AccountKey=p7krD91OtnLCZ5303VXZtW/kw8hwA6o7GYxAzd0zp2JU9+ead0pjYeSLfgQ+vTMiEb6O1m08eud3+AStQf4jgg==;EndpointSuffix=core.windows.net";

	private static String COSMOS_KEY = "UxOzFdRhP967DVajvb1skwRUeCHjsPqPcVBa5tzkfNmTyfhtNp1U5Uw782duFnthExgZvAEtWy95ACDbW6zNtQ==";
	private static String COSMOS_URL = "https://cosmos60019.documents.azure.com:443/";
	private static String COSMOS_DATABASE = "cosmosdb60019";

	private static String REDIS_KEY_EUROPE = "Rij5s5vXGLCuXX6V4pdArQdXBw9Pz6OQGAzCaFCVg8w=";
	private static String REDIS_URL_EUROPE = "redis60019northeurope.redis.cache.windows.net";

	private static String REDIS_KEY_US = "jGNb12KuXiPthhmHfZyQY1BfYHv1EsR9hAzCaCKSPoc=";
	private static String REDIS_URL_US = "redis60019northcentralus.redis.cache.windows.net";




	public static final String PROPS_FILE = "azurekeys-northeurope.props";
	private static Properties props;

	public static synchronized Properties getProperties()  {
		if (props == null) {
			try (InputStream input = AzureProperties.class.getClassLoader().getResourceAsStream(PROPS_FILE)) {
				if (input != null) {
					props.load(input);
					Log.info("Properties loaded successfully.");
				} else {
					Log.info("Properties file not found in classpath: " + PROPS_FILE);
				}
			}
			catch (Exception e) {
				Log.info("Failed to load properties file: " + PROPS_FILE);
			}

		}
		return props;
	}


	public static AzureProperties getInstance() {
		if(instance == null) {
			instance = new AzureProperties();
		}
		return instance;
	}

	public AzureProperties() {
		getProperties();
	}

	public static String getBlobKey() {
		//return props.getProperty(BLOB_KEY);
		if(EUROPE_REGION) {
			return BLOB_CONNECTION_EUROPE;
		}
		return BLOB_CONNECTION_US;
	}

	public static String getSecondaryBlobKey() {
		if(EUROPE_REGION) {
			return BLOB_CONNECTION_US;
		}
		return BLOB_CONNECTION_EUROPE;
	}

	public static String getCacheKey() {
		if(EUROPE_REGION) {
			return BLOB_CONNECTION_EUROPE;
		}
		return BLOB_CONNECTION_US;
	}

	public static String getCacheUrl() {
		if(EUROPE_REGION) {
			return REDIS_URL_EUROPE;
		}
		return REDIS_URL_US;
	}

	public static String getCosmosDBKey() {
		//return props.getProperty(COSMOSDB_KEY);
		return COSMOS_KEY;
	}

	public static String getCosmosDBUrl() {
		//return props.getProperty(COSMOSDB_URL);
		return COSMOS_URL;
	}

	public static String getCosmosDBDatabase() {
		//return props.getProperty(COSMOSDB_DATABASE);
		return COSMOS_DATABASE;
	}

	public static boolean isBlobStorageEnabled() {
		return BLOB_STORAGE_ENABLED;
	}

	public static boolean isUsersCosmosDBEnabled() {
		return USERS_COSMOSDB_ENABLED;
	}

	public static boolean isShortsStorageEnabled() {
		return SHORTS_STORAGE_ENABLED;
	}

	public static boolean isCacheEnabled() {
		return CACHE_ENABLED;
	}

	public static boolean isBlobSecondaryRegionEnabled() {
		return BLOB_SECONDARY_REGION;
	}

}
