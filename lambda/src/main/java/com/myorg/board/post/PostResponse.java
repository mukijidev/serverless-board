package com.myorg.board.post;
import java.util.List;
import java.util.Objects;

public record PostResponse(String postId, String title, String content, String authorId, String createdAt,
                           List<String> imageKeys, int commentCount) {

    public static PostResponse from(Post post) {
        List<String> keys = Objects.requireNonNullElse(post.getImageKeys(), List.of());
        int commentCount = Objects.requireNonNullElse(post.getCommentCount(), 0);
        return new PostResponse(post.getPostId(), post.getTitle(), post.getContent(),
                post.getAuthorId(), post.getCreatedAt(), keys, commentCount);
    }

}