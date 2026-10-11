package com.myorg.board.upload;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.myorg.board.common.ApiResponses;
import com.myorg.board.common.Auth;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;


public class PresignedUrlHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final String bucketName = System.getenv("BUCKET_NAME");
    private final S3Presigner presigner  = S3Presigner.create();


    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String userId = Auth.userId(event);
        if (userId == null) {
            return ApiResponses.error(401, "Unauthorized");
        }

        String body = event.getBody();
        if (body == null || body.isBlank()) {
            return ApiResponses.error(400, "Request body is required");
        }

        UploadRequest request;
        try {
            request = ApiResponses.mapper().readValue(body, UploadRequest.class);
        } catch (JsonProcessingException e ) {
            return ApiResponses.error(400, "Invalid Json body");
        }

        if (request == null || request.contentType() == null || request.size() == null){
            return ApiResponses.error(400, "content type and size are required");
        }

        String extension = ImageKeys.extensionFor(request.contentType());
        if(extension == null){
            return ApiResponses.error(400,"content type must be image/jpeg or image/png");
        }

        if(request.size() <= 0 || request.size() > ImageKeys.MAX_SIZE_BYTES)
        {
            return ApiResponses.error(400, "size must be between 1 and 5MB ");
        }

        String key = ImageKeys.newKey(extension);

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(request.contentType())
                    .contentLength(request.size())
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(5))
                    .putObjectRequest(putRequest)
                    .build();

            String url = presigner.presignPutObject(presignRequest).url().toString();
            return ApiResponses.json(200, new UploadResponse(url, key));
        } catch (RuntimeException e) {
            context.getLogger().log("failed to presign upload: "+ e);
            return ApiResponses.error(500, "Internal server error");
        }

    }
}