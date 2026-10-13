package com.myorg.board.post;

import java.util.ArrayList;
import java.util.List;

import com.myorg.board.common.Result;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure;

public class PostRepository {
    private final DynamoDbTable<Post> table;
    private final DynamoDbIndex<Post> index;
    private static final String META = "META";

    public PostRepository(String tableName) {
        DynamoDbClient client = DynamoDbClient.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
        DynamoDbEnhancedClient enhanced = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(client)
                .build();
        this.table = enhanced.table(tableName, TableSchema.fromBean(Post.class));
        this.index = table.index("gsi1");
    }

    public Result<Post> save(Post post) {
        table.putItem(post);
        return Result.ok(post);
    }

    public List<Post> listLatest(int limit) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue("POSTS").build()))
                .scanIndexForward(false)
                .limit(limit)
                .build();

        List<Post> posts = new ArrayList<>();
        for (Page<Post> page : index.query(request)) {
            posts.addAll(page.items());
            break;
        }
        return posts;
    }

    public Result<Post> update(String postId, String userId, String title, String content,
                               List<String> imageKeys) {
            Post change = new Post();
            change.setPk(partitionKey(postId));
            change.setSk(META);
            change.setTitle(title);
            change.setContent(content);
            change.setImageKeys(imageKeys);

            try {
                Post updated = table.updateItem(UpdateItemEnhancedRequest.builder(Post.class)
                        .item(change)
                        .ignoreNullsMode(IgnoreNullsMode.SCALAR_ONLY)
                        .conditionExpression(ownerCondition(userId))
                        .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD)
                        .build());
                return Result.ok(updated);
            } catch(ConditionalCheckFailedException e){
                return failure(e);
            }
    }


    public Result<Void> delete(String postId, String userId) {
        try {
            table.deleteItem(DeleteItemEnhancedRequest.builder()
                    .key(Key.builder().partitionValue(partitionKey(postId)).sortValue(META).build())
                    .conditionExpression(ownerCondition(userId))
                    .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD)
                    .build());
            return Result.ok(null);

        } catch (ConditionalCheckFailedException e ){
            return failure(e);
        }


    }

    private static<T> Result<T> failure(ConditionalCheckFailedException e)
    {
        return e.hasItem() ? Result.forbidden() : Result.notFound();
    }

    private static Expression ownerCondition(String userId) {
        return Expression.builder()
                .expression("attribute_exists(pk) AND authorId = :uid")
                .putExpressionValue(":uid", AttributeValue.fromS(userId))
                .build();
    }
    private static String partitionKey(String postId) {
        return "POST#" + postId;
    }
}