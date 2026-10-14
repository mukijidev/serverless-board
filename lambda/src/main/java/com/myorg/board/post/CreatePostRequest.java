package com.myorg.board.post;
import java.util.List;

public record CreatePostRequest(String title, String content, List<String> imageKeys, String thumbnailKey) {
}

