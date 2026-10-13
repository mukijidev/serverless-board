package com.myorg.board.comment;


import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class Comment {
    private String pk;
    private String sk;
    private String commentId;
    private String postId;
    private String content;
    private String authorId;
    private String createdAt;

    // Partition key: POST#<postId>
    @DynamoDbPartitionKey
    @DynamoDbAttribute("pk")
    public String getPk() { return pk;}
    public void setPk(String pk) {this.pk = pk;}

    // Sort key: COMMENT#<commentId>. starts with a UTC timestamp,
    @DynamoDbSortKey
    @DynamoDbAttribute("sk")
    public String getSk() { return sk;}
    public void setSk(String sk) {this.sk = sk;}

    public String getCommentId() { return commentId;}
    public void setCommentId(String commentId) {this.commentId = commentId;};

    public String getPostId() { return postId;}
    public void setPostId(String postId) {this.postId = postId;}

    public String getContent() { return content;}
    public void setContent(String content) {this.content = content;}

    public String getAuthorId() { return authorId;}
    public void setAuthorId(String authorId) {this.authorId = authorId;}

    public String getCreatedAt() { return createdAt;}
    public void setCreatedAt(String createdAt) {this.createdAt = createdAt;}
}