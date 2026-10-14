package com.myorg.board.post;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.myorg.board.upload.ImageKeys;
import java.util.List;
import java.util.Map;

public final class PostRules {
    public static final int MAX_TITLE_LENGTH = 100;
    public static final int MAX_IMAGES = 5;
    public static final int MAX_AUTHOR_NAME_LENGTH = 20;

    private PostRules() {}

    public static String imageKeysError(List<String> imageKeys) {
        if (imageKeys.size() > MAX_IMAGES) {
            return "at most " + MAX_IMAGES + " images are allowed";
        }
        for (String key : imageKeys) {
            if (!ImageKeys.isValid(key)) {
                return "invalid image key";
            }
        }
        return null;
    }

    public static String thumbnailKeyError(String thumbnailKey, List<String> imageKeys) {
        if(thumbnailKey != null && !imageKeys.contains(thumbnailKey)) {
            return "thumbnailkey must be one of the imageKeys";
        }


        return null;
    }

    public static String postId(APIGatewayProxyRequestEvent event) {
        Map<String, String> params = event.getPathParameters();
        return params == null ? null : params.get("postId");
    }

    public static String authorName(String nickname) {
        if (nickname == null) {
            return null;
        }
        String name = nickname.strip();
        if (name.isEmpty()) {
            return null;
        }
        return name.substring(0, Math.min(name.length(), MAX_AUTHOR_NAME_LENGTH));
    }
}