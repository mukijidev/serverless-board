package com.myorg.board.comment;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.myorg.board.common.ApiResponses;
import com.myorg.board.common.Auth;
import com.myorg.board.common.Result;
import com.myorg.board.post.PostRules;

public class DeleteCommentHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

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

        String commentId = CommentRules.commentId(event);
        if (commentId == null) {
            return ApiResponses.error(400, "commentId is required");
        }

        try {
            Result<Void> result = repository.delete(postId, commentId, userId);

            if (result.outcome() == Result.Outcome.NOT_FOUND) {
                return ApiResponses.error(404, "Comment not found");
            } else if (result.outcome() == Result.Outcome.FORBIDDEN) {
                return ApiResponses.error(403, "not the author of this comment");
            }

            return ApiResponses.noContent();
        } catch (RuntimeException e) {
            context.getLogger().log("Failed to delete comment : " + e);
            return ApiResponses.error(500, "Internal Server error");
        }
    }
}

