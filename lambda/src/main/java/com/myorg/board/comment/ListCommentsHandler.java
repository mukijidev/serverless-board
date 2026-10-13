package com.myorg.board.comment;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.myorg.board.common.ApiResponses;
import com.myorg.board.post.PostRules;

import java.util.List;

public class ListCommentsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final int MAX_COMMENTS = 100;
    private final CommentRepository repository = new CommentRepository(System.getenv("TABLE_NAME"));

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String postId = PostRules.postId(event);
        if(postId == null) {
            return ApiResponses.error(400, "postId is required");
        }

        try {
            List<CommentResponse> comments = repository.list(postId, MAX_COMMENTS).stream()
                    .map(CommentResponse::from)
                    .toList();
            return ApiResponses.json(200, comments);
        } catch(RuntimeException e ){
            context.getLogger().log("Failed to list comments : "  + e);
            return ApiResponses.error(500, "Internal server error");
        }
    }
}