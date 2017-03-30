package com.ethercamp.harmony.util.exception;

import com.googlecode.jsonrpc4j.ErrorResolver;
import com.googlecode.jsonrpc4j.JsonRpcErrors;
import com.googlecode.jsonrpc4j.JsonRpcError;
import com.googlecode.jsonrpc4j.ReflectionUtil;
import java.lang.reflect.Method;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public class Web3jSafeAnnotationsErrorResolver implements ErrorResolver {

  public static final Web3jSafeAnnotationsErrorResolver INSTANCE = new Web3jSafeAnnotationsErrorResolver();

    public JsonError resolveError(
            Throwable t, Method method, List<JsonNode> arguments) {

          JsonRpcErrors errors = ReflectionUtil.getAnnotation(method, JsonRpcErrors.class);
          
          if (errors != null) {
            for (JsonRpcError em : errors.value()) {
              if (em.exception().isInstance(t)) {
                String message = em.message()!=null && em.message().trim().length() > 0
                        ? em.message()
                        : t.getMessage();
                
                // we set the "data" argument to be a simple concatenation of the 
                // exception's class name and the message; this helps with Web3j's
                // expectation of a "data" field that is a String, as opposed to the 
                // defaut Object as defined in the default versions of the ErrorResolver
                // implementations used with the jsonrpc4j library
                return new JsonError(em.code(), message,
                                em.exception().getName() + ": " + message);
              }
            }
          }

          //  none found
          return null;
    }
}
