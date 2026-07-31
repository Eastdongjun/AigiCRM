package com.ycmn.common.engine;

import com.fasterxml.jackson.databind.*;
import com.ycmn.common.engine.PermissionRuleEngine.RuleResult;
import com.ycmn.security.SecurityUtils;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.*;

/**
 * 响应字段过滤拦截器
 *
 * 根据规则引擎计算的 hiddenFields/visibleFields，
 * 在序列化 JSON 响应时过滤掉无权限看到的字段。
 *
 * 用法：只需添加此 @ControllerAdvice，无需修改 Controller
 */
@ControllerAdvice
public class FieldFilterAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true; // 对所有 JSON 响应生效
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                   MediaType selectedContentType,
                                   Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                   ServerHttpRequest request, ServerHttpResponse response) {

        // 未登录或无规则则不处理
        RuleResult ruleResult = SecurityUtils.getCurrentRuleResult();
        if (ruleResult == null) return body;
        if (body == null) return null;

        Set<String> hidden = ruleResult.getHiddenFields();
        Set<String> visible = ruleResult.getVisibleFields();

        // 无字段级规则则不处理
        if (hidden.isEmpty() && visible.isEmpty()) return body;

        // 将 body 转为 Map，过滤后返回
        return filterFields(body, hidden, visible);
    }

    @SuppressWarnings("unchecked")
    private Object filterFields(Object body, Set<String> hiddenFields, Set<String> visibleFields) {
        // 单对象 → Map
        if (!(body instanceof Collection) && !(body instanceof Map)) {
            Map<String, Object> map = objectToMap(body);
            if (map == null) return body;
            applyFieldFilter(map, hiddenFields, visibleFields);
            return map;
        }

        // 列表 → 每个元素过滤
        if (body instanceof Collection) {
            List<Object> result = new ArrayList<>();
            for (Object item : (Collection<?>) body) {
                if (item instanceof Map) {
                    Map<String, Object> map = (Map<String, Object>) item;
                    applyFieldFilter(map, hiddenFields, visibleFields);
                    result.add(map);
                } else {
                    Map<String, Object> map = objectToMap(item);
                    if (map != null) {
                        applyFieldFilter(map, hiddenFields, visibleFields);
                        result.add(map);
                    } else {
                        result.add(item);
                    }
                }
            }
            return result;
        }

        // Map
        if (body instanceof Map) {
            applyFieldFilter((Map<String, Object>) body, hiddenFields, visibleFields);
            return body;
        }

        return body;
    }

    /** 执行字段过滤：visibleFields 优先于 hiddenFields */
    private void applyFieldFilter(Map<String, Object> map,
                                   Set<String> hiddenFields,
                                   Set<String> visibleFields) {
        if (map == null) return;

        // 有明确可见列表 → 只保留可见的
        if (!visibleFields.isEmpty()) {
            map.keySet().retainAll(visibleFields);
            return;
        }

        // 隐藏列表
        for (String field : hiddenFields) {
            map.remove(field);
        }
    }

    /** Object → Map（通过 Jackson） */
    @SuppressWarnings("unchecked")
    private Map<String, Object> objectToMap(Object obj) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.findAndRegisterModules();
            return mapper.convertValue(obj, Map.class);
        } catch (Exception e) {
            return null;
        }
    }
}
