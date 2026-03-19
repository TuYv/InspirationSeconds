package com.example.wxnotion.image.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.wxnotion.image.model.Theme;
import com.example.wxnotion.image.service.ThemeService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/themes")
@RequiredArgsConstructor
public class ThemeController {

    private final ThemeService themeService;

    @GetMapping
    public Page<Theme> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "star_count") String sort,
            @RequestParam(required = false) String q) {
        return themeService.listThemes(page, Math.min(size, 100), sort, q);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Theme> get(@PathVariable Long id) {
        Theme theme = themeService.getById(id);
        return theme != null ? ResponseEntity.ok(theme) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<Theme> create(@RequestBody @Valid CreateThemeRequest req) {
        Theme theme = new Theme();
        theme.setName(req.getName());
        theme.setDescription(req.getDescription());
        theme.setCss(req.getCss());
        theme.setPreviewMd(req.getPreviewMd());
        theme.setAuthorName(req.getAuthorName());
        return ResponseEntity.status(201).body(themeService.create(theme, req.getThumbnail()));
    }

    @PostMapping("/{id}/star")
    public ResponseEntity<Void> star(@PathVariable Long id) {
        themeService.star(id);
        return ResponseEntity.ok().build();
    }

    @Data
    public static class CreateThemeRequest {
        @NotBlank
        private String name;
        private String description;
        @NotBlank
        private String css;
        private String previewMd;
        private String authorName;
        private String thumbnail;
    }
}
