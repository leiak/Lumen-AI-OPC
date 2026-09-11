package com.ruoyi.opc.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class CommentCreateRequest implements Serializable {
    @NotNull(message = "moduleId 必填")
    private Long moduleId;

    @NotBlank(message = "content 必填")
    @Size(min = 1, max = 1000)
    private String content;

    private Long parentId;
}
