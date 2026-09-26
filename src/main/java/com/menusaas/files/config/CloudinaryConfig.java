package com.menusaas.files.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.menusaas.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cliente Cloudinary como bean (permite {@code @MockitoBean} en tests).
 *
 * <p>Solo existe si hay credenciales: {@code CLOUDINARY_URL} o el trío
 * {@code CLOUDINARY_CLOUD_NAME + API_KEY + API_SECRET}. Sin credenciales no
 * hay bean y la subida responde 503 (Cloudinary es la única vía de subida).
 */
@Slf4j
@Configuration
public class CloudinaryConfig {

    @Bean
    @ConditionalOnMissingBean(Cloudinary.class)
    @ConditionalOnExpression("'${CLOUDINARY_URL:}' != '' || '${app.cloudinary.url:}' != '' || "
            + "('${app.cloudinary.cloud-name:}' != '' && '${app.cloudinary.api-key:}' != ''"
            + " && '${app.cloudinary.api-secret:}' != '')")
    public Cloudinary cloudinaryClient(AppProperties appProperties) {
        AppProperties.Cloudinary config = appProperties.cloudinary();
        if (config != null && config.url() != null && !config.url().isBlank()) {
            log.info("Cloudinary inicializado desde app.cloudinary.url.");
            return new Cloudinary(config.url());
        }
        log.info("Cloudinary inicializado con cloud_name/api_key/api_secret.");
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", config.cloudName(),
                "api_key", config.apiKey(),
                "api_secret", config.apiSecret(),
                "secure", true));
    }
}
