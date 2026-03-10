#!/usr/bin/env python3
# 测试Python脚本
import os
import sys

def main():
    print("Hello from Python script!")
    print(f"Current directory: {os.getcwd()}")
    print(f"Python version: {sys.version}")

if __name__ == "__main__":
    main()