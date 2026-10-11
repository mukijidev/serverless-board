package com.myorg.board.post;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.myorg.board.common.ApiResponses;
import com.myorg.board.common.Auth;

public class DeletePostHandler implements  RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final PostRepository repository = new PostRepository(System.getenv("TABLE_NAME"));

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {

        String userId = Auth.userId(event);
        if (userId == null) {
            return ApiResponses.error(401, "Unauthorized");
        }

        String postId = PostRules.postId(event);
        if(postId == null) {
            return ApiResponses.error(400, "postId is required");
        }

        try {
            return switch (repository.delete(postId, userId).outcome()) {
                case OK -> ApiResponses.noContent();
                case NOT_FOUND -> ApiResponses.error(404, "Post not found");
                case FORBIDDEN -> ApiResponses.error(403, "You are not the author of this post");
            };
        } catch (RuntimeException e) {
            context.getLogger().log("Failed to delete post: " + e);
            return ApiResponses.error(500, "Internal server error");
        }
    }

}