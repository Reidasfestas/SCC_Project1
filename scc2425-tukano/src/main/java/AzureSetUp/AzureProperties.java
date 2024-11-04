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
//	private static final String BLOB_KEY = "BlobStoreConnection";
//	private static final String COSMOSDB_KEY = "COSMOSDB_KEY";
//	private static final String COSMOSDB_URL = "COSMOSDB_URL";
//	private static final String COSMOSDB_DATABASE = "COSMOSDB_DATABASE";
	private static final boolean BLOB_STORAGE_ENABLED = true;
	private static final boolean USERS_COSMOSDB_ENABLED = true;
	private static final boolean SHORTS_STORAGE_ENABLED = true;
	private static final boolean CACHE_ENABLED = false;
	private static final boolean BLOB_SECONDARY_REGION = false;


	private static String BLOB_CONNECTION = "DefaultEndpointsProtocol=https;AccountName=sto60019northeurope;AccountKey=Mhg9RjX9gEJjdv+PTyp25kOb4pcGaxfy9HbSlQSiZMAr35lFv/A7EzDjII5q9KF4cPIkksc/y9ll+ASt4MYNhw==;EndpointSuffix=core.windows.net";
	private static String COSMOS_KEY = "XhgB52VcbKdVCrxzYYweRZztYcgVqjpbF1k7ToVB4TN5lg18LoExJXbyyycDXW9Xv1MGp0d1dr9mACDb74vQcw==";
	private static String COSMOS_URL = "https://cosmos60019.documents.azure.com:443/";
	private static String COSMOS_DATABASE = "cosmosdb60019";

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
		return BLOB_CONNECTION;
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
