package com.example.wxnotion.image.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.wxnotion.image.mapper.ThemeMapper;
import com.example.wxnotion.image.model.Theme;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ThemeService {

    private final ThemeMapper themeMapper;

    @Value("${image.thumbnail-dir:/tmp/image-thumbnails}")
    private String thumbnailDir;

    @Value("${image.thumbnail-base-url:http://localhost:8080/thumbnails}")
    private String thumbnailBaseUrl;

    public Page<Theme> listThemes(int page, int size, String sort, String q) {
        QueryWrapper<Theme> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(q)) {
            wrapper.like("name", q);
        }
        if ("created_at".equals(sort)) {
            wrapper.orderByDesc("created_at");
        } else {
            wrapper.orderByDesc("star_count");
        }
        return themeMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public Theme getById(Long id) {
        return themeMapper.selectById(id);
    }

    public Theme create(Theme theme, String thumbnailBase64) {
        if (StringUtils.hasText(thumbnailBase64)) {
            try {
                theme.setThumbnailUrl(saveThumbnail(thumbnailBase64));
            } catch (Exception e) {
                log.warn("缩略图保存失败，继续发布: {}", e.getMessage());
            }
        }
        if (!StringUtils.hasText(theme.getAuthorName())) {
            theme.setAuthorName("匿名");
        }
        theme.setStarCount(0);
        theme.setIsBuiltin(false);
        theme.setCreatedAt(LocalDateTime.now());
        themeMapper.insert(theme);
        return theme;
    }

    public void star(Long id) {
        Theme theme = themeMapper.selectById(id);
        if (theme != null) {
            theme.setStarCount(theme.getStarCount() + 1);
            themeMapper.updateById(theme);
        }
    }

    private static final int MAX_THUMBNAIL_BYTES = 5 * 1024 * 1024; // 5 MB

    private String saveThumbnail(String base64) throws Exception {
        if (base64.length() > MAX_THUMBNAIL_BYTES * 4 / 3 + 100) {
            throw new IllegalArgumentException("缩略图超过最大允许大小 5MB");
        }
        String data = base64.contains(",") ? base64.substring(base64.indexOf(',') + 1) : base64;
        byte[] bytes = Base64.getDecoder().decode(data);
        if (bytes.length > MAX_THUMBNAIL_BYTES) {
            throw new IllegalArgumentException("缩略图超过最大允许大小 5MB");
        }

        File dir = new File(thumbnailDir);
        if (!dir.exists()) dir.mkdirs();

        String filename = UUID.randomUUID() + ".jpg";
        try (FileOutputStream fos = new FileOutputStream(new File(dir, filename))) {
            fos.write(bytes);
        }
        return thumbnailBaseUrl + "/" + filename;
    }
}
