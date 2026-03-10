#!/bin/bash

# 测试MySQL连接的脚本

echo "=========================================="
echo "测试阿里云MySQL连接"
echo "=========================================="
echo ""

# 1. 测试端口连通性
echo "1. 测试端口连通性..."
if nc -zv 8.138.114.34 3306 2>&1 | grep -q "succeeded"; then
    echo "✅ 端口3306连通"
else
    echo "❌ 端口3306不通"
    exit 1
fi

echo ""

# 2. 使用Java测试数据库连接
echo "2. 测试数据库连接..."

cat > /tmp/TestMySQLConnection.java << 'EOF'
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class TestMySQLConnection {
    public static void main(String[] args) {
        String url = "jdbc:mysql://8.138.114.34:3306/lightscript?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
        String user = "lightscript";
        String password = "lightscript123";
        
        try {
            System.out.println("正在连接MySQL...");
            Connection conn = DriverManager.getConnection(url, user, password);
            System.out.println("✅ 数据库连接成功！");
            
            Statement stmt = conn.createStatement();
            
            // 测试查询
            ResultSet rs = stmt.executeQuery("SELECT VERSION() as version, DATABASE() as db, NOW() as time");
            if (rs.next()) {
                System.out.println("\n数据库信息:");
                System.out.println("  MySQL版本: " + rs.getString("version"));
                System.out.println("  当前数据库: " + rs.getString("db"));
                System.out.println("  服务器时间: " + rs.getString("time"));
            }
            
            // 查看表
            rs = stmt.executeQuery("SHOW TABLES");
            System.out.println("\n数据库表:");
            while (rs.next()) {
                System.out.println("  - " + rs.getString(1));
            }
            
            rs.close();
            stmt.close();
            conn.close();
            
            System.out.println("\n✅ 所有测试通过！");
            
        } catch (Exception e) {
            System.err.println("❌ 连接失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
EOF

# 编译并运行
cd /tmp
javac TestMySQLConnection.java 2>&1
if [ $? -eq 0 ]; then
    # 下载MySQL驱动（如果需要）
    if [ ! -f mysql-connector-java.jar ]; then
        echo "下载MySQL驱动..."
        curl -L -o mysql-connector-java.jar https://repo1.maven.org/maven2/mysql/mysql-connector-java/8.0.33/mysql-connector-java-8.0.33.jar 2>/dev/null
    fi
    
    java -cp .:mysql-connector-java.jar TestMySQLConnection
else
    echo "❌ Java编译失败"
    exit 1
fi

echo ""
echo "=========================================="
echo "测试完成"
echo "=========================================="
