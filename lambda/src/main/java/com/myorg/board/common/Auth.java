package com.myorg.board.common;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;

import java.util.Map;

public final class Auth {
    private Auth() {}

    public static String userId(APIGatewayProxyRequestEvent event) {
        return claim(event, "sub");
    }

    public static String nickname(APIGatewayProxyRequestEvent event) {
        return claim(event, "nickname");
    }

    private static String claim(APIGatewayProxyRequestEvent event, String name) {
        APIGatewayProxyRequestEvent.ProxyRequestContext context = event.getRequestContext();
        if( context == null || context.getAuthorizer() == null) {
            return null;
        }

        Object claims = context.getAuthorizer().get("claims");
        if(claims instanceof Map<?, ?> map) {
            Object value = map.get(name);
            return value == null ? null : value.toString();
        }

        return null;
    }
}

