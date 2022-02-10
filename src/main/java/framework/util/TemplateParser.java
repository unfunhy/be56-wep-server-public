package framework.util;

import framework.params.Model;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateParser {

    private final String INLINE_PATTERN_REGEX = ".*\\{\\{(.+?)}}.*";
    private final String BLOCK_OPEN_REGEX = ".*\\{\\{#(.+?)}}.*";

    private final String UNIT_INLINE_PATTERN_REGEX = "\\{\\{(.+?)}}";
    private final String UNIT_BLOCK_OPEN_REGEX = "\\{\\{#(.+?)}}";
    private final String UNIT_BLOCK_CLOSE_REGEX = "\\{\\{/(.+?)}}";
    private final String NEW_LINE = "\r\n";

    private final Pattern INLINE_PATTERN = Pattern.compile(UNIT_INLINE_PATTERN_REGEX);
    private final Pattern BLOCK_OPEN_PATTERN = Pattern.compile(UNIT_BLOCK_OPEN_REGEX);
    private final Pattern BLOCK_CLOSE_PATTERN = Pattern.compile(UNIT_BLOCK_CLOSE_REGEX);
    private final int PATTERN_ARGS_INDEX = 1;

    public byte[] getModelAppliedView(File file, Model model) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(file);
        BufferedReader br = new BufferedReader(new InputStreamReader(fileInputStream));
        StringBuilder builder = new StringBuilder();

        String line;
        while ((line = br.readLine()) != null) {
            if (line.matches(BLOCK_OPEN_REGEX)) {
                line = replaceBlockTemplate(line, br, model);
            } else if (line.matches(INLINE_PATTERN_REGEX)) {
                line = replaceInlineTemplate(line, model, null);
            }
            builder.append(line).append(NEW_LINE);
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * target object에 해당하는 field가 없다면, model에 해당하는 값이 있는지 확인
     */
    private String replaceInlineTemplate(String line, Model model, Object targetObj) {
        Matcher matcher = INLINE_PATTERN.matcher(line);
        while (matcher.find()) {
            String targetAttributeName = matcher.group(PATTERN_ARGS_INDEX);
            String target = matcher.group();
            String replacement = getValueFromModel(model, targetAttributeName, targetObj);
            line = line.replace(target, replacement);
        }
        return line;
    }

    private String replaceBlockTemplate(String line, BufferedReader br, Model model) throws IOException {
        Matcher blockMatcher = BLOCK_OPEN_PATTERN.matcher(line);
        String targetComponentName = "";
        if (blockMatcher.find()) {
            targetComponentName = blockMatcher.group(PATTERN_ARGS_INDEX);
        }
        String blockLine = getAllHtmlElementsWrappedAsBlock(line, br);

        // model에 key, value가 있을 경우만 렌더링, 없으면 해당 부분 렌더링 하지 않음
        Object targetObj;
        try {
            targetObj = model.getAttributes(targetComponentName);
            checkModelAttributeExist(targetObj, targetComponentName);
        } catch (Exception e) {
            return "";
        }

        // list obj
        if (targetObj instanceof List) {
            return getTableHtmlElements(model, blockLine, (List<?>) targetObj);
        }
        // non-list obj
        else {
            return replaceInlineTemplate(blockLine, model, targetObj);
        }
    }

    private String getTableHtmlElements(Model model, String blockLine, List<?> targetList) {
        StringBuilder builder = new StringBuilder();
        for (var targetObj : targetList) {
            String convertedLine = replaceInlineTemplate(blockLine, model, targetObj);
            builder.append(convertedLine);
        }
        return builder.toString();
    }

    private String getValueFromModel(Model model, String targetAttributeName, Object targetObj) {
        Object replacementObj;
        try {
            replacementObj = getFieldValue(targetObj, targetAttributeName);
        } catch (Exception e) {
            replacementObj = model.getAttributes(targetAttributeName);
        }
        checkModelAttributeExist(replacementObj, targetAttributeName);
        return replacementObj.toString();
    }

    private Object getFieldValue(Object targetObj, String targetFieldName) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        StringBuilder getterMethodNameBuilder = new StringBuilder();
        getterMethodNameBuilder.append("get").append(targetFieldName);
        getterMethodNameBuilder.setCharAt(3, (char) (getterMethodNameBuilder.charAt(3) - ('a' - 'A')));
        Method getter = targetObj.getClass().getMethod(getterMethodNameBuilder.toString());
        return getter.invoke(targetObj, (Object[]) null);
    }

    private String getAllHtmlElementsWrappedAsBlock(String line, BufferedReader br) throws IOException {
        Matcher matcher = BLOCK_CLOSE_PATTERN.matcher(line);
        StringBuilder builder = new StringBuilder();
        builder.append(line.replaceAll(UNIT_BLOCK_OPEN_REGEX, "")).append(NEW_LINE);
        while (!matcher.find() && (line = br.readLine()) != null) {
            builder.append(line).append(NEW_LINE);
            matcher = BLOCK_CLOSE_PATTERN.matcher(line);
        }
        return builder.toString().replaceAll(UNIT_BLOCK_CLOSE_REGEX, "");
    }

    private void checkModelAttributeExist(Object obj, String targetAttributeName) {
        if (obj == null) {
            throw new IllegalArgumentException(String.format("템플릿 엔진 에러: \"%s\" 모델을 찾을 수 없습니다.", targetAttributeName));
        }
    }
}
