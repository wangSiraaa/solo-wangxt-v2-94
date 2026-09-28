package com.example.equity.config;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 演示环境：在没有外部 PostgreSQL 时，进程内启动真实 PostgreSQL（zonky embedded-postgres）。
 * 生产 / 正式环境使用 external profile，连接 application.yml 中配置的外部数据库。
 */
@Configuration
@Profile("embedded")
public class EmbeddedPostgresConfig {

    @Bean(destroyMethod = "close")
    @Primary
    public EmbeddedPostgres embeddedPostgres() throws IOException {
        Path dataDir = Files.createTempDirectory("equity-pg-");
        return EmbeddedPostgres.builder()
                .setDataDirectory(dataDir)
                .setServerConfig("timezone", "UTC")
                .start();
    }

    @Bean
    @Primary
    public DataSource dataSource(EmbeddedPostgres pg) {
        return pg.getPostgresDatabase();
    }
}
