const { createApp, ref, onMounted, onUnmounted } = Vue;

const app = createApp({
    setup() {
        // 检查 Element Plus 是否正确加载
        console.log('ElementPlus 对象:', typeof ElementPlus);

        // 尝试不同的方式获取 ElMessage
        let ElMessage;
        if (typeof ElementPlus !== 'undefined' && ElementPlus.ElMessage) {
            ElMessage = ElementPlus.ElMessage;
        } else if (typeof window.ElMessage !== 'undefined') {
            ElMessage = window.ElMessage;
        } else {
            console.error('无法找到 ElMessage 组件');
            // 创建一个简单的替代品
            ElMessage = {
                error: (msg) => console.error('Error:', msg),
                warning: (msg) => console.warn('Warning:', msg),
                success: (msg) => console.log('Success:', msg)
            };
        }
        const stats = ref({});
        const recentActivities = ref([]);
        let agentStatusChart = null;
        let taskTrendChart = null;
        let refreshInterval = null;

        const fetchStats = async () => {
            try {
                console.log('正在获取仪表盘数据...');
                const statsRes = await api.get('/web/dashboard/stats');
                console.log('统计数据响应:', statsRes.data);
                stats.value = statsRes.data;
                updateCharts();
            } catch (error) {
                console.error('Failed to fetch dashboard data:', error);
                console.error('错误详情:', error.response);
                ElMessage.error('数据加载失败: ' + (error.response?.data?.message || error.message));
            }
        };

        const initCharts = () => {
            // 初始化客户端状态分布图表
            const agentChartDom = document.getElementById('agentStatusChart');
            if (agentChartDom) {
                agentStatusChart = echarts.init(agentChartDom);
            }
            
            // 初始化任务执行趋势图表
            const taskChartDom = document.getElementById('taskTrendChart');
            if (taskChartDom) {
                taskTrendChart = echarts.init(taskChartDom);
            }
        };

        const updateCharts = () => {
            if (!stats.value) return;
            
            // 更新客户端状态分布图表
            if (agentStatusChart) {
                const agentOption = {
                    tooltip: {
                        trigger: 'item',
                        formatter: '{a} <br/>{b}: {c} ({d}%)'
                    },
                    legend: {
                        orient: 'vertical',
                        left: 'left',
                        data: ['在线', '离线']
                    },
                    series: [
                        {
                            name: '客户端状态',
                            type: 'pie',
                            radius: ['40%', '70%'],
                            avoidLabelOverlap: false,
                            itemStyle: {
                                borderRadius: 10,
                                borderColor: '#fff',
                                borderWidth: 2
                            },
                            label: {
                                show: true,
                                formatter: '{b}: {c}'
                            },
                            emphasis: {
                                label: {
                                    show: true,
                                    fontSize: '16',
                                    fontWeight: 'bold'
                                }
                            },
                            data: [
                                { value: stats.value.onlineAgents || 0, name: '在线', itemStyle: { color: '#67C23A' } },
                                { value: stats.value.offlineAgents || 0, name: '离线', itemStyle: { color: '#F56C6C' } }
                            ]
                        }
                    ]
                };
                agentStatusChart.setOption(agentOption);
            }
            
            // 更新任务执行趋势图表
            if (taskTrendChart) {
                const taskOption = {
                    tooltip: {
                        trigger: 'axis',
                        axisPointer: {
                            type: 'shadow'
                        }
                    },
                    legend: {
                        data: ['运行中', '已完成', '失败', '超时']
                    },
                    grid: {
                        left: '3%',
                        right: '4%',
                        bottom: '3%',
                        containLabel: true
                    },
                    xAxis: {
                        type: 'category',
                        data: ['任务状态']
                    },
                    yAxis: {
                        type: 'value'
                    },
                    series: [
                        {
                            name: '运行中',
                            type: 'bar',
                            data: [stats.value.runningTasks || 0],
                            itemStyle: { color: '#409EFF' }
                        },
                        {
                            name: '已完成',
                            type: 'bar',
                            data: [stats.value.completedTasks || 0],
                            itemStyle: { color: '#67C23A' }
                        },
                        {
                            name: '失败',
                            type: 'bar',
                            data: [stats.value.failedTasks || 0],
                            itemStyle: { color: '#F56C6C' }
                        },
                        {
                            name: '超时',
                            type: 'bar',
                            data: [stats.value.timeoutTasks || 0],
                            itemStyle: { color: '#E6A23C' }
                        }
                    ]
                };
                taskTrendChart.setOption(taskOption);
            }
        };

        onMounted(() => {
            initCharts();
            fetchStats();
            refreshInterval = setInterval(fetchStats, 30000); // 每30秒刷新一次
        });

        onUnmounted(() => {
            clearInterval(refreshInterval);
        });

        return { 
            stats,
            recentActivities, 
            refreshStats: fetchStats,
            refreshData: fetchStats, // "刷新"按钮使用
            formatDateTime,
            getActivityType: (type) => (type === 'TASK' ? 'success' : 'info'),
            getStatusType: (status) => (status === 'SUCCESS' ? 'success' : 'danger'),
        };
    }
});

app.use(ElementPlus);
app.mount('#app');
