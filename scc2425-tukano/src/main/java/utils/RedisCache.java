package utils;

import AzureSetUp.AzureProperties;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisCache {
	//private static final String RedisHostname = "scc2425cache60247.redis.cache.windows.net";
	//private static final String RedisKey = "mdujNtqMGUSBB9PVPMZCbBRugzJhrbrprAzCaH0nOmc=";
	private static final int REDIS_PORT = 6380;
	private static final int REDIS_TIMEOUT = 3000;
	private static final boolean Redis_USE_TLS = true;
	
	private static JedisPool instance;
	
	public synchronized static JedisPool getCachePool() {
		if( instance != null)
			return instance;
		
		var poolConfig = new JedisPoolConfig();
		poolConfig.setMaxTotal(128);
		poolConfig.setMaxIdle(128);
		poolConfig.setMinIdle(16);
		poolConfig.setTestOnBorrow(true);
		poolConfig.setTestOnReturn(true);
		poolConfig.setTestWhileIdle(true);
		poolConfig.setNumTestsPerEvictionRun(3);
		poolConfig.setBlockWhenExhausted(true);
		instance = new JedisPool(poolConfig, AzureProperties.getCacheUrl(), REDIS_PORT, REDIS_TIMEOUT, AzureProperties.getCacheKey(), Redis_USE_TLS);
		return instance;
	}

}
