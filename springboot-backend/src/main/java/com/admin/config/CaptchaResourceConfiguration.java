package com.admin.config;

import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.resource.FontCache;
import cloud.tianai.captcha.resource.ResourceStore;
import cloud.tianai.captcha.resource.common.model.dto.Resource;
import cloud.tianai.captcha.resource.common.model.dto.ResourceMap;
import cloud.tianai.captcha.resource.impl.provider.ClassPathResourceProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static cloud.tianai.captcha.common.constant.CommonConstant.DEFAULT_SLIDER_IMAGE_TEMPLATE_PATH;

@Component
@RequiredArgsConstructor
public class CaptchaResourceConfiguration {

    private final ResourceStore resourceStore;


    private static final String[] OPTIONS = {
            "SLIDER", "WORD_IMAGE_CLICK", "ROTATE", "CONCAT"
    };

    @PostConstruct
    public void init() {
        // 添加自定义背景图片
        for (int i = 1; i <= 10; i++) {
            ResourceMap template = new ResourceMap("default", 4);
            template.put("active.png", new Resource(ClassPathResourceProvider.NAME, "slide/" + i + "/1.png"));
            template.put("fixed.png", new Resource(ClassPathResourceProvider.NAME, "slide/" + i + "/2.png"));
            resourceStore.addTemplate(CaptchaTypeConstant.SLIDER, template);
        }


        // 添加自定义背景图片
        for (String option : OPTIONS) {
            for (int i = 1; i <= 26; i++) {
                resourceStore.addResource(option, new Resource("classpath", "bgimages/" + i + ".jpg", "default"));
            }
        }


        //添加自定义字体
        resourceStore.addResource(FontCache.FONT_TYPE, new Resource("classpath", "fonts/SIMSUN.TTC", "default"));
    }
}