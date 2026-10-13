package com.myorg.board.post;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.myorg.board.common.ApiResponses;
import com.myorg.board.common.Auth;
import com.myorg.board.comment.CommentRepository;
import com.myorg.board.common.Result;

public class DeletePostHandler implements  RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final PostRepository repository = new PostRepository(System.getenv("TABLE_NAME"));
    private final CommentRepository commentRepository = new CommentRepository(System.getenv("TABLE_NAME"));

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
            Result<Void> result = repository.delete(postId, userId);
            Result.Outcome outcome = result.outcome();

            if(outcome == Result.Outcome.OK) {
                deleteComments(postId, context);
            }

            return switch (outcome ) {
                case OK -> ApiResponses.noContent();
                case NOT_FOUND -> ApiResponses.error(404, "Post not found");
                case FORBIDDEN -> ApiResponses.error(403, "not the author of post");
            };
        } catch (RuntimeException e) {
            context.getLogger().log("Failed to delete post: " + e);
            return ApiResponses.error(500, "Internal server error");
        }
    }

    private void deleteComments(String postId, Context context) {
        try {
            commentRepository.deleteAllForPost(postId);
        } catch(RuntimeException e) {
            context.getLogger().log("Failed to delete comments of post " + postId + ":" + e);
        }
    }

}