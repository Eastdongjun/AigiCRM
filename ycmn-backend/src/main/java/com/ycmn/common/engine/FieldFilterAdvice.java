package com.ycmn.common.engine;

import com.fasterxml.jackson.databind.*;
import com.ycmn.common.Result;
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
        if (body == null) return null;
        // 站内响应几乎都是 Result{code,message,data}——必须在 data 内部过滤（原实现过滤顶层，脱敏完全失效）
        if (body instanceof Result) {
            Result<Object> result = (Result<Object>) body;
            Object data = result.getData();
            if (data != null) result.setData(filterFields(data, hiddenFields, visibleFields));
            return result;
        }
        if (body instanceof Collection) {
            List<Object> result = new ArrayList<>();
            for (Object item : (Collection<?>) body) {
                result.add(filterFields(item, hiddenFields, visibleFields));
            }
            return result;
        }
        if (body instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) body;
            applyFieldFilter(map, hiddenFields, visibleFields);
            // 递归处理嵌套值（分页 records、嵌套对象/数组），保证深层字段也被过滤
            for (Map.Entry<String, Object> e : new ArrayList<>(map.entrySet())) {
                Object v = e.getValue();
                if (v instanceof Map || v instanceof Collection) {
                    e.setValue(filterFields(v, hiddenFields, visibleFields));
                }
            }
            return map;
        }
        // 单对象 → Map 后再过滤
        Map<String, Object> map = objectToMap(body);
        if (map != null) return filterFields(map, hiddenFields, visibleFields);
        return body;
    }

    /** 执行字段过滤：visibleFields 优先于 hiddenFields */
    private void applyFieldFilter(Map<String, Object> map,
                                   Set<String> hiddenFields,
                                   Set<String> visibleFields) {
        if (map == null) return;

        // 分页包装（{records:[业务对象], total, size...}）：过滤 records 内的业务对象，保留包装字段
        Object records = map.get("records");
        if (records instanceof Collection) {
            filterFields(records, hiddenFields, visibleFields);
            return;
        }

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
