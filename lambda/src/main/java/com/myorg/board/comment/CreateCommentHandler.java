package com.myorg.board.comment;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.myorg.board.common.ApiResponses;
import com.myorg.board.common.Auth;
import com.myorg.board.common.Result;
import com.myorg.board.post.PostRules;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class CreateCommentHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);


    private final CommentRepository repository = new CommentRepository(System.getenv("TABLE_NAME"));

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String userId = Auth.userId(event);
        if (userId == null) {
            return ApiResponses.error(401, "Unauthorized");
        }

        String postId = PostRules.postId(event);
        if (postId == null) {
            return ApiResponses.error(400, "postId is required");
        }

        String body = event.getBody();
        if (body == null || body.isBlank()) {
            return ApiResponses.error(400, "Request body is required");
        }

        CreateCommentRequest request;

        try {
            request = ApiResponses.mapper().readValue(body, CreateCommentRequest.class);
        } catch (JsonProcessingException e) {
            return ApiResponses.error(400, "Invalid JSON Body");
        }

        if (request == null || request.content() == null || request.content().isBlank()) {
            return ApiResponses.error(400, "content is required");
        }

        if (request.content().length() > CommentRules.MAX_CONTENT_LENGTH) {
            return ApiResponses.error(400, "content must be " + CommentRules.MAX_CONTENT_LENGTH +
                    "characters or less ");
        }

        Instant now = Instant.now();
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setCommentId(CommentRules.newCommentId(now));
        comment.setContent(request.content());
        comment.setAuthorId(userId);
        comment.setCreatedAt(TIMESTAMP.format(now));

        try {
            Result<Comment> result = repository.add(comment);
            if (result.outcome() == Result.Outcome.NOT_FOUND) {
                return ApiResponses.error(404, "Post not found");
            }
            return ApiResponses.json(201, CommentResponse.from(result.value()));
        } catch (RuntimeException e) {
            context.getLogger().log("Failed to add comment : " + e);
            return ApiResponses.error(500, "Internal Server Error");
        }
    }


}
