package com.yang;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPooled;

public class RedisUtil {
    private static JedisPooled jedis = new RedisUtil().getConnection();
    private RedisUtil() {
        GenericObjectPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(100);
        config.setMaxIdle(50);
        config.setMinIdle(10);
        config.setTestOnBorrow(true);
        config.setTestOnReturn(true);
        config.setTestWhileIdle(true);
        String password = System.getenv("REDIS_PASSWORD");
        System.out.println(password);
        jedis = new JedisPooled(config, "redis", 6379, 2000, password);
    }
    public static JedisPooled getConnection() {
        return jedis;
    }

    public static void main(String[] args) {
        getConnection().set("test3", "test");
    }
}
