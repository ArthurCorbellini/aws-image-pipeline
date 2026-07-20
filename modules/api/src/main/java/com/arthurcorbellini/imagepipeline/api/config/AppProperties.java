package com.arthurcorbellini.imagepipeline.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(S3 s3, Sqs sqs) {

  public record S3(String bucketName) {
  }

  public record Sqs(String queueUrl) {
  }
}
