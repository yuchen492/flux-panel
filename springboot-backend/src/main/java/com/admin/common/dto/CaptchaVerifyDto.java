package com.admin.common.dto;

import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 验证码验证请求DTO
 */
@Data
public class CaptchaVerifyDto {

    private String id;

    private ImageCaptchaTrack data;

}
