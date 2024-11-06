package cn.citizenwiki.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpResponse;


/**
 * json请求体处理器
 * @param <T>
 */
public class JacksonBodyHandler<T> implements HttpResponse.BodyHandler<T> {
        private final TypeReference<T> typeReference;
        private final ObjectMapper objectMapper;

        public JacksonBodyHandler(TypeReference<T> typeReference, ObjectMapper objectMapper) {
            this.typeReference = typeReference;
            this.objectMapper = objectMapper;
        }

        @Override
        public HttpResponse.BodySubscriber<T> apply(HttpResponse.ResponseInfo responseInfo) {
            return HttpResponse.BodySubscribers.mapping(
                    HttpResponse.BodySubscribers.ofInputStream(),
                    inputStream -> {
                        try {
                            return objectMapper.readValue(inputStream, typeReference);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to parse JSON", e);
                        }
                    }
            );
        }
    }