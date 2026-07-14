package com.arthurcorbellini.imagepipeline.api.controller;

import com.arthurcorbellini.imagepipeline.api.config.AppProperties;
import com.arthurcorbellini.imagepipeline.common.ImageMessage;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.ObjectMapper;

@RestController
public class ImageController {

  private final S3Client s3Client;
  private final SqsClient sqsClient;
  private final AppProperties appProperties;
  private final ObjectMapper objectMapper;

  public ImageController(S3Client s3Client, SqsClient sqsClient, AppProperties appProperties, ObjectMapper objectMapper) {
    this.s3Client = s3Client;
    this.sqsClient = sqsClient;
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
        RequestBody.fromInputStream(file.getInputStream(), file.getSize())
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

  private String extractExtension(String filename) {
    if (filename == null || !filename.contains(".")) {
      return "";
    }
    return filename.substring(filename.lastIndexOf('.'));
  }

  public record UploadResponse(UUID imageId) {
  }
}
