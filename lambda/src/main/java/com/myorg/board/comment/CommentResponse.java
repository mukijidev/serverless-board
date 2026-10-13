package com.myorg.board.comment;

public record CommentResponse(String commentId, String postId, String content,
                              String authorId, String createdAt) {

    public static CommentResponse from(Comment comment) {
        return new CommentResponse(comment.getCommentId(), comment.getPostId(),
                comment.getContent(), comment.getAuthorId(), comment.getCreatedAt());
    }
}