#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
下载前端库文件到本地
使用方法: python download-libs.py
"""

import os
import urllib.request
import sys

# 定义要下载的文件
LIBRARIES = [
    {
        'name': 'Vue.js',
        'url': 'https://cdn.jsdelivr.net/npm/vue@3/dist/vue.global.prod.js',
        'path': 'lib/vue/vue.global.prod.js'
    },
    {
        'name': 'Element Plus JS',
        'url': 'https://cdn.jsdelivr.net/npm/element-plus@2/dist/index.full.min.js',
        'path': 'lib/element-plus/index.full.min.js'
    },
    {
        'name': 'Element Plus CSS',
        'url': 'https://cdn.jsdelivr.net/npm/element-plus@2/dist/index.min.css',
        'path': 'lib/element-plus/index.min.css'
    },
    {
        'name': 'Axios',
        'url': 'https://cdn.jsdelivr.net/npm/axios@1/dist/axios.min.js',
        'path': 'lib/axios/axios.min.js'
    },
    {
        'name': 'ECharts',
        'url': 'https://cdn.jsdelivr.net/npm/echarts@5/dist/echarts.min.js',
        'path': 'lib/echarts/echarts.min.js'
    }
]

def download_file(url, filepath):
    """下载文件"""
    # 创建目录
    os.makedirs(os.path.dirname(filepath), exist_ok=True)
    
    # 下载文件
    print(f'  下载中... ', end='', flush=True)
    try:
        urllib.request.urlretrieve(url, filepath)
        file_size = os.path.getsize(filepath) / 1024  # KB
        print(f'✓ ({file_size:.1f} KB)')
        return True
    except Exception as e:
        print(f'✗ 失败: {e}')
        return False

def main():
    print('=' * 60)
    print('下载前端库文件')
    print('=' * 60)
    print()
    
    success_count = 0
    total = len(LIBRARIES)
    
    for i, lib in enumerate(LIBRARIES, 1):
        print(f'[{i}/{total}] {lib["name"]}')
        if download_file(lib['url'], lib['path']):
            success_count += 1
        print()
    
    print('=' * 60)
    print(f'完成! 成功: {success_count}/{total}')
    
    if success_count == total:
        print('\n所有库文件已下载到 lib/ 目录')
        print('现在可以修改HTML文件使用本地库了')
    else:
        print('\n部分文件下载失败，请检查网络连接')
        sys.exit(1)

if __name__ == '__main__':
    main()
