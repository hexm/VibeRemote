const { createApp, ref, onMounted, computed } = Vue;

const app = createApp({
    setup() {
        const userInfo = ref({});
        const tasks = ref([]);
        const loading = ref(false);
        const searchKeyword = ref('');
        const statusFilter = ref('');
        const dateRange = ref([]);
        const currentPage = ref(1);
        const pageSize = ref(10);

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

        const fetchTasks = async () => {
            loading.value = true;
            try {
                const response = await api.get('/tasks');
                tasks.value = response.data;
            } catch (error) {
                ElMessage.error('获取任务列表失败');
            } finally {
                loading.value = false;
            }
        };

        const filteredTasks = computed(() => {
            // Filtering logic would go here
            return tasks.value;
        });

        onMounted(() => {
            checkLogin();
            fetchTasks();
        });

        return {
            userInfo,
            tasks,
            loading,
            searchKeyword,
            statusFilter,
            dateRange,
            currentPage,
            pageSize,
            filteredTasks,
            totalTasks: computed(() => filteredTasks.value.length),
            logout,
            refreshTasks: fetchTasks,
            formatDateTime,
            getTaskStatusType: (status) => {
                const map = {
                    PENDING: 'info',
                    RUNNING: 'primary',
                    SUCCESS: 'success',
                    FAILED: 'danger',
                    TIMEOUT: 'warning',
                };
                return map[status] || 'info';
            },
            getTaskStatusText: (status) => {
                const map = {
                    PENDING: '等待中',
                    RUNNING: '运行中',
                    SUCCESS: '成功',
                    FAILED: '失败',
                    TIMEOUT: '超时',
                };
                return map[status] || status;
            },
            // Placeholders
            getAgentName: () => 'N/A',
            viewTaskDetail: () => {},
            viewTaskLogs: () => {},
            cancelTask: () => {},
            handleSizeChange: (val) => { pageSize.value = val; },
            handleCurrentChange: (val) => { currentPage.value = val; },
        };
    },
});

app.use(ElementPlus);
app.mount('#app');
