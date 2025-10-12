const { createApp, ref, onMounted, computed } = Vue;

const app = createApp({
    setup() {
        const userInfo = ref({});
        const agents = ref([]);
        const loading = ref(false);
        const searchKeyword = ref('');
        const statusFilter = ref('');
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

        const fetchAgents = async () => {
            loading.value = true;
            try {
                const response = await api.get('/agents');
                agents.value = response.data;
            } catch (error) {
                ElMessage.error('获取客户端列表失败');
            } finally {
                loading.value = false;
            }
        };

        const filteredAgents = computed(() => {
            return agents.value.filter(agent => {
                const matchesKeyword = agent.hostname.toLowerCase().includes(searchKeyword.value.toLowerCase()) || agent.ip.includes(searchKeyword.value);
                const matchesStatus = !statusFilter.value || agent.status === statusFilter.value;
                return matchesKeyword && matchesStatus;
            });
        });

        onMounted(() => {
            checkLogin();
            fetchAgents();
        });

        return {
            userInfo,
            agents,
            loading,
            searchKeyword,
            statusFilter,
            currentPage,
            pageSize,
            filteredAgents,
            totalAgents: computed(() => filteredAgents.value.length),
            logout,
            refreshAgents: fetchAgents,
            formatDateTime,
            // Placeholder for other functions
            handleSelectionChange: () => {},
            viewAgentDetail: () => {},
            createTaskForAgent: () => {},
            viewAgentTasks: () => {},
            handleSizeChange: (val) => { pageSize.value = val; },
            handleCurrentChange: (val) => { currentPage.value = val; },
        };
    },
});

app.use(ElementPlus);
app.mount('#app');
