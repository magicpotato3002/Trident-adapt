#!/bin/bash
# ============================================================
# LoyalTide - Build Script
# Yêu cầu: Java 21+ JDK và Maven 3.8+
# ============================================================

echo "=== LoyalTide Build Script ==="

# Kiểm tra Java
if ! command -v javac &> /dev/null; then
    echo "ERROR: javac not found. Cài JDK 21:"
    echo "  Ubuntu/Debian: sudo apt install openjdk-21-jdk"
    echo "  Windows: https://adoptium.net/"
    exit 1
fi

# Kiểm tra Maven
if ! command -v mvn &> /dev/null; then
    echo "ERROR: mvn not found. Cài Maven:"
    echo "  Ubuntu/Debian: sudo apt install maven"
    echo "  Windows: https://maven.apache.org/download.cgi"
    exit 1
fi

echo "Java: $(java -version 2>&1 | head -1)"
echo "Maven: $(mvn -version 2>&1 | head -1)"
echo ""
echo "Building LoyalTide..."

mvn clean package -q

if [ $? -eq 0 ]; then
    JAR=$(find target -name "LoyalTide-*.jar" | head -1)
    echo "✅ Build thành công: $JAR"
    echo "Copy vào thư mục plugins/ của server Paper 1.21.1"
else
    echo "❌ Build thất bại. Xem lỗi ở trên."
    exit 1
fi
