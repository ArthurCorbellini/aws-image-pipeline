package com.arthurcorbellini.imagepipeline.api.controller;

import com.arthurcorbellini.imagepipeline.api.config.AppProperties;
import com.arthurcorbellini.imagepipeline.common.ImageMessage;
import com.arthurcorbellini.imagepipeline.common.ImageStatus;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.ObjectMapper;

@RestController
public class ImageController {

  private final S3Client s3Client;
  private final SqsClient sqsClient;
  private final S3Presigner s3Presigner;
  private final AppProperties appProperties;
  private final ObjectMapper objectMapper;

  public ImageController(S3Client s3Client, SqsClient sqsClient, S3Presigner s3Presigner, AppProperties appProperties, ObjectMapper objectMapper) {
    this.s3Client = s3Client;
    this.sqsClient = sqsClient;
    this.s3Presigner = s3Presigner;
    this.appProperties = appProperties;
    this.objectMapper = objectMapper;
  }

  @PostMapping("/images")
  public ResponseEntity<UploadResponse> upload(@RequestParam("file") MultipartFile file) throws IOException {
    UUID imageId = UUID.randomUUID();
    String s3Key = "originals/" + imageId + extractExtension(file.getOriginalFilename());

    s3Client.putObject(
        PutObjectRequest.builder()
            .bucket(appProperties.s3().bucketName())
            .key(s3Key)
            .contentType(file.getContentType())
            .build(),
        RequestBody.fromBytes(file.getBytes())
    );

    ImageMessage message = new ImageMessage(imageId, s3Key);
    sqsClient.sendMessage(
        SendMessageRequest.builder()
            .queueUrl(appProperties.sqs().queueUrl())
            .messageBody(objectMapper.writeValueAsString(message))
            .build()
    );

    return ResponseEntity.status(HttpStatus.CREATED).body(new UploadResponse(imageId));
  }

  @GetMapping("/images/{id}")
  public ResponseEntity<StatusResponse> status(@PathVariable UUID id) {
    String metadataKey = "metadata/" + id + ".json";

    try {
      var metadataObject = s3Client.getObject(
          GetObjectRequest.builder()
              .bucket(appProperties.s3().bucketName())
              .key(metadataKey)
              .build()
      );
      ImageStatus imageStatus = objectMapper.readValue(metadataObject, ImageStatus.class);

      String url = presignedUrl(imageStatus.processedKey());
      return ResponseEntity.ok(new StatusResponse("READY", url));
    } catch (NoSuchKeyException e) {
      return ResponseEntity.ok(new StatusResponse("PENDING", null));
    }
  }

  private String presignedUrl(String key) {
    GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
        .signatureDuration(Duration.ofMinutes(15))
        .getObjectRequest(b -> b.bucket(appProperties.s3().bucketName()).key(key))
        .build();

    return s3Presigner.presignGetObject(presignRequest).url().toString();
  }

  private String extractExtension(String filename) {
    if (filename == null || !filename.contains(".")) {
      return "";
    }
    return filename.substring(filename.lastIndexOf('.'));
  }

  public record UploadResponse(UUID imageId) {
  }

  public record StatusResponse(String status, String url) {
  }
}
