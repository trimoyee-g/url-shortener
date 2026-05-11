package com.urlshortener.url_shortener.e2e;

import com.urlshortener.url_shortener.integration.BaseIntegrationTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseE2ETest extends BaseIntegrationTest {
}