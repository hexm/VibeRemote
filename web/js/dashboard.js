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
        const userInfo = ref({});
        const stats = ref({});
        const recentActivities = ref([]);
        let agentStatusChart = null;
        let taskTrendChart = null;
        let refreshInterval = null;

        const checkLogin = () => {
            const token = localStorage.getItem('jwt_token');
            if (!token) {
                window.location.href = '/login.html';
                return;
            }
            try {
                const payload = JSON.parse(atob(token.split('.')[1]));
                userInfo.value = { username: payload.sub };
            } catch (e) {
                localStorage.removeItem('jwt_token');
                window.location.href = '/login.html';
            }
        };

        const logout = () => {
            localStorage.removeItem('jwt_token');
            window.location.href = '/login.html';
        };

        const fetchData = async () => {
            try {
                console.log('正在获取仪表盘数据...');
                // 只获取统计数据，暂时注释掉不存在的活动接口
                const statsRes = await api.get('/web/dashboard/stats');
                console.log('统计数据响应:', statsRes.data);
                
                stats.value = statsRes.data;
                // 暂时使用空数组作为活动数据
                recentActivities.value = [];
                updateCharts();
            } catch (error) {
                console.error('Failed to fetch dashboard data:', error);
                console.error('错误详情:', error.response);
                ElMessage.error('数据加载失败: ' + (error.response?.data?.message || error.message));
            }
        };

        const initCharts = () => {
            agentStatusChart = echarts.init(document.getElementById('agentStatusChart'));
            taskTrendChart = echarts.init(document.getElementById('taskTrendChart'));
        };

        const updateCharts = () => {
            console.log('更新图表，统计数据:', stats.value);
            
            if (agentStatusChart) {
                agentStatusChart.setOption({
                    tooltip: { trigger: 'item' },
                    legend: { top: '5%', left: 'center' },
                    series: [{
                        name: '客户端状态',
                        type: 'pie',
                        radius: ['40%', '70%'],
                        avoidLabelOverlap: false,
                        itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
                        label: { show: false, position: 'center' },
                        emphasis: { label: { show: true, fontSize: '20', fontWeight: 'bold' } },
                        labelLine: { show: false },
                        data: [
                            { value: stats.value.onlineAgents || 0, name: '在线' },
                            { value: stats.value.offlineAgents || 0, name: '离线' },
                        ]
                    }]
                });
            }
            
            if (taskTrendChart) {
                taskTrendChart.setOption({
                    tooltip: { trigger: 'item' },
                    legend: { top: '5%', left: 'center' },
                    series: [{
                        name: '任务状态',
                        type: 'pie',
                        radius: ['40%', '70%'],
                        avoidLabelOverlap: false,
                        itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
                        label: { show: false, position: 'center' },
                        emphasis: { label: { show: true, fontSize: '20', fontWeight: 'bold' } },
                        labelLine: { show: false },
                        data: [
                            { value: stats.value.pendingTasks || 0, name: '待执行' },
                            { value: stats.value.runningTasks || 0, name: '执行中' },
                            { value: stats.value.completedTasks || 0, name: '已完成' },
                            { value: stats.value.failedTasks || 0, name: '失败' },
                        ]
                    }]
                });
            }
        };

        onMounted(() => {
            checkLogin();
            initCharts();
            fetchData();
            refreshInterval = setInterval(fetchData, 30000); // Refresh every 30 seconds
        });

        onUnmounted(() => {
            clearInterval(refreshInterval);
        });

        return { 
            userInfo, 
            stats, 
            recentActivities, 
            logout, 
            refreshData: fetchData, 
            formatDateTime,
            getActivityType: (type) => (type === 'TASK' ? 'success' : 'info'),
            getStatusType: (status) => (status === 'SUCCESS' ? 'success' : 'danger'),
        };
    },
});

app.use(ElementPlus);
app.mount('#app');
