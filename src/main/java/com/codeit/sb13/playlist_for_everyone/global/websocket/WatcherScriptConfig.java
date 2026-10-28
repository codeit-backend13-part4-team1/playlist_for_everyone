package com.codeit.sb13.playlist_for_everyone.global.websocket;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class WatcherScriptConfig {

    static public final String DELIMITER = "-";
    static public final Integer TTL_TIMEOUT = 60;

    private RedisScript<Long> load(String path){
        return RedisScript.of(
                new ClassPathResource(path),
                Long.class
        );
    }

    @Bean("lua-script-register")
    public RedisScript<Long> watcherRegisterScript(){
        return load("scripts/wch_reg.lua");
    }

    @Bean("lua-script-unregister")
    public RedisScript<Long> watcherUnregisterScript(){
        return load("scripts/wch_urg.lua");
    }

    @Bean("lua-script-refresh")
    public RedisScript<Long> watcherRefreshScript(){
        return load("scripts/wch_rfs.lua");
    }

    @Bean("lua-script-delete")
    public RedisScript<Long> watcherDeleteScript(){
        return load("scripts/wch_del.lua");
    }

    @Bean("lua-script-count")
    public RedisScript<Long> watcherCountScript(){
        return load("scripts/wch_cnt.lua");
    }


}
