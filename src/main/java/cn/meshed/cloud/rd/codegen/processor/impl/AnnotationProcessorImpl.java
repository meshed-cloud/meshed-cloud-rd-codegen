package cn.meshed.cloud.rd.codegen.processor.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONNull;
import cn.hutool.json.JSONUtil;
import cn.meshed.cloud.rd.codegen.Field;
import cn.meshed.cloud.rd.codegen.processor.AnnotationProcessor;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static cn.meshed.cloud.rd.codegen.constant.Constant.ANNOTATION_FORMAT;
import static cn.meshed.cloud.rd.codegen.constant.Constant.ANNOTATION_PARAMETER_FORMAT;
import static cn.meshed.cloud.rd.codegen.constant.Constant.ANNOTATION_SINGLE_PARAMETER_FORMAT;
import static cn.meshed.cloud.rd.codegen.constant.Constant.INTEGER;
import static cn.meshed.cloud.rd.codegen.constant.Constant.MESSAGE;
import static cn.meshed.cloud.rd.codegen.constant.Constant.STRING;
import static cn.meshed.cloud.rd.codegen.constant.Constant.VALUE;

/**
 * <h1></h1>
 *
 * @author Vincent Vic
 * @version 1.0
 */
public class AnnotationProcessorImpl implements AnnotationProcessor {

    /**
     * 生成字段注解
     *
     * @param field 字段
     * @return 注解列表
     */
    @Override
    public Set<String> generateFieldAnnotation(Field field) {
        Set<String> annotations = new HashSet<>();
        String explain = StringUtils.isNotBlank(field.getExplain()) ? field.getExplain() : field.getName();
        nonNullAnnotations(field, annotations, explain);
        //解析json
        handleAnnotationJson(field, annotations);
        return annotations;
    }

    /**
     *
     * @param field
     * @param annotations
     */
    private void handleAnnotationJson(Field field, Set<String> annotations) {
        if (StringUtils.isNotBlank(field.getAnnotationJson())) {
            Map<String, Map<String, Object>> annotationMap = JSONUtil.toBean(field.getAnnotationJson(), Map.class);
            if (!annotationMap.isEmpty()) {

                Map<String, Map<String, Object>> annotationRule = getAnnotationRule();
                for (Map.Entry<String, Map<String, Object>> entry : annotationMap.entrySet()) {

                    Map<String, Object> args = new HashMap<>();
                    String annotationName = entry.getKey();
                    //获取规则map
                    Map<String, Object> annotationRuleMap = annotationRule.get(annotationName);
                    if (annotationRuleMap == null){
                        continue;
                    }
                    Map<String, Object> map = null;
                    try {
                        //通过规则匹配取值
                        map = entry.getValue();
                    } catch (Exception e){
                        continue;
                    }
                    if (map == null){
                        continue;
                    }
                    for (Map.Entry<String, Object> objectEntry : annotationRuleMap.entrySet()) {
                        Object value = map.get(objectEntry.getKey());
                        //规则中未取到值则放弃此参数
                        if (value == null) {
                            continue;
                        }
                        //存在值判断类型是否匹配
                        if (STRING.equals(objectEntry.getValue()) && value instanceof String) {
                            args.put(objectEntry.getKey(),"\""+value+"\"");
                        } else if (INTEGER.equals(objectEntry.getValue()) && isInteger(value)){
                            args.put(objectEntry.getKey(),value);
                        }
                    }
                    annotations.add(getAnnotation(annotationName,args));
                }
            }
        }
    }

    /**
     * 非空注解
     *
     * @param field       字段
     * @param annotations 注解列表
     * @param explain     描述
     */
    private void nonNullAnnotations(Field field, Set<String> annotations, String explain) {
        if (field.isNonNull()) {
            String tip = "\""+explain + "不能为空\"";
            //泛型优先处理
            if (StringUtils.isNotBlank(field.getGeneric())) {
                switch (field.getGeneric()) {
                    case "List":
                    case "Set":
                        annotations.add(getAnnotation("NotEmpty", MESSAGE, tip));
                        break;
                    default:
                        //其他暂不支持
                }
            } else if (STRING.equals(field.getType())) {
                annotations.add(getAnnotation("NotBlank", MESSAGE, tip));
            } else {
                annotations.add(getAnnotation("NotNull", MESSAGE, tip));
            }
        }
    }

    /**
     * 生成模型字段注解
     *
     * @param field 字段
     * @return 注解列表
     */
    @Override
    public Set<String> generateModelFieldAnnotation(Field field) {
        Set<String> annotations = generateFieldAnnotation(field);
        String explain = StringUtils.isNotBlank(field.getExplain()) ? field.getExplain() : field.getName();
        annotations.add(getAnnotation("ApiModelProperty", VALUE, "\""+explain+"\""));
        return annotations;
    }

    /**
     * 获取无参注解
     *
     * @param annotationName 注解名字
     * @return 注解
     */
    private static String getAnnotation(String annotationName) {
        return String.format(ANNOTATION_FORMAT, annotationName);
    }

    /**
     * 获取单一参数注解
     *
     * @param annotationName 注解名字
     * @param key            key
     * @param value          值
     * @return 注解
     */
    private static String getAnnotation(String annotationName, String key, String value) {
        return String.format(ANNOTATION_SINGLE_PARAMETER_FORMAT, annotationName, key, value);
    }

    /**
     * 返回多参数注解
     *
     * @param annotationName 注解名字
     * @param args           参数
     * @return 注解
     */
    private static String getAnnotation(String annotationName, Map<String, Object> args) {
        if (args != null && args.size() > 0) {
            String argStr = args.toString();
            return String.format(ANNOTATION_PARAMETER_FORMAT, annotationName, argStr.substring(1, argStr.length() - 1));
        }
        return getAnnotation(annotationName);
    }

    private Map<String, Map<String, Object>> getAnnotationRule() {
        String str = FileUtil.readString("annotationRule.json", StandardCharsets.UTF_8);
        return JSONUtil.toBean(str, Map.class);
    }


    /**
     * 判断是否为整数
     * @param str 传入的字符串
     * @return 是整数返回true,否则返回false
     */

    public boolean isInteger(Object str) {
        if (str instanceof Integer){
            return true;
        }
        Pattern pattern = Pattern.compile("^[-\\+]?[\\d]*$");
        return pattern.matcher(String.valueOf(str)).matches();
    }

    public static void main(String[] args) {
        Map<String, Map<String, Object>> map = JSONUtil.toBean("{\"Pattern\":{\"regexp\":\"String\",\"message\":\"String\"},\"Size\":{\"min\": 22,\"max\":\"Integer\",\"message\":\"String\"},\"Max\":{\"value\":\"Integer\",\"message\":\"String\"},\"Min\":{\"value\":\"Integer\",\"message\":\"String\"},\"Email\":null}", Map.class);
        System.out.println(map);
    }
}