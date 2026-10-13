package com.myorg;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.*;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.constructs.Construct;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.GlobalSecondaryIndexProps;
import software.amazon.awscdk.services.dynamodb.Table;
import java.util.Map;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.services.cognito.AuthFlow;
import software.amazon.awscdk.services.cognito.AutoVerifiedAttrs;
import software.amazon.awscdk.services.cognito.SignInAliases;
import software.amazon.awscdk.services.cognito.UserPool;
import software.amazon.awscdk.services.cognito.UserPoolClient;
import software.amazon.awscdk.services.cognito.UserPoolClientOptions;
import software.amazon.awscdk.services.apigateway.Cors;
import software.amazon.awscdk.services.apigateway.CorsOptions;
import software.amazon.awscdk.services.apigateway.GatewayResponseOptions;
import software.amazon.awscdk.services.apigateway.ResponseType;
import software.amazon.awscdk.services.s3.CorsRule;
import software.amazon.awscdk.services.s3.HttpMethods;
import software.amazon.awscdk.services.cloudfront.BehaviorOptions;
import software.amazon.awscdk.services.cloudfront.Distribution;
import software.amazon.awscdk.services.cloudfront.PriceClass;
import software.amazon.awscdk.services.cloudfront.ViewerProtocolPolicy;
import software.amazon.awscdk.services.cloudfront.origins.S3BucketOrigin;
import software.amazon.awscdk.services.s3.assets.AssetOptions;
import software.amazon.awscdk.services.s3.deployment.BucketDeployment;
import software.amazon.awscdk.services.s3.deployment.Source;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

public class ServerlessBoardStack extends Stack {
    private final String siteOrigin;

    public ServerlessBoardStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public ServerlessBoardStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // s3
        final Bucket bucket = Bucket.Builder.create(this, "BoardBucket")
                .removalPolicy(RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .cors(List.of(CorsRule.builder()
                        .allowedMethods(List.of(HttpMethods.PUT))
                        .allowedOrigins(List.of("https://*.cloudfront.net"))
                        .allowedHeaders(List.of("Content-Type"))
                        .maxAge(3000)
                        .build()))
                .build();

        // site hosting, private bucket + cloudFront(OAC)
        final Bucket siteBucket = Bucket.Builder.create(this, "SiteBucket")
                .removalPolicy(RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .build();

        final Distribution distribution  = Distribution.Builder.create(this, "SiteDistribution")
                .defaultRootObject("index.html")
                .priceClass(PriceClass.PRICE_CLASS_100)
                .defaultBehavior(BehaviorOptions.builder()
                        .origin(S3BucketOrigin.withOriginAccessControl(siteBucket))
                        .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS).build())
                .additionalBehaviors(Map.of("uploads/*", BehaviorOptions.builder()
                        .origin(S3BucketOrigin.withOriginAccessControl(bucket))
                        .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS).build()))
                .build();

        this.siteOrigin = "https://" + distribution.getDistributionDomainName();


        // dynamodb
        final Table table = Table.Builder.create(this, "BoardTable")
                .partitionKey(Attribute.builder().name("pk").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("sk").type(AttributeType.STRING).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        table.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("gsi1")
                .partitionKey(Attribute.builder().name("gsi1pk").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("gsi1sk").type(AttributeType.STRING).build())
                .build());

        // User Pool
        final UserPool userPool = UserPool.Builder.create(this, "BoardUserPool")
                .userPoolName("serverless-board-users")
                .selfSignUpEnabled(true)
                .signInAliases(SignInAliases.builder().email(true).build())
                .autoVerify(AutoVerifiedAttrs.builder().email(true).build())
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        final UserPoolClient userPoolClient = userPool.addClient("BoardWebClient",
                UserPoolClientOptions.builder().generateSecret(false)
                        .authFlows(AuthFlow.builder().userPassword(true).userSrp(true).build()).build());

        CfnOutput.Builder.create(this, "UserPoolId").value(userPool.getUserPoolId()).build();
        CfnOutput.Builder.create(this, "UserPoolClientId").value(userPoolClient.getUserPoolClientId()).build();





        final Function createPostFn = createBoardFunction(
                "CreatePostFunction", "com.myorg.board.post.CreatePostHandler::handleRequest",  Map.of("TABLE_NAME", table.getTableName()));
        final Function listPostsFn = createBoardFunction(
                "ListPostsFunction", "com.myorg.board.post.ListPostsHandler::handleRequest",  Map.of("TABLE_NAME", table.getTableName()));

        table.grant(createPostFn, "dynamodb:PutItem");
        table.grant(listPostsFn, "dynamodb:Query");

        final Function updatePostFn = createBoardFunction(
                "UpdatePostFunction", "com.myorg.board.post.UpdatePostHandler::handleRequest",
                Map.of("TABLE_NAME", table.getTableName()));
        final Function deletePostFn = createBoardFunction(
                "DeletePostFunction", "com.myorg.board.post.DeletePostHandler::handleRequest",
                Map.of("TABLE_NAME", table.getTableName()));

        table.grant(updatePostFn, "dynamodb:UpdateItem");
        table.grant(deletePostFn, "dynamodb:DeleteItem");

        final Function createCommentFn = createBoardFunction(
                "CreateCommentFunction", "com.myorg.board.comment.CreateCommentHandler::handleRequest",
                Map.of("TABLE_NAME", table.getTableName()));
        final Function listCommentsFn = createBoardFunction(
                "ListCommentsFunction", "com.myorg.board.comment.ListCommentsHandler::handleRequest",
                Map.of("TABLE_NAME", table.getTableName()));
        final Function deleteCommentFn = createBoardFunction(
                "DeleteCommentFunction", "com.myorg.board.comment.DeleteCommentHandler::handleRequest",
                Map.of("TABLE_NAME", table.getTableName()));

        table.grant(createCommentFn, "dynamodb:PutItem", "dynamodb:UpdateItem");
        table.grant(listCommentsFn, "dynamodb:Query");
        table.grant(deleteCommentFn, "dynamodb:DeleteItem", "dynamodb:UpdateItem");

        final Function presignedUrlFn = createBoardFunction(
                "PresignedUrlFunction",  "com.myorg.board.upload.PresignedUrlHandler::handleRequest",
                Map.of("BUCKET_NAME", bucket.getBucketName()));

        bucket.grantPut(presignedUrlFn, "uploads/*");




        final RestApi api = RestApi.Builder.create(this, "BoardApi")
                .restApiName("serverless-board-api")
                .description("Serverless board REST API")
                .deployOptions(StageOptions.builder()
                        .stageName("prod")
                        .throttlingRateLimit(5)
                        .throttlingBurstLimit(10)
                        .build())
                .defaultCorsPreflightOptions(CorsOptions.builder()
                        .allowOrigins(List.of(siteOrigin))
                        .allowMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"))
                        .allowHeaders(List.of("Content-Type", "Authorization"))
                        .maxAge((Duration.hours(1)))
                        .build())
                .build();

        final Map<String, String> corsHeaders = new TreeMap<>(Map.of(
                "Access-Control-Allow-Origin", "'" + siteOrigin + "'",
                "Access-Control-Allow-Headers", "'Content-Type,Authorization'"));

        api.addGatewayResponse("Default4xx", GatewayResponseOptions.builder()
                .type(ResponseType.DEFAULT_4_XX)
                .responseHeaders(corsHeaders)
                .build());
        api.addGatewayResponse("Default5xx", GatewayResponseOptions.builder()
                .type(ResponseType.DEFAULT_5_XX)
                .responseHeaders(corsHeaders)
                .build());

        final CognitoUserPoolsAuthorizer authorizer = CognitoUserPoolsAuthorizer.Builder
                .create(this, "BoardAuthorizer")
                .cognitoUserPools(List.of(userPool))
                .build();

        final MethodOptions authRequired = MethodOptions.builder()
                .authorizer(authorizer)
                .authorizationType(AuthorizationType.COGNITO)
                .build();


        final Resource posts = api.getRoot().addResource("posts");
        posts.addMethod("GET", new LambdaIntegration(listPostsFn));
        posts.addMethod("POST", new LambdaIntegration(createPostFn),authRequired);

        final Resource uploads = api.getRoot().addResource("uploads");
        uploads.addMethod("POST", new LambdaIntegration(presignedUrlFn), authRequired);

        final Resource postById = posts.addResource("{postId}");
        postById.addMethod("PUT", new LambdaIntegration(updatePostFn), authRequired);
        postById.addMethod("DELETE", new LambdaIntegration(deletePostFn), authRequired);

        final Resource comments = postById.addResource("comments");
        comments.addMethod("GET", new LambdaIntegration(listCommentsFn));
        comments.addMethod("POST", new LambdaIntegration(createCommentFn), authRequired);


        final Resource commentById = comments.addResource("{commentId}");
        commentById.addMethod("DELETE", new LambdaIntegration(deleteCommentFn), authRequired);

        CfnOutput.Builder.create(this, "ApiUrl")
                .value((api.getUrl()))
                .description("Base URL of the board  API")
                .build();




        final LogGroup deploymentLogs = LogGroup.Builder.create(this, "SiteDeploymentLogs")
                .retention(RetentionDays.ONE_WEEK)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        BucketDeployment.Builder.create(this, "SiteDeployment")
                        .destinationBucket(siteBucket)
                                .sources(List.of(
                                        Source.asset("frontend", AssetOptions.builder()
                                                .exclude(List.of("config.json"))
                                                .build()),
                                        Source.jsonData("config.json", new TreeMap<>(Map.of(
                                                "apiBaseUrl", api.getUrl(),
                                                "region", this.getRegion(),
                                                "clientId", userPoolClient.getUserPoolClientId())))))
                                        .distribution(distribution)
                                        .distributionPaths(List.of("/*"))
                                        .logGroup(deploymentLogs)
                                        .build();




        CfnOutput.Builder.create(this, "SiteUrl")
                .value(siteOrigin)
                .description("Public URL of the board site")
                .build();
    }

    private Function createBoardFunction(String id, String handler, Map<String, String> environment) {
        final Map<String, String> env = new HashMap<>(environment);
        env.put("ALLOWED_ORIGIN", siteOrigin);

        final LogGroup logs = LogGroup.Builder.create(this, id + "Logs")
                .retention(RetentionDays.ONE_WEEK)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        return Function.Builder.create(this, id)
                .runtime(Runtime.JAVA_21)
                .handler(handler)
                .code(Code.fromAsset("lambda/target/board-lambda.jar"))
                .memorySize(512)
                .timeout(Duration.seconds(15))
                .logGroup(logs)
                .environment(env)
                .build();
    }
}
