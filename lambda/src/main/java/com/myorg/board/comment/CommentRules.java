package com.myorg.board.comment;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

public final class CommentRules {
    public static final int MAX_CONTENT_LENGTH = 500;

    private CommentRules() {}

    public static String commentId(APIGatewayProxyRequestEvent event) {
        Map<String, String> params = event.getPathParameters();
        return params == null? null: params.get("commentId");
    }

    private static final DateTimeFormatter ID_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneOffset.UTC);

    public static String newCommentId(Instant now) {
        return ID_TIME.format(now) + "-" + UUID.randomUUID();
    }
}