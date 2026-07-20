package com.arthurcorbellini.imagepipeline.common;

import java.util.UUID;

public record ImageMessage(UUID imageId, String s3Key) {
}
