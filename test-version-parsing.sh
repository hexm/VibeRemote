#!/bin/bash

echo "Testing version parsing for: agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar"

# 使用Java直接测试版本解析逻辑
java -cp server/target/classes -c "
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class TestVersionParsing {
    public static void main(String[] args) {
        String filename = \"agent-0.1.0-SNAPSHOT-jar-with-dependencies.jar\";
        String result = parseVersionFromFilename(filename);
        System.out.println(\"Input: \" + filename);
        System.out.println(\"Parsed version: \" + result);
    }
    
    private static String parseVersionFromFilename(String filename) {
        if (filename == null) return null;
        
        String nameWithoutExt = filename.replaceAll(\"\\\\.[^.]+$\", \"\");
        Pattern pattern = Pattern.compile(\"(\\\\d+\\\\.\\\\d+\\\\.\\\\d+(?:\\\\.\\\\d+)?)(?:-[A-Za-z0-9]+)*\");
        Matcher matcher = pattern.matcher(nameWithoutExt);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }
}
"