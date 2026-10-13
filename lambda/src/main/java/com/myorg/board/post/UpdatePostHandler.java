package com.myorg.board.post;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.myorg.board.common.ApiResponses;
import com.myorg.board.common.Auth;
import com.myorg.board.common.Result;

public class UpdatePostHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final PostRepository repository = new PostRepository(System.getenv("TABLE_NAME"));

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {

        String userId = Auth.userId(event);
        if(userId == null) {
            return ApiResponses.error(401, "Unauthorized");
        }

        String postId = PostRules.postId(event);
        if(postId == null) {
            return ApiResponses.error(400, "postId is required");
        }

        String body = event.getBody();
        if(body == null || body.isBlank()) {
            return ApiResponses.error(400, "Request body is required");
        }

        UpdatePostRequest request;
        try {
            request = ApiResponses.mapper().readValue(body, UpdatePostRequest.class);
        } catch(JsonProcessingException e ){
            return ApiResponses.error(400, "Invalid Json body");
        }

        if (request == null
                || (request.title() == null && request.content() == null && request.imageKeys() == null)) {
            return ApiResponses.error(400, "nothing to update");
        }
        if (request.title() != null
                && (request.title().isBlank() || request.title().length() > PostRules.MAX_TITLE_LENGTH)) {
            return ApiResponses.error(400, "title must be 1 to " + PostRules.MAX_TITLE_LENGTH + " characters");
        }
        if (request.content() != null && request.content().isBlank()) {
            return ApiResponses.error(400, "content must not be blank");
        }
        if (request.imageKeys() != null) {
            String error = PostRules.imageKeysError(request.imageKeys());
            if (error != null) {
                return ApiResponses.error(400, error);
            }
        }

        try {
            Result<Post> result = repository.update(
                    postId, userId, request.title(), request.content(), request.imageKeys());
            return switch (result.outcome()) {
                case OK -> ApiResponses.json(200, PostResponse.from(result.value()));
                case NOT_FOUND -> ApiResponses.error(404, "Post not found");
                case FORBIDDEN -> ApiResponses.error(403, "You are not the author of this post");
            };
        } catch (RuntimeException e) {
            context.getLogger().log("Failed to update post: " + e);
            return ApiResponses.error(500, "Internal server error");
        }



    }

}
