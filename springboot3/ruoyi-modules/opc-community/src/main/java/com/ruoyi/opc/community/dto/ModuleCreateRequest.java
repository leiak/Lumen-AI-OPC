package com.ruoyi.opc.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class ModuleCreateRequest implements Serializable {
    @NotBlank(message = "code 必填")
    @Size(min = 2, max = 64)
    private String code;

    @NotBlank(message = "name 必填")
    @Size(min = 2, max = 128)
    private String name;

    @NotBlank(message = "category 必填")
    private String category;

    private String description;

    private String icon;

    private String tags;
}
