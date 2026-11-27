package com.codeit.sb13.playlist_for_everyone.global.websocket.watching.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

@Configuration
public class WatcherScriptConfig {

    static public final String DELIMITER = ";";
    static public final Integer TTL_TIMEOUT = 60;

    private RedisScript<Long> load(String path){
        return RedisScript.of(
                new ClassPathResource(path),
                Long.class
        );
    }

    @Bean("lua-script-register")
    public RedisScript<Long> watcherRegisterScript(){
        return load("scripts/watching/wch_register.lua");
    }
    @Bean("lua-script-unregister")
    public RedisScript<Long> watcherUnregisterScript(){
        return load("scripts/watching/wch_unregister.lua");
    }
    @Bean("lua-script-unregister-all")
    public RedisScript<Long> watcherUnregisterAllScript(){
        return load("scripts/watching/wch_unregister_all.lua");
    }
    @Bean("lua-script-delete-with-contentId")
    public RedisScript<Long> watcherDeleteWithContentIdScript(){
        return load("scripts/watching/wch_unregister_content.lua");
    }

    @Bean("lua-script-refresh")
    public RedisScript<Long> watcherRefreshScript(){
        return load("scripts/watching/wch_refresh.lua");
    }

    @Bean("lua-script-count")
    public RedisScript<Long> watcherCountScript(){
        return load("scripts/watching/wch_count.lua");
    }

    @Bean("lua-count-multiple-contents")
    public RedisScript<List> watcherCountMultipleContentsScript(){
        return RedisScript.of(
                new ClassPathResource("scripts/watching/wch_count_all.lua"),
                List.class
        );
    }
    @Bean("lua-script-query-current-viewer")
    public RedisScript<List> watcherCurrentViewerCountScript(){
        return RedisScript.of(
                new ClassPathResource("scripts/watching/wch_query_viewer_by_content.lua"),
                List.class
        );
    }

    @Bean("lua-script-query-viewer")
    public RedisScript<List> watcherQueryViewerScript(){
        return RedisScript.of(
                new ClassPathResource("scripts/watching/wch_query_viewer_by_user.lua"),
                List.class
        );
    }

}
