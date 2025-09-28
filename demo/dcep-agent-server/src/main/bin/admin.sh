#!/bin/bash
#这里可替换为你自己的执行程序，其他代码无需更改
JAR_NAME=$(ls ../*.jar)
JVM_OPTIONS="-Dserver.port=21630"

#使用说明，用来提示输入参数
usage() {
    echo "Usage: sh 执行脚本.sh [start|stop|restart|status]"
    exit 1
}

#检查程序是否在运行
is_exist(){
  pid=`ps -ef|grep $JAR_NAME|grep -v grep|awk '{print $2}' `
  #如果不存在返回1，存在返回0     
  if [ -z "${pid}" ]; then
    return 1
  else
    return 0
  fi
}
#启动方法
start(){
  
  is_exist
  if [ $? -eq "0" ]; then
    echo "${JAR_NAME} is already running. pid=${pid} ."
  else
    nohup java $JVM_OPTIONS -jar $JAR_NAME > /dev/null 2>&1 &
    sleep 2
    is_exist
    echo "${JAR_NAME} started. Pid is ${pid}"
  fi
}
 
#停止方法
stop(){
  is_exist
  if [ $? -eq "0" ]; then
    kill -9 $pid
    echo "${JAR_NAME} stoped. Pid is ${pid}"
  else
    echo "${JAR_NAME} is not running"
  fi  
}
 
#输出运行状态
status(){
  is_exist
  if [ $? -eq "0" ]; then
    echo "${JAR_NAME} is running. Pid is ${pid}"
  else
    echo "${JAR_NAME} is NOT running."
  fi
}
#重启
restart(){
  stop
  start
}

#根据输入参数，选择执行对应方法，不输入则执行使用说明
case "$1" in
  "start")
    start
    ;;
  "stop")
    stop
    ;;
  "status")
    status
    ;;
  "restart")
    restart
    ;;
  *)
    usage
    ;;
esac