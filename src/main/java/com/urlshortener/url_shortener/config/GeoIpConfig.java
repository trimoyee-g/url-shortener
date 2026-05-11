package com.urlshortener.url_shortener.config;

import com.maxmind.geoip2.DatabaseReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Configuration
public class GeoIpConfig {

    @Bean
    public DatabaseReader geoIpDatabaseReader() throws IOException {
        InputStream stream = new ClassPathResource("GeoLite2-Country.mmdb").getInputStream();
        return new DatabaseReader.Builder(stream).build();
    }
}