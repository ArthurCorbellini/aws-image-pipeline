package com.arthurcorbellini.imagepipeline.api.config;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
@EnableConfigurationProperties(AwsProperties.class)
public class AwsClientConfig {

  @Bean
  public S3Client s3Client(AwsProperties props) {
    return S3Client.builder()
        .endpointOverride(URI.create(props.endpointUrl()))
        .region(Region.of(props.region()))
        .credentialsProvider(credentials(props))
        .build();
  }

  @Bean
  public SqsClient sqsClient(AwsProperties props) {
    return SqsClient.builder()
        .endpointOverride(URI.create(props.endpointUrl()))
        .region(Region.of(props.region()))
        .credentialsProvider(credentials(props))
        .build();
  }

  private StaticCredentialsProvider credentials(AwsProperties props) {
    return StaticCredentialsProvider.create(
        AwsBasicCredentials.create(props.accessKeyId(), props.secretAccessKey())
    );
  }
}
