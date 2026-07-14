package com.arthurcorbellini.imagepipeline.worker.processor;

import com.arthurcorbellini.imagepipeline.common.ImageMessage;
import com.arthurcorbellini.imagepipeline.common.ImageStatus;
import com.arthurcorbellini.imagepipeline.worker.config.AppProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import tools.jackson.databind.ObjectMapper;

@Component
public class ImageProcessor {

  private static final int THUMBNAIL_SIZE = 200;

  private final S3Client s3Client;
  private final SqsClient sqsClient;
  private final AppProperties appProperties;
  private final ObjectMapper objectMapper;

  public ImageProcessor(S3Client s3Client, SqsClient sqsClient, AppProperties appProperties, ObjectMapper objectMapper) {
    this.s3Client = s3Client;
    this.sqsClient = sqsClient;
    this.appProperties = appProperties;
    this.objectMapper = objectMapper;
  }

  @Scheduled(fixedDelay = 1000)
  public void poll() {
    var response = sqsClient.receiveMessage(
        ReceiveMessageRequest.builder()
            .queueUrl(appProperties.sqs().queueUrl())
            .maxNumberOfMessages(1)
            .waitTimeSeconds(10)
            .build()
    );

    for (Message message : response.messages()) {
      process(message);
    }
  }

  private void process(Message message) {
    ImageMessage imageMessage = objectMapper.readValue(message.body(), ImageMessage.class);
    String processedKey = imageMessage.s3Key().replaceFirst("^originals/", "processed/");

    var original = s3Client.getObject(
        GetObjectRequest.builder()
            .bucket(appProperties.s3().bucketName())
            .key(imageMessage.s3Key())
            .build()
    );

    ByteArrayOutputStream thumbnail = new ByteArrayOutputStream();
    try {
      Thumbnails.of(original)
          .size(THUMBNAIL_SIZE, THUMBNAIL_SIZE)
          .toOutputStream(thumbnail);
    } catch (IOException e) {
      throw new RuntimeException("Failed to process image " + imageMessage.imageId(), e);
    }

    s3Client.putObject(
        PutObjectRequest.builder()
            .bucket(appProperties.s3().bucketName())
            .key(processedKey)
            .build(),
        RequestBody.fromBytes(thumbnail.toByteArray())
    );

    ImageStatus status = new ImageStatus("READY", processedKey);
    s3Client.putObject(
        PutObjectRequest.builder()
            .bucket(appProperties.s3().bucketName())
            .key("metadata/" + imageMessage.imageId() + ".json")
            .contentType("application/json")
            .build(),
        RequestBody.fromString(objectMapper.writeValueAsString(status))
    );

    sqsClient.deleteMessage(
        DeleteMessageRequest.builder()
            .queueUrl(appProperties.sqs().queueUrl())
            .receiptHandle(message.receiptHandle())
            .build()
    );
  }
}
