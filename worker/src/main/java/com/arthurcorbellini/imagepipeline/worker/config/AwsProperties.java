package com.arthurcorbellini.imagepipeline.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws")
public record AwsProperties(String endpointUrl, String region, String accessKeyId, String secretAccessKey) {
}
