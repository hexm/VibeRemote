const { createApp, ref, onMounted, onUnmounted } = Vue;

const app = createApp({
    setup() {
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
                const [statsRes, activityRes] = await Promise.all([
                    api.get('/stats/summary'),
                    api.get('/stats/recent-activity')
                ]);
                stats.value = statsRes.data;
                recentActivities.value = activityRes.data;
                updateCharts();
            } catch (error) {
                console.error('Failed to fetch dashboard data:', error);
                ElMessage.error('数据加载失败');
            }
        };

        const initCharts = () => {
            agentStatusChart = echarts.init(document.getElementById('agentStatusChart'));
            taskTrendChart = echarts.init(document.getElementById('taskTrendChart'));
        };

        const updateCharts = () => {
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
            // Task trend chart would need more data from backend, placeholder for now
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
