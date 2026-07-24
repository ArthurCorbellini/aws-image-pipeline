package com.arthurcorbellini.imagepipeline.api.config;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
public class AwsClientConfig {

  @Bean
  public S3Client s3Client(AwsProperties props) {
    var builder = S3Client.builder()
        .region(Region.of(props.region()))
        .credentialsProvider(credentials(props))
        .forcePathStyle(true);
    if (hasText(props.endpointUrl())) {
      builder.endpointOverride(URI.create(props.endpointUrl()));
    }
    return builder.build();
  }

  @Bean
  public S3Presigner s3Presigner(AwsProperties props) {
    var builder = S3Presigner.builder()
        .region(Region.of(props.region()))
        .credentialsProvider(credentials(props))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
    if (hasText(props.presignedEndpointUrl())) {
      builder.endpointOverride(URI.create(props.presignedEndpointUrl()));
    }
    return builder.build();
  }

  @Bean
  public SqsClient sqsClient(AwsProperties props) {
    var builder = SqsClient.builder()
        .region(Region.of(props.region()))
        .credentialsProvider(credentials(props));
    if (hasText(props.endpointUrl())) {
      builder.endpointOverride(URI.create(props.endpointUrl()));
    }
    return builder.build();
  }

  private AwsCredentialsProvider credentials(AwsProperties props) {
    if (!hasText(props.accessKeyId())) {
      return DefaultCredentialsProvider.builder().build();
    }
    return StaticCredentialsProvider.create(
        AwsBasicCredentials.create(props.accessKeyId(), props.secretAccessKey())
    );
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
