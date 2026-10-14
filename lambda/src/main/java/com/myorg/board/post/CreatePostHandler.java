package com.myorg.board.post;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.myorg.board.common.ApiResponses;
import com.myorg.board.common.Auth;
import com.myorg.board.common.Result;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class CreatePostHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final PostRepository repository = new PostRepository(System.getenv("TABLE_NAME"));



    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String userId = Auth.userId(event);
        if(userId == null) {
            return ApiResponses.error(401, "Unauthorized");
        }

        String body = event.getBody();
        if (body == null || body.isBlank()) {
            return ApiResponses.error(400, "Request body is required");
        }

        CreatePostRequest request;
        try {
            request = ApiResponses.mapper().readValue(body, CreatePostRequest.class);
        } catch (JsonProcessingException e) {
            return ApiResponses.error(400, "Invalid JSON body");
        }

        if (request == null || isBlank(request.title()) || isBlank(request.content())) {
            return ApiResponses.error(400, "title and content are required");
        }
        if (request.title().length() > PostRules.MAX_TITLE_LENGTH) {
            return ApiResponses.error(400, "title must be 100 characters or less");
        }

        List<String> imageKeys = Objects.requireNonNullElse(request.imageKeys(), List.of());

        String imageError = PostRules.imageKeysError(imageKeys);
        if (imageError != null) {
            return ApiResponses.error(400, imageError);
        }

        String thumbnailError = PostRules.thumbnailKeyError(request.thumbnailKey(), imageKeys);
        if(thumbnailError != null) {
            return ApiResponses.error(400, thumbnailError);
        }




        String postId = UUID.randomUUID().toString();
        String createdAt = TIMESTAMP.format(Instant.now());

        Post post = new Post();
        post.setPk("POST#" + postId);
        post.setSk("META");
        post.setGsi1pk("POSTS");
        post.setGsi1sk(createdAt + "#" + postId);
        post.setPostId(postId);
        post.setTitle(request.title());
        post.setContent(request.content());
        post.setAuthorId(userId);
        post.setAuthorName(PostRules.authorName(Auth.nickname(event)));
        post.setCreatedAt(createdAt);
        post.setImageKeys(imageKeys);
        post.setThumbnailKey(request.thumbnailKey());
        post.setCommentCount(0);

        try {
            Result<Post> result = repository.save(post);
            return ApiResponses.json(201, PostResponse.from(result.value()));
        } catch (RuntimeException e) {
            context.getLogger().log("Failed to save post: " + e);
            return ApiResponses.error(500, "Internal server error");
        }

    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}