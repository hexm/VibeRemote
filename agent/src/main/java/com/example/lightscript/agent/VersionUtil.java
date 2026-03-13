package com.example.lightscript.agent;

/**
 * Agent版本工具类
 * 直接从Maven构建时注入的版本信息读取
 */
public class VersionUtil {
    
    private static String cachedVersion = null;
    
    /**
     * 获取当前Agent版本号
     * 从Maven构建时注入的MANIFEST.MF或version.properties读取
     */
    public static String getCurrentVersion() {
        if (cachedVersion != null) {
            return cachedVersion;
        }
        
        // 1. 从MANIFEST.MF读取Maven注入的版本
        String version = getVersionFromManifest();
        if (version != null && !version.trim().isEmpty()) {
            cachedVersion = version.trim();
            return cachedVersion;
        }
        
        // 2. 从version.properties读取Maven注入的版本
        version = getVersionFromProperties();
        if (version != null && !version.trim().isEmpty()) {
            cachedVersion = version.trim();
            return cachedVersion;
        }
        
        // 3. 默认版本（不应该到这里）
        cachedVersion = "1.0.0";
        return cachedVersion;
    }
    
    /**
     * 从MANIFEST.MF文件读取Maven注入的版本信息
     */
    private static String getVersionFromManifest() {
        try {
            Package pkg = VersionUtil.class.getPackage();
            if (pkg != null) {
                String version = pkg.getImplementationVersion();
                if (version != null) {
                    return version;
                }
                version = pkg.getSpecificationVersion();
                if (version != null) {
                    return version;
                }
            }
        } catch (Exception e) {
            // 忽略错误，尝试其他方式
        }
        return null;
    }
    
    /**
     * 从version.properties读取Maven注入的版本信息
     */
    private static String getVersionFromProperties() {
        try {
            java.io.InputStream is = VersionUtil.class.getClassLoader().getResourceAsStream("version.properties");
            if (is != null) {
                java.util.Properties props = new java.util.Properties();
                props.load(is);
                is.close();
                return props.getProperty("version");
            }
        } catch (Exception e) {
            // 忽略错误
        }
        return null;
    }
    
    /**
     * 清除缓存的版本号（用于测试）
     */
    public static void clearCache() {
        cachedVersion = null;
    }
}