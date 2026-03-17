package com.example.wxnotion.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${image.thumbnail-dir:/tmp/image-thumbnails}")
    private String thumbnailDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/thumbnails/**")
                .addResourceLocations("file:" + thumbnailDir + "/");
    }
}
