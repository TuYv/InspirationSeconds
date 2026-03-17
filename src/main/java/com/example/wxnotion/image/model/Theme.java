package com.example.wxnotion.image.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("themes")
public class Theme {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String description;
    private String css;
    private String previewMd;
    private String authorName;
    private String thumbnailUrl;
    private Integer starCount;
    private Boolean isBuiltin;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
