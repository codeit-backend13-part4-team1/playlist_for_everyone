package com.codeit.sb13.playlist_for_everyone.global.config;

import com.codeit.sb13.playlist_for_everyone.domain.conversation.entity.ConversationSortBy;
import com.codeit.sb13.playlist_for_everyone.domain.notification.entity.SortBy;
import com.codeit.sb13.playlist_for_everyone.domain.playlist.entity.PlaylistSortBy;
import com.codeit.sb13.playlist_for_everyone.domain.user.entity.UserSortBy;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    private final MDCLoggingInterceptor mdcLoggingInterceptor;

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, SortBy.class, SortBy::from);
        registry.addConverter(String.class, PlaylistSortBy.class, PlaylistSortBy::from);
        registry.addConverter(String.class, UserSortBy.class, UserSortBy::from);
        registry.addConverter(String.class, ConversationSortBy.class, ConversationSortBy::from);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(mdcLoggingInterceptor);
    }
}
