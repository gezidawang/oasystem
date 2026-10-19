package com.employee.config;

import com.employee.utils.AesUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 加密初始化配置（Phase 0-4）。
 * 读取 application.yml 的 aes.key，调用 AesUtil.init 派生 AES-256 密钥。
 * 生产环境应使用 Jasypt 加密该属性值（ENC(...)），此处仅开发明文。
 */
@Configuration
public class EncryptConfig {

    @Value("${aes.key:}")
    private String aesKey;

    @PostConstruct
    public void init() {
        if (aesKey != null && !aesKey.isBlank()) {
            AesUtil.init(aesKey);
        }
    }
}
