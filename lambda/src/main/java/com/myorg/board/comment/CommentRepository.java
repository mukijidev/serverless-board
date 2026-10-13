package com.myorg.board.comment;

import com.myorg.board.common.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.Delete;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.ReturnValuesOnConditionCheckFailure;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

public class CommentRepository {
    private static final String META = "META";
    private static final String COMMENT_PREFIX = "COMMNET#";
    private static final String CONDITION_FAILED = "ConditionalCheckFailed";

    private final String tableName;
    private final DynamoDbClient client;
    private final TableSchema<Comment> schema = TableSchema.fromBean(Comment.class);
    private final DynamoDbTable<Comment> table;

    public CommentRepository(String tableName) {
        this.tableName = tableName;
        this.client = DynamoDbClient.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
        this.table = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(client)
                .build()
                .table(tableName, schema);
    }

    public Result<Comment> add(Comment comment) {
        comment.setPk(partitionKey(comment.getPostId()));
        comment.setSk(COMMENT_PREFIX + comment.getCommentId());

        TransactWriteItem save = TransactWriteItem.builder()
                .put(Put.builder()
                        .tableName(tableName)
                        .item(schema.itemToMap(comment, true))
                        .build())
                .build();
        try {
            client.transactWriteItems(TransactWriteItemsRequest.builder()
                    .transactItems(save, changeCount(comment.getPostId(), 1))
                    .build());
            return Result.ok(comment);
        } catch (TransactionCanceledException e) {
            if (conditionFailed(e, 1)) {
                return Result.notFound();
            }
            throw e;
        }
    }


    public List<Comment> list(String postId, int limit) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.sortBeginsWith(Key.builder()
                        .partitionValue(partitionKey(postId))
                        .sortValue(COMMENT_PREFIX)
                        .build()))
                .consistentRead(true)
                .limit(limit)
                .build();

        List<Comment> comments = new ArrayList<>();
        for (Page<Comment> page : table.query(request)) {
            comments.addAll(page.items());
            break;
        }

        return comments;
    }

    public Result<Void> delete(String postId, String commentId, String userId) {
        TransactWriteItem remove = TransactWriteItem.builder()
                .delete(Delete.builder()
                        .tableName(tableName)
                        .key(Map.of(
                                "pk", AttributeValue.fromS(partitionKey(postId)),
                                "sk", AttributeValue.fromS(COMMENT_PREFIX + commentId)))
                        .conditionExpression("attribute_exists(pk) AND authorId = :uid")
                        .expressionAttributeValues(Map.of(":uid", AttributeValue.fromS(userId)))
                        .returnValuesOnConditionCheckFailure(ReturnValuesOnConditionCheckFailure.ALL_OLD)
                        .build())
                .build();

        try {
            client.transactWriteItems(TransactWriteItemsRequest.builder()
                    .transactItems(remove, changeCount(postId, -1))
                    .build());
            return Result.ok(null);

        } catch (TransactionCanceledException e) {
            if(conditionFailed(e, 0)) {
                return reason(e, 0).hasItem() ? Result.forbidden() : Result.notFound();
            }
            if(conditionFailed(e, 1)) {
                return Result.notFound();
            }

            throw  e;
        }
    }


    private TransactWriteItem changeCount(String postId, int delta) {
        return TransactWriteItem.builder()
                .update(Update.builder()
                        .tableName(tableName)
                        .key(Map.of(
                                "pk", AttributeValue.fromS(partitionKey(postId)),
                                "sk", AttributeValue.fromS(META)))
                        .updateExpression("ADD commentCount :delta")
                        .conditionExpression("attribute_exists(pk)")
                        .expressionAttributeValues(
                                Map.of(":delta", AttributeValue.fromN(String.valueOf(delta))))
                        .build())
                .build();

    }


    private static CancellationReason reason(TransactionCanceledException e, int index) {
        if(!e.hasCancellationReasons() || e.cancellationReasons().size() <= index) {
            return null;
        }
        return e.cancellationReasons().get(index);
    }


    private static boolean conditionFailed(TransactionCanceledException e, int index) {
        CancellationReason reason = reason(e, index);
        return reason != null && CONDITION_FAILED.equals(reason.code());
    }

    private static String partitionKey(String postId) {
        return "POST#" + postId;
    }

}